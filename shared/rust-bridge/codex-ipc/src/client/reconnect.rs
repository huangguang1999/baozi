//! Reconnecting IPC client wrapper with exponential backoff.

use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;
use std::sync::RwLock as StdRwLock;
use std::time::Duration;

use tokio::sync::{RwLock, broadcast, mpsc, watch};
use tracing::{error, info, warn};

use crate::client::handle::{IpcClient, IpcClientConfig};
use crate::error::IpcError;
use crate::handler::RequestHandler;
use crate::protocol::params::TypedBroadcast;

/// Policy for reconnection attempts.
pub struct ReconnectPolicy {
    pub initial_delay: Duration,
    pub max_delay: Duration,
    pub max_attempts: Option<u32>,
}

impl Default for ReconnectPolicy {
    fn default() -> Self {
        Self {
            initial_delay: Duration::from_secs(1),
            max_delay: Duration::from_secs(30),
            max_attempts: None,
        }
    }
}

/// IPC client wrapper that automatically reconnects on disconnection.
pub struct ReconnectingIpcClient {
    client: Arc<StdRwLock<Option<IpcClient>>>,
    handler: Arc<RwLock<Option<Arc<dyn RequestHandler>>>>,
    broadcast_tx: broadcast::Sender<TypedBroadcast>,
    connection_tx: watch::Sender<bool>,
    invalidate_tx: mpsc::UnboundedSender<()>,
    reconnect_task: tokio::task::JoinHandle<()>,
}

impl ReconnectingIpcClient {
    /// Connect to the IPC bus with automatic reconnection.
    pub async fn connect(
        config: IpcClientConfig,
        policy: ReconnectPolicy,
    ) -> Result<Self, IpcError> {
        let config = Arc::new(config);
        let connector = {
            let config = Arc::clone(&config);
            move || {
                let config = Arc::clone(&config);
                async move { IpcClient::connect_with_config(&config).await }
            }
        };
        Self::connect_with_connector(connector, policy).await
    }

    /// Connect using a custom connector that can recreate the IPC stream.
    pub async fn connect_with_connector<F, Fut>(
        connector: F,
        policy: ReconnectPolicy,
    ) -> Result<Self, IpcError>
    where
        F: Fn() -> Fut + Send + Sync + 'static,
        Fut: Future<Output = Result<IpcClient, IpcError>> + Send + 'static,
    {
        let initial_client = connector().await?;
        Ok(Self::start_with_connector(
            Some(initial_client),
            connector,
            policy,
        ))
    }

    /// Start the reconnecting wrapper with an optional initial client.
    ///
    /// When `initial_client` is `None`, the background task immediately starts
    /// trying to connect using the supplied connector.
    pub fn start_with_connector<F, Fut>(
        initial_client: Option<IpcClient>,
        connector: F,
        policy: ReconnectPolicy,
    ) -> Self
    where
        F: Fn() -> Fut + Send + Sync + 'static,
        Fut: Future<Output = Result<IpcClient, IpcError>> + Send + 'static,
    {
        let connector: Arc<
            dyn Fn() -> Pin<Box<dyn Future<Output = Result<IpcClient, IpcError>> + Send>>
                + Send
                + Sync,
        > = Arc::new(move || Box::pin(connector()));
        let client = Arc::new(StdRwLock::new(initial_client));
        let handler: Arc<RwLock<Option<Arc<dyn RequestHandler>>>> = Arc::new(RwLock::new(None));
        let (broadcast_tx, _) = broadcast::channel::<TypedBroadcast>(256);
        let (connection_tx, _) = watch::channel(Self::client_lock_read(&client).as_ref().is_some());
        let (invalidate_tx, invalidate_rx) = mpsc::unbounded_channel();

        let reconnect_task = {
            let client = Arc::clone(&client);
            let handler = Arc::clone(&handler);
            let broadcast_tx = broadcast_tx.clone();
            let connection_tx = connection_tx.clone();
            let connector = Arc::clone(&connector);
            tokio::spawn(Self::reconnect_loop(
                policy,
                client,
                handler,
                broadcast_tx,
                connection_tx,
                invalidate_rx,
                connector,
            ))
        };

        Self {
            client,
            handler,
            broadcast_tx,
            connection_tx,
            invalidate_tx,
            reconnect_task,
        }
    }

    fn client_lock_read(
        client: &StdRwLock<Option<IpcClient>>,
    ) -> std::sync::RwLockReadGuard<'_, Option<IpcClient>> {
        match client.read() {
            Ok(guard) => guard,
            Err(error) => {
                warn!("ipc reconnect: recovering poisoned client read lock");
                error.into_inner()
            }
        }
    }

    fn client_lock_write(
        client: &StdRwLock<Option<IpcClient>>,
    ) -> std::sync::RwLockWriteGuard<'_, Option<IpcClient>> {
        match client.write() {
            Ok(guard) => guard,
            Err(error) => {
                warn!("ipc reconnect: recovering poisoned client write lock");
                error.into_inner()
            }
        }
    }

    /// Returns the current connected client, if any.
    pub fn client(&self) -> Option<IpcClient> {
        Self::client_lock_read(&self.client).clone()
    }

    /// Returns whether the wrapper currently has an active IPC client.
    pub fn is_connected(&self) -> bool {
        self.client().is_some()
    }

    /// Subscribe to typed broadcasts. Subscriptions survive reconnections.
    pub fn subscribe_broadcasts(&self) -> broadcast::Receiver<TypedBroadcast> {
        self.broadcast_tx.subscribe()
    }

    /// Subscribe to IPC connection state changes.
    pub fn subscribe_connection_state(&self) -> watch::Receiver<bool> {
        self.connection_tx.subscribe()
    }

    /// Force the current client to recycle and reconnect.
    pub fn invalidate(&self) {
        let _ = self.invalidate_tx.send(());
    }

    /// Set the request handler. It will be re-registered on reconnect.
    pub async fn set_request_handler(&self, h: Arc<dyn RequestHandler>) {
        {
            let mut guard = self.handler.write().await;
            *guard = Some(Arc::clone(&h));
        }
        if let Some(c) = self.client().as_ref() {
            c.set_request_handler(h).await;
        }
    }

    /// Stop the reconnect loop and disconnect any currently attached IPC
    /// client.
    pub async fn shutdown(&self) {
        self.reconnect_task.abort();
        let client = {
            let mut guard = Self::client_lock_write(&self.client);
            guard.take()
        };
        if let Some(client) = client {
            client.disconnect().await;
        }
        let _ = self.connection_tx.send_replace(false);
    }

    async fn reconnect_loop(
        policy: ReconnectPolicy,
        client: Arc<StdRwLock<Option<IpcClient>>>,
        handler: Arc<RwLock<Option<Arc<dyn RequestHandler>>>>,
        broadcast_tx: broadcast::Sender<TypedBroadcast>,
        connection_tx: watch::Sender<bool>,
        mut invalidate_rx: mpsc::UnboundedReceiver<()>,
        connector: Arc<
            dyn Fn() -> Pin<Box<dyn Future<Output = Result<IpcClient, IpcError>> + Send>>
                + Send
                + Sync,
        >,
    ) {
        loop {
            let current_client = {
                let guard = Self::client_lock_read(&client);
                guard.clone()
            };
            if let Some(current_client) = current_client {
                let mut sub = current_client.subscribe_broadcasts();
                let mut invalidated = false;
                loop {
                    tokio::select! {
                        msg = sub.recv() => match msg {
                            Ok(msg) => {
                                let _ = broadcast_tx.send(msg);
                            }
                            Err(broadcast::error::RecvError::Closed) => break,
                            Err(broadcast::error::RecvError::Lagged(_)) => continue,
                        },
                        signal = invalidate_rx.recv() => {
                            if signal.is_none() {
                                return;
                            }
                            info!("ipc connection invalidated, recycling client");
                            invalidated = true;
                            break;
                        }
                    }
                }
                if invalidated {
                    current_client.force_disconnect().await;
                }
            }

            {
                let mut guard = Self::client_lock_write(&client);
                *guard = None;
            }
            let _ = connection_tx.send_replace(false);

            info!("ipc connection lost, starting reconnect");

            let mut delay = policy.initial_delay;
            let mut attempt = 0u32;

            let new_client = loop {
                attempt += 1;
                if let Some(max) = policy.max_attempts {
                    if attempt > max {
                        error!("ipc reconnect: max attempts ({max}) reached, giving up");
                        return;
                    }
                }

                info!("ipc reconnecting, attempt {attempt}");
                tokio::time::sleep(delay).await;

                match connector().await {
                    Ok(c) => {
                        info!("ipc reconnected");
                        break c;
                    }
                    Err(e) => {
                        warn!("ipc reconnect failed: {e}");
                        delay = (delay * 2).min(policy.max_delay);
                    }
                }
            };

            // Re-register handler if set.
            {
                let h_guard = handler.read().await;
                if let Some(h) = h_guard.as_ref() {
                    new_client.set_request_handler(Arc::clone(h)).await;
                }
            }

            // Store the new client.
            {
                let mut guard = Self::client_lock_write(&client);
                *guard = Some(new_client);
            }
            let _ = connection_tx.send_replace(true);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicUsize, Ordering};

    use crate::protocol::envelope::{Envelope, Response};
    use crate::protocol::method::Method;
    use crate::protocol::params::InitializeResult;
    use crate::transport::frame;
    use tokio::time::{Instant, sleep};

    async fn connect_test_client(label: usize) -> Result<IpcClient, IpcError> {
        let (client_stream, mut server_stream) = tokio::io::duplex(4096);
        tokio::spawn(async move {
            let raw = frame::read_frame(&mut server_stream)
                .await
                .expect("initialize request frame");
            let envelope: Envelope = serde_json::from_str(&raw).expect("initialize envelope");
            let request = match envelope {
                Envelope::Request(request) => request,
                other => panic!("expected initialize request, got {other:?}"),
            };
            assert_eq!(request.method, Method::Initialize.wire_name());

            let response = Envelope::Response(Response::Success {
                request_id: request.request_id,
                method: request.method,
                handled_by_client_id: format!("router-{label}"),
                result: serde_json::to_value(InitializeResult {
                    client_id: format!("client-{label}"),
                })
                .expect("initialize result"),
            });
            frame::write_frame(
                &mut server_stream,
                &serde_json::to_string(&response).expect("response json"),
            )
            .await
            .expect("initialize response write");

            let _ = frame::read_frame(&mut server_stream).await;
        });

        IpcClient::connect_with_stream(
            &IpcClientConfig {
                socket_path: PathBuf::from(format!("/tmp/reconnect-test-{label}.sock")),
                client_type: "mobile-test".to_string(),
                request_timeout: Duration::from_secs(1),
            },
            client_stream,
        )
        .await
    }

    async fn wait_until(timeout: Duration, mut predicate: impl FnMut() -> bool) {
        let deadline = Instant::now() + timeout;
        loop {
            if predicate() {
                return;
            }
            assert!(Instant::now() < deadline, "timed out waiting for condition");
            sleep(Duration::from_millis(5)).await;
        }
    }

    #[tokio::test]
    async fn invalidate_recycles_the_current_client_and_reconnects() {
        let connect_count = Arc::new(AtomicUsize::new(0));
        let reconnecting = ReconnectingIpcClient::start_with_connector(
            None,
            {
                let connect_count = Arc::clone(&connect_count);
                move || {
                    let connect_count = Arc::clone(&connect_count);
                    async move {
                        let attempt = connect_count.fetch_add(1, Ordering::SeqCst) + 1;
                        connect_test_client(attempt).await
                    }
                }
            },
            ReconnectPolicy {
                initial_delay: Duration::from_millis(1),
                max_delay: Duration::from_millis(5),
                max_attempts: Some(16),
            },
        );

        wait_until(Duration::from_secs(1), || reconnecting.is_connected()).await;
        let first_client_id = reconnecting
            .client()
            .expect("initial client")
            .client_id()
            .to_string();
        assert_eq!(first_client_id, "client-1");

        reconnecting.invalidate();

        wait_until(Duration::from_secs(1), || {
            reconnecting
                .client()
                .is_some_and(|client| client.client_id() != first_client_id)
        })
        .await;
        assert_eq!(connect_count.load(Ordering::SeqCst), 2);

        reconnecting.shutdown().await;
    }
}

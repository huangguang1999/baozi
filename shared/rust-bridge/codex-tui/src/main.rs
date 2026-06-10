mod app;
mod input;
mod markdown;
mod router;
mod screens;
mod theme;
mod widgets;

use std::io;
use std::path::PathBuf;
use std::sync::Arc;

use crossterm::{
    event::{DisableMouseCapture, EnableMouseCapture},
    execute,
    terminal::{EnterAlternateScreen, LeaveAlternateScreen, disable_raw_mode, enable_raw_mode},
};
use ratatui::{Terminal, backend::CrosstermBackend};

use codex_mobile_client::MobileClient;
use codex_mobile_client::session::connection::ServerConfig;

/// Terminal guard — restores terminal state on drop (including panics).
struct TerminalGuard;

impl Drop for TerminalGuard {
    fn drop(&mut self) {
        let _ = disable_raw_mode();
        let _ = execute!(io::stdout(), LeaveAlternateScreen, DisableMouseCapture);
    }
}

/// CLI arguments.
struct Args {
    connect: Option<String>,
    log_file: Option<PathBuf>,
}

fn parse_args() -> Args {
    let mut args = Args {
        connect: None,
        log_file: None,
    };
    let mut iter = std::env::args().skip(1);
    while let Some(arg) = iter.next() {
        match arg.as_str() {
            "--connect" | "-c" => {
                args.connect = iter.next();
            }
            "--log-file" | "-l" => {
                args.log_file = iter.next().map(PathBuf::from);
            }
            "--help" | "-h" => {
                eprintln!("codex-tui — terminal client for Codex");
                eprintln!();
                eprintln!("USAGE:");
                eprintln!("  codex-tui [OPTIONS]");
                eprintln!();
                eprintln!("OPTIONS:");
                eprintln!("  -c, --connect HOST:PORT   Connect to server on startup");
                eprintln!(
                    "  -l, --log-file PATH       Write logs to file (default: codex-tui.log)"
                );
                eprintln!("  -h, --help                Show this help");
                std::process::exit(0);
            }
            _ => {
                eprintln!("Unknown argument: {arg}");
                eprintln!("Run with --help for usage.");
                std::process::exit(1);
            }
        }
    }
    args
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = parse_args();

    let log_path = cli
        .log_file
        .unwrap_or_else(|| PathBuf::from("codex-tui.log"));
    let log_dir = log_path.parent().unwrap_or(std::path::Path::new("."));
    let log_name = log_path
        .file_name()
        .unwrap_or(std::ffi::OsStr::new("codex-tui.log"));
    let log_file = tracing_appender::rolling::never(log_dir, log_name);
    tracing_subscriber::fmt()
        .with_writer(log_file)
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("warn")),
        )
        .init();

    // Set panic hook to restore terminal
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let _ = disable_raw_mode();
        let _ = execute!(io::stdout(), LeaveAlternateScreen, DisableMouseCapture);
        default_hook(info);
    }));

    // Initialize the shared Rust client
    let client = Arc::new(MobileClient::new());

    // Auto-connect if --connect was passed
    if let Some(addr) = &cli.connect {
        let (host, port) = parse_host_port(addr);
        let config = ServerConfig {
            server_id: format!("{host}:{port}"),
            display_name: host.clone(),
            host: host.clone(),
            port,
            websocket_url: None,
            is_local: false,
            tls: false,
        };
        match client.connect_remote(config).await {
            Ok(sid) => {
                tracing::info!("Auto-connected to {sid}");
            }
            Err(e) => {
                eprintln!("Failed to connect to {addr}: {e}");
                std::process::exit(1);
            }
        }
    }

    // Set up terminal
    enable_raw_mode()?;
    execute!(io::stdout(), EnterAlternateScreen, EnableMouseCapture)?;
    let _guard = TerminalGuard;

    let backend = CrosstermBackend::new(io::stdout());
    let mut terminal = Terminal::new(backend)?;
    terminal.clear()?;

    // Run the app
    let mut app = app::App::new(client);
    app.run(&mut terminal).await?;

    Ok(())
}

fn parse_host_port(addr: &str) -> (String, u16) {
    if let Some((host, port_str)) = addr.rsplit_once(':') {
        if let Ok(port) = port_str.parse::<u16>() {
            return (host.to_string(), port);
        }
    }
    (addr.to_string(), 8390)
}

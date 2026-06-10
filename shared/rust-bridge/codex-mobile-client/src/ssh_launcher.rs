use std::ffi::OsStr;
use std::io;
use std::path::Path;
use std::process::ExitStatus;
use std::sync::Arc;

use alleycat_bridge_core::{
    ChildProcess, ChildStderr, ChildStdin, ChildStdout, ProcessLauncher, ProcessRole, ProcessSpec,
    StdioMode,
};
use futures::future::BoxFuture;

use crate::ssh::{
    PROFILE_INIT, RemoteShell, SshClient, SshExecChild, build_posix_exec_command, shell_quote,
};

#[derive(Clone)]
pub(crate) struct SshLauncher {
    ssh: Arc<SshClient>,
    shell: RemoteShell,
}

impl SshLauncher {
    pub(crate) fn new(ssh: Arc<SshClient>, shell: RemoteShell) -> Self {
        Self { ssh, shell }
    }
}

impl ProcessLauncher for SshLauncher {
    fn launch(&self, spec: ProcessSpec) -> BoxFuture<'_, io::Result<Box<dyn ChildProcess>>> {
        Box::pin(async move {
            let command = build_remote_command(&spec, self.shell)?;
            let command = match self.shell {
                RemoteShell::Posix => build_posix_exec_command(&command),
                RemoteShell::PowerShell => command,
            };
            let child = self
                .ssh
                .open_exec_child_with_stdio(
                    &command,
                    spec.stdin == StdioMode::Piped,
                    spec.stdout == StdioMode::Piped,
                    spec.stderr == StdioMode::Piped,
                )
                .await
                .map_err(|error| io::Error::new(io::ErrorKind::ConnectionAborted, error))?;
            Ok(Box::new(SshChildProcess { inner: child }) as Box<dyn ChildProcess>)
        })
    }
}

pub(crate) fn build_remote_command(spec: &ProcessSpec, shell: RemoteShell) -> io::Result<String> {
    match shell {
        RemoteShell::Posix => build_posix_command(spec),
        RemoteShell::PowerShell => Err(io::Error::new(
            io::ErrorKind::Unsupported,
            "SSH bridge launch is not implemented for PowerShell remotes",
        )),
    }
}

fn build_posix_command(spec: &ProcessSpec) -> io::Result<String> {
    let mut command = format!("{PROFILE_INIT} ");
    if let Some(cwd) = spec.cwd.as_deref() {
        let quoted_cwd = quote_path(cwd);
        match spec.role {
            ProcessRole::Agent => {
                command.push_str("if [ -d ");
                command.push_str(&quoted_cwd);
                command.push_str(" ]; then cd ");
                command.push_str(&quoted_cwd);
                command.push_str(" || exit $?; fi; ");
            }
            ProcessRole::ToolCommand => {
                command.push_str("cd ");
                command.push_str(&quoted_cwd);
                command.push_str(" && ");
            }
        }
    }

    let mut words = vec!["exec".to_string(), "env".to_string()];
    for (key, value) in &spec.env {
        let assignment = format!("{}={}", os_to_string(key)?, os_to_string(value)?);
        words.push(shell_quote(&assignment));
    }
    words.push(quote_path(&spec.program));
    for arg in &spec.args {
        words.push(shell_quote(&os_to_string(arg)?));
    }
    command.push_str(&words.join(" "));

    match spec.stdin {
        StdioMode::Null => command.push_str(" </dev/null"),
        StdioMode::Piped | StdioMode::Inherit => {}
    }
    match spec.stdout {
        StdioMode::Null => command.push_str(" >/dev/null"),
        StdioMode::Piped | StdioMode::Inherit => {}
    }
    match spec.stderr {
        StdioMode::Null => command.push_str(" 2>/dev/null"),
        StdioMode::Piped | StdioMode::Inherit => {}
    }

    Ok(command)
}

fn quote_path(path: &Path) -> String {
    shell_quote(&path.to_string_lossy())
}

fn os_to_string(value: &OsStr) -> io::Result<String> {
    value
        .to_str()
        .map(ToOwned::to_owned)
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "non-utf8 process argument"))
}

struct SshChildProcess {
    inner: SshExecChild,
}

// The child pipes are only accessed through `&mut self` trait methods. The
// bridge-core trait requires `Sync` for all child handles so launchers can be
// stored behind shared trait objects.
unsafe impl Sync for SshChildProcess {}

impl ChildProcess for SshChildProcess {
    fn take_stdin(&mut self) -> Option<ChildStdin> {
        self.inner.take_stdin().map(|stream| stream as ChildStdin)
    }

    fn take_stdout(&mut self) -> Option<ChildStdout> {
        self.inner.take_stdout().map(|stream| stream as ChildStdout)
    }

    fn take_stderr(&mut self) -> Option<ChildStderr> {
        self.inner.take_stderr().map(|stream| stream as ChildStderr)
    }

    fn id(&self) -> Option<u32> {
        None
    }

    fn wait(&mut self) -> BoxFuture<'_, io::Result<ExitStatus>> {
        Box::pin(async move { self.inner.wait().await })
    }

    fn kill(&mut self) -> BoxFuture<'_, io::Result<()>> {
        Box::pin(async move { self.inner.kill().await })
    }
}

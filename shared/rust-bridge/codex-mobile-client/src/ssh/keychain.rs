//! macOS login-keychain unlock for the SSH bootstrap.
//!
//! Why: SSH-launched non-interactive shells cannot pop a "Codex wants to
//! sign with your developer cert / read a credential" prompt. If the
//! keychain is locked, anything that tries to read from it stalls. We
//! unlock it up front using a password the user opts into providing via
//! the `unlock_macos_keychain` flag.
//!
//! The password reaches `security` via stdin → `cat` → command substitution,
//! never as a literal in the shell command string. It still appears briefly
//! in `security`'s own argv (an OS limitation — `security` has no native
//! stdin password mode), but it no longer leaks via shell-command logging
//! or our own tracing.

use std::time::Duration;

use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tracing::info;

use super::{SshClient, SshError, append_bridge_info_log, remote_shell_name, types::RemoteShell};

/// Unlock the macOS login keychain. The password is supplied via the SSH
/// exec channel's stdin → `cat` → `$(…)` so it never appears in the shell
/// command string we send across the wire (or in our own logging).
const MACOS_KEYCHAIN_UNLOCK_FROM_STDIN: &str = r#"if command -v security >/dev/null 2>&1 && [ -e "$HOME/Library/Keychains/login.keychain-db" ]; then
  _litter_kc_path="$HOME/Library/Keychains/login.keychain-db"
  printf 'litter_keychain_unlock start path=%s\n' "$_litter_kc_path" >&2
  _litter_kc_output="$(security unlock-keychain -p "$(cat)" "$_litter_kc_path" 2>&1 >/dev/null)"
  _litter_kc_status=$?
  _litter_kc_output="$(printf '%s' "$_litter_kc_output" | tr '\n' ' ')"
  printf 'litter_keychain_unlock result status=%s stderr=%s\n' "$_litter_kc_status" "$_litter_kc_output" >&2
  exit 0
else
  printf 'litter_keychain_unlock skipped reason=security_or_login_keychain_missing\n' >&2
  exit 0
fi"#;

const MACOS_KEYCHAIN_UNLOCK_TIMEOUT: Duration = Duration::from_secs(15);

impl SshClient {
    pub(super) async fn log_macos_keychain_unlock_for_bootstrap(
        &self,
        shell: RemoteShell,
    ) -> Result<(), SshError> {
        if shell != RemoteShell::Posix {
            append_bridge_info_log(&format!(
                "ssh_bootstrap_keychain_unlock skipped shell={}",
                remote_shell_name(shell)
            ));
            return Ok(());
        }

        let Some(password) = self.macos_keychain_password.as_deref() else {
            append_bridge_info_log("ssh_bootstrap_keychain_unlock skipped reason=disabled");
            return Ok(());
        };

        let mut child = self
            .open_exec_child_with_stdio(MACOS_KEYCHAIN_UNLOCK_FROM_STDIN, true, true, true)
            .await?;
        if let Some(mut stdin) = child.take_stdin() {
            let _ = stdin.write_all(password.as_bytes()).await;
        }
        child
            .close_stdin()
            .await
            .map_err(|e| SshError::ExecFailed {
                exit_code: 1,
                stderr: format!("keychain unlock stdin close failed: {e}"),
            })?;
        let unlock_result = tokio::time::timeout(MACOS_KEYCHAIN_UNLOCK_TIMEOUT, async {
            let mut stdout_buf = Vec::new();
            if let Some(mut stdout) = child.take_stdout() {
                let _ = stdout.read_to_end(&mut stdout_buf).await;
            }
            let mut stderr_buf = Vec::new();
            if let Some(mut stderr) = child.take_stderr() {
                let _ = stderr.read_to_end(&mut stderr_buf).await;
            }
            let exit = child.wait().await.map_err(|e| SshError::ExecFailed {
                exit_code: 1,
                stderr: format!("keychain unlock wait failed: {e}"),
            })?;
            Ok::<_, SshError>((exit, stderr_buf))
        })
        .await;

        let (exit, stderr_buf) = match unlock_result {
            Ok(result) => result?,
            Err(_) => {
                let _ = child.kill().await;
                append_bridge_info_log("ssh_bootstrap_keychain_unlock timeout");
                return Err(SshError::Timeout);
            }
        };
        let exit_code = exit.code().unwrap_or(1);
        let output = String::from_utf8_lossy(&stderr_buf);
        let output_trim = output.trim();
        if output_trim.is_empty() {
            info!(
                "ssh bootstrap keychain unlock output shell={} exit_code={} <empty>",
                remote_shell_name(shell),
                exit_code
            );
        } else {
            info!(
                "ssh bootstrap keychain unlock output shell={} exit_code={} {}",
                remote_shell_name(shell),
                exit_code,
                output_trim
            );
        }
        append_bridge_info_log(&format!(
            "ssh_bootstrap_keychain_unlock exit_code={} stderr={}",
            exit_code, output_trim
        ));
        Ok(())
    }
}

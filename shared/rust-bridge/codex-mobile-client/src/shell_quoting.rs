//! Canonical shell-quoting helpers shared by every code path that constructs
//! a remote command string.
//!
//! Three quoting flavors:
//! - `posix_quote` — `/bin/sh`, `bash`, `dash`, `zsh`, `ksh` (single-quoted)
//! - `powershell_quote` — Windows PowerShell single-quoted
//! - `cmd_quote` — Windows `cmd.exe` (double-quote escape)
//!
//! Anything that builds a remote shell string MUST go through these. Defining
//! a per-module `shell_quote` is a foot-gun: a fix to one copy doesn't reach
//! the others. See `tests` for the contract.

/// Quote `s` for a POSIX shell.
///
/// Wraps in single quotes and escapes embedded `'` as `'\''`. Inside `'…'`
/// nothing is interpreted by the shell — no `$VAR` expansion, no backticks,
/// no glob, no escape sequences.
pub fn posix_quote(s: &str) -> String {
    let escaped = s.replace('\'', "'\\''");
    format!("'{escaped}'")
}

/// Quote `s` for a PowerShell single-quoted string. Inside `'…'` PowerShell
/// performs no expansion; embedded `'` doubles to `''`.
pub fn powershell_quote(s: &str) -> String {
    format!("'{}'", s.replace('\'', "''"))
}

/// Quote `s` for a `cmd.exe` argument that's already wrapped in double
/// quotes (escape `"` as `""`).
pub fn cmd_quote(s: &str) -> String {
    s.replace('"', "\"\"")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn posix_quote_simple() {
        assert_eq!(posix_quote("hello"), "'hello'");
    }

    #[test]
    fn posix_quote_empty_string() {
        assert_eq!(posix_quote(""), "''");
    }

    #[test]
    fn posix_quote_path_with_spaces() {
        assert_eq!(
            posix_quote("/home/user/my file.txt"),
            "'/home/user/my file.txt'"
        );
    }

    #[test]
    fn posix_quote_with_single_quote() {
        assert_eq!(posix_quote("it's"), "'it'\\''s'");
    }

    #[test]
    fn posix_quote_consecutive_single_quotes() {
        assert_eq!(posix_quote("a''b"), "'a'\\'''\\''b'");
    }

    #[test]
    fn posix_quote_dollar_var_stays_literal() {
        // We rely on this: posix_quote("$HOME") must NOT expand at runtime.
        assert_eq!(posix_quote("$HOME/foo"), "'$HOME/foo'");
    }

    #[test]
    fn posix_quote_backtick_stays_literal() {
        assert_eq!(posix_quote("`whoami`"), "'`whoami`'");
    }

    #[test]
    fn posix_quote_unicode() {
        assert_eq!(posix_quote("café 北京"), "'café 北京'");
    }

    #[test]
    fn powershell_quote_simple() {
        assert_eq!(powershell_quote("hello"), "'hello'");
    }

    #[test]
    fn powershell_quote_with_single_quote() {
        assert_eq!(powershell_quote("it's"), "'it''s'");
    }

    #[test]
    fn cmd_quote_double_quote() {
        assert_eq!(cmd_quote(r#"a"b"#), r#"a""b"#);
    }
}

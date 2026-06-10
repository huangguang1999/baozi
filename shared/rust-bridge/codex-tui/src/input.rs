#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InputMode {
    /// Vim-like navigation keys active.
    Normal,
    /// Text composer has focus — all keys go to tui-textarea.
    Insert,
    /// Search input active (e.g., sessions search).
    Search,
}

impl Default for InputMode {
    fn default() -> Self {
        Self::Normal
    }
}

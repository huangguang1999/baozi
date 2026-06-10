#![allow(dead_code)]
use ratatui::style::{Color, Modifier, Style};

pub const ACCENT: Color = Color::Rgb(0, 255, 156);
pub const BG: Color = Color::Black;
pub const FG: Color = Color::White;
pub const FG_DIM: Color = Color::DarkGray;
pub const FG_SECONDARY: Color = Color::Gray;
pub const BORDER: Color = Color::DarkGray;
pub const CODE_BG: Color = Color::Rgb(30, 30, 30);
pub const ERROR: Color = Color::Red;
pub const WARNING: Color = Color::Yellow;
pub const SUCCESS: Color = Color::Green;

pub fn accent() -> Style {
    Style::default().fg(ACCENT)
}

pub fn user_msg() -> Style {
    Style::default().fg(ACCENT)
}

pub fn assistant_msg() -> Style {
    Style::default().fg(FG)
}

pub fn reasoning() -> Style {
    Style::default().fg(FG_DIM).add_modifier(Modifier::ITALIC)
}

pub fn dim() -> Style {
    Style::default().fg(FG_DIM)
}

pub fn secondary() -> Style {
    Style::default().fg(FG_SECONDARY)
}

pub fn border() -> Style {
    Style::default().fg(BORDER)
}

pub fn border_focused() -> Style {
    Style::default().fg(ACCENT)
}

pub fn diff_add() -> Style {
    Style::default().fg(SUCCESS)
}

pub fn diff_remove() -> Style {
    Style::default().fg(ERROR)
}

pub fn error() -> Style {
    Style::default().fg(ERROR)
}

pub fn bold() -> Style {
    Style::default().add_modifier(Modifier::BOLD)
}

pub fn health_color(health: &codex_mobile_client::store::ServerHealthSnapshot) -> Color {
    use codex_mobile_client::store::ServerHealthSnapshot;
    match health {
        ServerHealthSnapshot::Connected => SUCCESS,
        ServerHealthSnapshot::Connecting => WARNING,
        ServerHealthSnapshot::Disconnected => ERROR,
        ServerHealthSnapshot::Unresponsive => ERROR,
        ServerHealthSnapshot::Unknown(_) => FG_DIM,
    }
}

pub fn health_symbol(health: &codex_mobile_client::store::ServerHealthSnapshot) -> &'static str {
    use codex_mobile_client::store::ServerHealthSnapshot;
    match health {
        ServerHealthSnapshot::Connected => "●",
        ServerHealthSnapshot::Connecting => "○",
        ServerHealthSnapshot::Disconnected => "✖",
        ServerHealthSnapshot::Unresponsive => "✖",
        ServerHealthSnapshot::Unknown(_) => "?",
    }
}

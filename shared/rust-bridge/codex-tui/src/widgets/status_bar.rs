use codex_mobile_client::store::AppSnapshot;
use ratatui::{
    Frame,
    layout::Rect,
    text::{Line, Span},
    widgets::Paragraph,
};

use crate::input::InputMode;
use crate::theme;

pub fn render(
    frame: &mut Frame,
    area: Rect,
    snapshot: &AppSnapshot,
    mode: InputMode,
    status_message: Option<&str>,
) {
    let mode_label = match mode {
        InputMode::Normal => "NORMAL",
        InputMode::Insert => "INSERT",
        InputMode::Search => "SEARCH",
    };

    let server_count = snapshot.servers.len();
    let thread_count = snapshot.threads.len();

    let mut spans = vec![
        Span::styled(format!(" [{mode_label}]"), theme::accent()),
        Span::raw("  "),
    ];

    if let Some(msg) = status_message {
        spans.push(Span::styled(msg, theme::error()));
    } else {
        spans.push(Span::styled(
            format!("{server_count} server(s)  {thread_count} session(s)"),
            theme::secondary(),
        ));
    }

    let line = Line::from(spans);
    frame.render_widget(
        Paragraph::new(line).style(ratatui::style::Style::default().bg(theme::CODE_BG)),
        area,
    );
}

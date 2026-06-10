use codex_mobile_client::types::PendingApproval;
use ratatui::{
    Frame,
    layout::Rect,
    text::{Line, Span},
    widgets::Paragraph,
};

use crate::theme;

pub fn render(frame: &mut Frame, area: Rect, approval: &PendingApproval) {
    let command_text = approval
        .command
        .as_deref()
        .or(approval.path.as_deref())
        .unwrap_or("(unknown action)");

    let line = Line::from(vec![
        Span::styled(" ▌APPROVE? ", theme::bold()),
        Span::styled(format!("`{command_text}`"), theme::accent()),
        Span::raw("  "),
        Span::styled("[y]", theme::accent()),
        Span::raw("es  "),
        Span::styled("[n]", theme::error()),
        Span::raw("o  "),
        Span::styled("[a]", theme::accent()),
        Span::raw("lways  "),
        Span::styled("[x]", theme::error()),
        Span::raw("cancel"),
    ]);

    frame.render_widget(
        Paragraph::new(line).style(ratatui::style::Style::default().bg(theme::CODE_BG)),
        area,
    );
}

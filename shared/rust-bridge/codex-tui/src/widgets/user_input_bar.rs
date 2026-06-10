use codex_mobile_client::types::PendingUserInputRequest;
use ratatui::{
    Frame,
    layout::Rect,
    text::{Line, Span},
    widgets::Paragraph,
};

use crate::theme;

pub fn render(frame: &mut Frame, area: Rect, request: &PendingUserInputRequest, input_text: &str) {
    let question_text = request
        .questions
        .first()
        .map(|q| q.question.as_str())
        .unwrap_or("Server is asking for input:");

    let agent_label = request
        .requester_agent_nickname
        .as_deref()
        .unwrap_or("Server");

    let lines = vec![
        Line::from(vec![
            Span::styled(format!(" ▌{agent_label}: "), theme::bold()),
            Span::styled(question_text, theme::secondary()),
        ]),
        Line::from(vec![
            Span::styled(" > ", theme::accent()),
            Span::raw(input_text),
            Span::styled("█", theme::accent()),
        ]),
    ];

    frame.render_widget(
        Paragraph::new(lines).style(ratatui::style::Style::default().bg(theme::CODE_BG)),
        area,
    );
}

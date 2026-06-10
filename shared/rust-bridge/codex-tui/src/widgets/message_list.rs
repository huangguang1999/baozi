use codex_mobile_client::conversation::ConversationItem;
use ratatui::text::Line;

use crate::widgets::message_item;

/// Render all conversation items into a flat list of styled lines.
pub fn render_items(items: &[ConversationItem], width: u16) -> Vec<Line<'static>> {
    let mut lines: Vec<Line<'static>> = Vec::new();

    for item in items {
        let item_lines = message_item::render(item, width);
        lines.extend(item_lines);
        // Add blank separator between items
        lines.push(Line::default());
    }

    lines
}

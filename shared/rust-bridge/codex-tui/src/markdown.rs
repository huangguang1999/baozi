//! Lightweight markdown-to-ratatui Line converter.
//!
//! Handles the subset of markdown actually used by assistant messages:
//! headings, bold, italic, inline code, code blocks, lists, blockquotes,
//! horizontal rules, and links.

use ratatui::{
    style::{Modifier, Style},
    text::{Line, Span},
};

use crate::theme;

enum State {
    Normal,
    CodeBlock(String), // language
}

pub fn render(text: &str, _width: u16) -> Vec<Line<'static>> {
    let mut lines: Vec<Line<'static>> = Vec::new();
    let mut state = State::Normal;

    for raw_line in text.lines() {
        match &state {
            State::CodeBlock(_lang) => {
                if raw_line.trim_start().starts_with("```") {
                    state = State::Normal;
                    lines.push(Line::from(Span::styled(
                        " └───────────────────────────────────────",
                        Style::default().fg(theme::BORDER),
                    )));
                } else {
                    lines.push(Line::from(Span::styled(
                        format!(" │ {raw_line}"),
                        Style::default().fg(theme::FG).bg(theme::CODE_BG),
                    )));
                }
            }
            State::Normal => {
                let trimmed = raw_line.trim_start();

                // Code block start
                if trimmed.starts_with("```") {
                    let lang = trimmed.trim_start_matches('`').trim().to_string();
                    let label = if lang.is_empty() {
                        "code".to_string()
                    } else {
                        lang.clone()
                    };
                    lines.push(Line::from(vec![
                        Span::styled(format!(" ┌─ {label} "), Style::default().fg(theme::BORDER)),
                        Span::styled("─".repeat(30), Style::default().fg(theme::BORDER)),
                    ]));
                    state = State::CodeBlock(lang);
                    continue;
                }

                // Heading
                if trimmed.starts_with("# ") {
                    let heading = trimmed.trim_start_matches('#').trim();
                    lines.push(Line::from(Span::styled(
                        format!(" {heading}"),
                        Style::default()
                            .fg(theme::FG)
                            .add_modifier(Modifier::BOLD | Modifier::UNDERLINED),
                    )));
                    continue;
                }
                if trimmed.starts_with("## ") {
                    let heading = trimmed.trim_start_matches('#').trim();
                    lines.push(Line::from(Span::styled(
                        format!(" {heading}"),
                        Style::default().fg(theme::FG).add_modifier(Modifier::BOLD),
                    )));
                    continue;
                }

                // Horizontal rule
                if trimmed == "---" || trimmed == "***" || trimmed == "___" {
                    lines.push(Line::from(Span::styled(
                        " ────────────────────────────────────────",
                        theme::dim(),
                    )));
                    continue;
                }

                // Blockquote
                if trimmed.starts_with("> ") {
                    let content = trimmed.trim_start_matches('>').trim();
                    lines.push(Line::from(vec![
                        Span::styled(" ▎ ", theme::dim()),
                        Span::styled(content.to_string(), theme::secondary()),
                    ]));
                    continue;
                }

                // Unordered list
                if trimmed.starts_with("- ") || trimmed.starts_with("* ") {
                    let indent = raw_line.len() - trimmed.len();
                    let content = &trimmed[2..];
                    let pad = " ".repeat(indent + 1);
                    let spans = parse_inline_spans(content);
                    let mut line_spans = vec![Span::raw(format!("{pad}• "))];
                    line_spans.extend(spans);
                    lines.push(Line::from(line_spans));
                    continue;
                }

                // Ordered list
                if let Some(rest) = try_strip_ordered_list(trimmed) {
                    let indent = raw_line.len() - trimmed.len();
                    let pad = " ".repeat(indent + 1);
                    let spans = parse_inline_spans(rest);
                    let mut line_spans = vec![Span::raw(format!("{pad}"))];
                    // Include the number prefix
                    let prefix_end = trimmed.len() - rest.len();
                    line_spans.push(Span::raw(trimmed[..prefix_end].to_string()));
                    line_spans.extend(spans);
                    lines.push(Line::from(line_spans));
                    continue;
                }

                // Regular paragraph line with inline formatting
                let spans = parse_inline_spans(trimmed);
                let mut line_spans = vec![Span::raw(" ")];
                line_spans.extend(spans);
                lines.push(Line::from(line_spans));
            }
        }
    }

    lines
}

fn try_strip_ordered_list(s: &str) -> Option<&str> {
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() && bytes[i].is_ascii_digit() {
        i += 1;
    }
    if i > 0 && i < bytes.len() && bytes[i] == b'.' {
        let rest = &s[i + 1..];
        Some(rest.trim_start())
    } else {
        None
    }
}

/// Parse inline markdown: **bold**, *italic*, `code`, [text](url).
fn parse_inline_spans(text: &str) -> Vec<Span<'static>> {
    let mut spans: Vec<Span<'static>> = Vec::new();
    let mut remaining = text;

    while !remaining.is_empty() {
        // Inline code
        if remaining.starts_with('`') {
            if let Some(end) = remaining[1..].find('`') {
                let code = &remaining[1..1 + end];
                spans.push(Span::styled(
                    code.to_string(),
                    Style::default().fg(ratatui::style::Color::Cyan),
                ));
                remaining = &remaining[2 + end..];
                continue;
            }
        }

        // Bold
        if remaining.starts_with("**") {
            if let Some(end) = remaining[2..].find("**") {
                let bold_text = &remaining[2..2 + end];
                spans.push(Span::styled(
                    bold_text.to_string(),
                    Style::default().add_modifier(Modifier::BOLD),
                ));
                remaining = &remaining[4 + end..];
                continue;
            }
        }

        // Italic
        if remaining.starts_with('*') && !remaining.starts_with("**") {
            if let Some(end) = remaining[1..].find('*') {
                let italic_text = &remaining[1..1 + end];
                spans.push(Span::styled(
                    italic_text.to_string(),
                    Style::default().add_modifier(Modifier::ITALIC),
                ));
                remaining = &remaining[2 + end..];
                continue;
            }
        }

        // Link [text](url)
        if remaining.starts_with('[') {
            if let Some(bracket_end) = remaining.find("](") {
                let text_part = &remaining[1..bracket_end];
                let after_bracket = &remaining[bracket_end + 2..];
                if let Some(paren_end) = after_bracket.find(')') {
                    let url = &after_bracket[..paren_end];
                    spans.push(Span::styled(
                        text_part.to_string(),
                        Style::default().add_modifier(Modifier::UNDERLINED),
                    ));
                    spans.push(Span::styled(format!(" ({url})"), theme::dim()));
                    remaining = &after_bracket[paren_end + 1..];
                    continue;
                }
            }
        }

        // Regular character — consume until next special char
        let next_special = remaining
            .find(|c: char| c == '`' || c == '*' || c == '[')
            .unwrap_or(remaining.len());
        if next_special == 0 {
            // Special char that didn't match a pattern — just output it
            spans.push(Span::raw(remaining[..1].to_string()));
            remaining = &remaining[1..];
        } else {
            spans.push(Span::raw(remaining[..next_special].to_string()));
            remaining = &remaining[next_special..];
        }
    }

    spans
}

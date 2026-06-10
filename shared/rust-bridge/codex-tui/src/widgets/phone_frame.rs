//! Renders a phone-shaped bezel around content.

use ratatui::{
    Frame,
    layout::{Alignment, Constraint, Layout, Rect},
    style::Style,
    text::{Line, Span},
    widgets::{Block, Borders, Clear, Paragraph},
};

use crate::theme;

/// The phone width in columns (iPhone-ish proportions).
const PHONE_WIDTH: u16 = 48;

/// Render the phone frame and return the inner content area.
pub fn render_frame(frame: &mut Frame) -> Rect {
    let term = frame.area();

    // Center the phone horizontally, use full height
    let phone_w = PHONE_WIDTH.min(term.width);
    let phone_h = term.height;
    let x = (term.width.saturating_sub(phone_w)) / 2;
    let y = 0;

    let phone_area = Rect {
        x,
        y,
        width: phone_w,
        height: phone_h,
    };

    // Clear entire terminal
    frame.render_widget(Clear, term);

    // Draw the outer bezel
    let bezel = Block::default()
        .borders(Borders::ALL)
        .border_style(Style::default().fg(theme::FG_DIM))
        .border_type(ratatui::widgets::BorderType::Rounded);

    let inner = bezel.inner(phone_area);
    frame.render_widget(bezel, phone_area);

    // Split inner into: status bar (2) | content (flex) | home bar (1)
    let chunks = Layout::vertical([
        Constraint::Length(2),
        Constraint::Min(1),
        Constraint::Length(1),
    ])
    .split(inner);

    // ── Status bar (iOS-style) ──
    let status_area = chunks[0];
    let w = status_area.width as usize;

    // Row 1: notch centered
    let notch = "╭──────────╮";
    let notch_pad = w.saturating_sub(12) / 2;
    let notch_line = Line::from(vec![
        Span::raw(" ".repeat(notch_pad)),
        Span::styled(notch, Style::default().fg(theme::FG_DIM)),
    ]);
    frame.render_widget(
        Paragraph::new(notch_line),
        Rect {
            x: status_area.x,
            y: status_area.y,
            width: status_area.width,
            height: 1,
        },
    );

    // Row 2: time on left, battery on right
    // Use two separate paragraphs — left-aligned and right-aligned
    let time = chrono_time_str();
    let row2 = Rect {
        x: status_area.x,
        y: status_area.y + 1,
        width: status_area.width,
        height: 1,
    };

    // Left: time
    let time_line = Line::from(vec![
        Span::raw(" "),
        Span::styled(time, Style::default().fg(theme::FG)),
    ]);
    frame.render_widget(Paragraph::new(time_line), row2);

    // Right: battery (render right-aligned on same row)
    let battery_line = Line::from(vec![Span::styled(
        "■ ▂▅▇  100% ",
        Style::default().fg(theme::FG),
    )]);
    frame.render_widget(
        Paragraph::new(battery_line).alignment(Alignment::Right),
        row2,
    );

    // ── Home indicator bar ──
    let home_area = chunks[2];
    let home_line = Line::from(Span::styled("────────", Style::default().fg(theme::FG_DIM)));
    frame.render_widget(
        Paragraph::new(home_line).alignment(Alignment::Center),
        home_area,
    );

    // Return the content area
    chunks[1]
}

fn chrono_time_str() -> String {
    let now = std::time::SystemTime::now();
    let since_midnight = now
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
        % 86400;
    let hours = (since_midnight / 3600) % 24;
    let minutes = (since_midnight % 3600) / 60;
    let h12 = if hours == 0 {
        12
    } else if hours > 12 {
        hours - 12
    } else {
        hours
    };
    format!("{h12}:{minutes:02}")
}

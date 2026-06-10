use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::Modifier,
    text::{Line, Span},
    widgets::{Block, Borders, Clear, List, ListItem, ListState, Paragraph},
};

use crate::theme;
use crate::widgets::popup;

#[derive(Debug, Default)]
pub struct DiscoveryState {
    pub servers: Vec<DiscoveredServerEntry>,
    pub list_state: ListState,
    pub manual_host: String,
    pub manual_port: String,
    pub focus: DiscoveryFocus,
    pub is_scanning: bool,
    pub status_message: Option<String>,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub enum DiscoveryFocus {
    #[default]
    ServerList,
    ManualHost,
    ManualPort,
}

#[derive(Debug, Clone)]
pub struct DiscoveredServerEntry {
    pub id: String,
    pub name: String,
    pub host: String,
    pub port: u16,
    pub source: String,
    pub reachable: bool,
}

pub fn render(frame: &mut Frame, area: Rect, state: &mut DiscoveryState) {
    let popup_area = popup::centered_rect(95, 80, area);
    frame.render_widget(Clear, popup_area);

    let block = Block::default()
        .title(" Discover Servers ")
        .borders(Borders::ALL)
        .border_style(theme::border_focused());

    let inner = block.inner(popup_area);
    frame.render_widget(block, popup_area);

    let chunks = Layout::vertical([
        Constraint::Min(4),
        Constraint::Length(1),
        Constraint::Length(4),
        Constraint::Length(1),
    ])
    .split(inner);

    // Server list
    let items: Vec<ListItem> = state
        .servers
        .iter()
        .map(|s| {
            let sym = if s.reachable { "●" } else { "○" };
            let col = if s.reachable {
                theme::SUCCESS
            } else {
                theme::FG_DIM
            };
            let line = Line::from(vec![
                Span::styled(format!(" {sym} "), ratatui::style::Style::default().fg(col)),
                Span::styled(&s.name, theme::accent()),
                Span::styled(format!(" ({}) ", s.source), theme::dim()),
                Span::raw(format!("{}:{}", s.host, s.port)),
            ]);
            ListItem::new(line)
        })
        .collect();

    let list = List::new(items).highlight_style(theme::accent().add_modifier(Modifier::REVERSED));
    frame.render_stateful_widget(list, chunks[0], &mut state.list_state);

    // Divider
    frame.render_widget(
        Paragraph::new(" ─── Manual ───").style(theme::dim()),
        chunks[1],
    );

    // Manual entry
    let manual_chunks = Layout::vertical([
        Constraint::Length(1),
        Constraint::Length(1),
        Constraint::Length(1),
    ])
    .split(chunks[2]);

    let host_style = if state.focus == DiscoveryFocus::ManualHost {
        theme::accent()
    } else {
        theme::secondary()
    };
    let port_style = if state.focus == DiscoveryFocus::ManualPort {
        theme::accent()
    } else {
        theme::secondary()
    };

    let host_text = if state.manual_host.is_empty() {
        "hostname or IP"
    } else {
        &state.manual_host
    };
    let port_text = if state.manual_port.is_empty() {
        "8390"
    } else {
        &state.manual_port
    };

    frame.render_widget(
        Paragraph::new(Line::from(vec![
            Span::raw("  Host: "),
            Span::styled(format!("[{host_text}]"), host_style),
        ])),
        manual_chunks[0],
    );
    frame.render_widget(
        Paragraph::new(Line::from(vec![
            Span::raw("  Port: "),
            Span::styled(format!("[{port_text}]"), port_style),
        ])),
        manual_chunks[1],
    );

    // Status / hints
    let status_text = if let Some(msg) = &state.status_message {
        Span::styled(format!(" {msg}"), theme::error())
    } else if state.is_scanning {
        Span::styled(" Scanning...", theme::secondary())
    } else {
        Span::raw("")
    };

    let hints = Line::from(vec![
        status_text,
        Span::raw("  "),
        Span::styled("Enter", theme::accent()),
        Span::raw(":connect  "),
        Span::styled("r", theme::accent()),
        Span::raw(":rescan  "),
        Span::styled("Tab", theme::accent()),
        Span::raw(":manual  "),
        Span::styled("Esc", theme::accent()),
        Span::raw(":close"),
    ]);
    frame.render_widget(Paragraph::new(hints), chunks[3]);
}

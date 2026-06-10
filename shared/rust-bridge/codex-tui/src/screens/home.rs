use codex_mobile_client::store::{AppSnapshot, ServerHealthSnapshot};
use codex_mobile_client::types::ThreadKey;
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::Modifier,
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
};

use crate::theme;

#[derive(Debug, Default)]
pub struct HomeState {
    pub focus: HomeSection,
    pub sessions_state: ListState,
    pub servers_state: ListState,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub enum HomeSection {
    #[default]
    Sessions,
    Servers,
}

pub struct RecentSession {
    pub key: ThreadKey,
    pub title: String,
    pub server_name: String,
    pub cwd: String,
    pub is_active: bool,
}

pub struct ServerEntry {
    pub server_id: String,
    pub display_name: String,
    pub host: String,
    pub port: u16,
    pub health: ServerHealthSnapshot,
}

pub fn derive_recent_sessions(snapshot: &AppSnapshot) -> Vec<RecentSession> {
    let mut sessions: Vec<_> = snapshot
        .threads
        .iter()
        .map(|(key, thread)| {
            let server_name = snapshot
                .servers
                .get(&key.server_id)
                .map(|s| s.display_name.clone())
                .unwrap_or_else(|| key.server_id.clone());
            RecentSession {
                key: key.clone(),
                title: thread
                    .info
                    .title
                    .clone()
                    .unwrap_or_else(|| "Untitled".into()),
                server_name,
                cwd: thread.info.cwd.clone().unwrap_or_default(),
                is_active: thread.active_turn_id.is_some(),
            }
        })
        .collect();
    sessions.sort_by(|a, b| {
        b.is_active
            .cmp(&a.is_active)
            .then_with(|| a.title.cmp(&b.title))
    });
    sessions
}

pub fn derive_servers(snapshot: &AppSnapshot) -> Vec<ServerEntry> {
    snapshot
        .servers
        .values()
        .map(|s| ServerEntry {
            server_id: s.server_id.clone(),
            display_name: s.display_name.clone(),
            host: s.host.clone(),
            port: s.port,
            health: s.health.clone(),
        })
        .collect()
}

pub fn render(frame: &mut Frame, area: Rect, state: &mut HomeState, snapshot: &AppSnapshot) {
    let recent = derive_recent_sessions(snapshot);
    let servers = derive_servers(snapshot);

    let chunks = Layout::vertical([
        Constraint::Length(1),
        Constraint::Min(5),
        Constraint::Min(3),
        Constraint::Length(1),
    ])
    .split(area);

    // Title bar
    let server_count = servers.len();
    let title = Line::from(vec![
        Span::styled(" codex-tui ", theme::bold()),
        Span::raw("─".repeat(chunks[0].width.saturating_sub(30) as usize)),
        Span::styled(
            format!(
                " {} server{} ",
                server_count,
                if server_count == 1 { "" } else { "s" }
            ),
            theme::secondary(),
        ),
    ]);
    frame.render_widget(Paragraph::new(title), chunks[0]);

    // Recent sessions
    let session_items: Vec<ListItem> = recent
        .iter()
        .map(|s| {
            let active_indicator = if s.is_active { " ●" } else { "" };
            let line = Line::from(vec![
                Span::styled(&s.title, theme::accent()),
                Span::raw("  "),
                Span::styled(&s.server_name, theme::secondary()),
                Span::raw("  "),
                Span::styled(&s.cwd, theme::dim()),
                Span::styled(active_indicator, theme::accent()),
            ]);
            ListItem::new(line)
        })
        .collect();

    let sessions_border = if state.focus == HomeSection::Sessions {
        theme::border_focused()
    } else {
        theme::border()
    };
    let sessions_block = Block::default()
        .title(Line::from(vec![
            Span::raw(" Recent Sessions "),
            Span::styled("[n]ew ", theme::dim()),
        ]))
        .borders(Borders::ALL)
        .border_style(sessions_border);
    let sessions_list = List::new(session_items)
        .block(sessions_block)
        .highlight_style(theme::accent().add_modifier(Modifier::REVERSED));
    frame.render_stateful_widget(sessions_list, chunks[1], &mut state.sessions_state);

    // Connected servers
    let server_items: Vec<ListItem> = servers
        .iter()
        .map(|s| {
            let health_sym = theme::health_symbol(&s.health);
            let health_col = theme::health_color(&s.health);
            let line = Line::from(vec![
                Span::styled(
                    format!("{health_sym} "),
                    ratatui::style::Style::default().fg(health_col),
                ),
                Span::styled(&s.display_name, theme::accent()),
                Span::raw(format!(" ({}:{})", s.host, s.port)),
                Span::raw("  "),
                Span::styled(
                    format!("{:?}", s.health).to_lowercase(),
                    ratatui::style::Style::default().fg(health_col),
                ),
            ]);
            ListItem::new(line)
        })
        .collect();

    let servers_border = if state.focus == HomeSection::Servers {
        theme::border_focused()
    } else {
        theme::border()
    };
    let servers_block = Block::default()
        .title(Line::from(vec![
            Span::raw(" Connected Servers "),
            Span::styled("[c]onnect ", theme::dim()),
        ]))
        .borders(Borders::ALL)
        .border_style(servers_border);
    let servers_list = List::new(server_items)
        .block(servers_block)
        .highlight_style(theme::accent().add_modifier(Modifier::REVERSED));
    frame.render_stateful_widget(servers_list, chunks[2], &mut state.servers_state);

    // Key hints
    let hints = Line::from(vec![
        Span::styled(" n", theme::accent()),
        Span::raw(":new  "),
        Span::styled("c", theme::accent()),
        Span::raw(":connect  "),
        Span::styled("s", theme::accent()),
        Span::raw(":settings  "),
        Span::styled("q", theme::accent()),
        Span::raw(":quit"),
    ]);
    frame.render_widget(Paragraph::new(hints), chunks[3]);
}

use codex_mobile_client::store::AppSnapshot;
use codex_mobile_client::types::{ThreadKey, ThreadSummaryStatus};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::Modifier,
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
};
use std::collections::BTreeMap;

use crate::theme;

#[derive(Debug, Default)]
pub struct SessionsState {
    pub list_state: ListState,
    pub search_query: String,
    pub search_active: bool,
    /// Flattened index → ThreadKey mapping
    pub visible_keys: Vec<ThreadKey>,
}

struct SessionRow {
    key: ThreadKey,
    title: String,
    model: String,
    is_active: bool,
    is_fork: bool,
}

pub fn render(frame: &mut Frame, area: Rect, state: &mut SessionsState, snapshot: &AppSnapshot) {
    let chunks = Layout::vertical([
        Constraint::Length(1),
        Constraint::Length(1),
        Constraint::Min(1),
        Constraint::Length(1),
    ])
    .split(area);

    // Header
    let header = Line::from(vec![
        Span::styled(" Sessions ", theme::bold()),
        Span::raw("─".repeat(chunks[0].width.saturating_sub(35) as usize)),
        Span::styled(" /", theme::accent()),
        Span::raw(":search  "),
        Span::styled("Esc", theme::accent()),
        Span::raw(":back "),
    ]);
    frame.render_widget(Paragraph::new(header), chunks[0]);

    // Search bar
    if state.search_active || !state.search_query.is_empty() {
        let search_line = Line::from(vec![
            Span::raw(" 🔍 "),
            Span::styled(&state.search_query, theme::accent()),
            if state.search_active {
                Span::styled("█", theme::accent())
            } else {
                Span::raw("")
            },
        ]);
        frame.render_widget(Paragraph::new(search_line), chunks[1]);
    }

    // Group threads by workspace (cwd)
    let mut groups: BTreeMap<String, Vec<SessionRow>> = BTreeMap::new();
    for (key, thread) in &snapshot.threads {
        let title = thread
            .info
            .title
            .clone()
            .unwrap_or_else(|| "Untitled".into());

        // Search filter
        if !state.search_query.is_empty() {
            let q = state.search_query.to_lowercase();
            if !title.to_lowercase().contains(&q) {
                continue;
            }
        }

        let cwd = thread.info.cwd.clone().unwrap_or_else(|| "~/".into());
        let model = thread.model.clone().unwrap_or_default();
        let is_active = thread.active_turn_id.is_some()
            || matches!(thread.info.status, ThreadSummaryStatus::Active);
        let is_fork = thread.info.parent_thread_id.is_some();

        groups.entry(cwd).or_default().push(SessionRow {
            key: key.clone(),
            title,
            model,
            is_active,
            is_fork,
        });
    }

    // Flatten into list items
    let mut items: Vec<ListItem> = Vec::new();
    let mut visible_keys: Vec<ThreadKey> = Vec::new();

    for (cwd, sessions) in &groups {
        // Workspace header (not selectable, but we include a dummy key)
        items.push(ListItem::new(Line::from(vec![Span::styled(
            format!(" {cwd}"),
            theme::bold(),
        )])));
        // Push a dummy key for workspace headers
        visible_keys.push(ThreadKey {
            server_id: String::new(),
            thread_id: String::new(),
        });

        for (i, session) in sessions.iter().enumerate() {
            let prefix = if i == sessions.len() - 1 {
                "  └─ "
            } else {
                "  ├─ "
            };
            let active = if session.is_active { " ●" } else { "" };
            let fork_badge = if session.is_fork { " [fork]" } else { "" };

            let line = Line::from(vec![
                Span::styled(prefix, theme::dim()),
                Span::styled(&session.title, theme::accent()),
                Span::raw("  "),
                Span::styled(&session.model, theme::secondary()),
                Span::styled(active, theme::accent()),
                Span::styled(
                    fork_badge,
                    ratatui::style::Style::default().fg(ratatui::style::Color::Cyan),
                ),
            ]);
            items.push(ListItem::new(line));
            visible_keys.push(session.key.clone());
        }
    }

    state.visible_keys = visible_keys;

    let block = Block::default()
        .borders(Borders::ALL)
        .border_style(theme::border_focused());
    let list = List::new(items)
        .block(block)
        .highlight_style(theme::accent().add_modifier(Modifier::REVERSED));
    frame.render_stateful_widget(list, chunks[2], &mut state.list_state);

    // Key hints
    let hints = Line::from(vec![
        Span::styled(" n", theme::accent()),
        Span::raw(":new  "),
        Span::styled("r", theme::accent()),
        Span::raw(":rename  "),
        Span::styled("d", theme::accent()),
        Span::raw(":delete  "),
        Span::styled("/", theme::accent()),
        Span::raw(":search  "),
        Span::styled("Enter", theme::accent()),
        Span::raw(":open"),
    ]);
    frame.render_widget(Paragraph::new(hints), chunks[3]);
}

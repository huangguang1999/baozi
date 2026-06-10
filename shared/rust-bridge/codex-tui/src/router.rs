use crate::screens::{
    conversation::ConversationState, discovery::DiscoveryState, home::HomeState,
    sessions::SessionsState, settings::SettingsState,
};

#[derive(Debug)]
pub enum Screen {
    Home(HomeState),
    Sessions(SessionsState),
    Conversation(ConversationState),
}

#[derive(Debug)]
pub enum Overlay {
    Discovery(DiscoveryState),
    Settings(SettingsState),
    Confirm {
        message: String,
        action: ConfirmAction,
    },
}

#[derive(Debug, Clone)]
pub enum ConfirmAction {
    DeleteSession {
        server_id: String,
        thread_id: String,
    },
    DisconnectServer {
        server_id: String,
    },
}

pub struct Router {
    pub stack: Vec<Screen>,
    pub overlay: Option<Overlay>,
}

impl Router {
    pub fn new() -> Self {
        Self {
            stack: vec![Screen::Home(HomeState::default())],
            overlay: None,
        }
    }

    pub fn push(&mut self, screen: Screen) {
        self.stack.push(screen);
    }

    pub fn pop(&mut self) -> bool {
        if self.stack.len() > 1 {
            self.stack.pop();
            true
        } else {
            false
        }
    }

    pub fn current(&self) -> &Screen {
        self.stack.last().expect("router stack is never empty")
    }

    pub fn current_mut(&mut self) -> &mut Screen {
        self.stack.last_mut().expect("router stack is never empty")
    }

    pub fn open_overlay(&mut self, overlay: Overlay) {
        self.overlay = Some(overlay);
    }

    pub fn close_overlay(&mut self) {
        self.overlay = None;
    }

    pub fn has_overlay(&self) -> bool {
        self.overlay.is_some()
    }
}

//! C FFI shim for voice handoff orchestration.
//!
//! All business logic lives in `codex_mobile_client::session::voice_handoff`.
//! This module re-exports the types and provides `extern "C"` entry points
//! so the current Swift bridge can continue to call through the C API until
//! UniFFI bindings replace it.

use std::ffi::c_void;
use std::os::raw::c_char;
use std::sync::Arc;

// Re-export all public types from the shared crate so existing in-crate
// consumers (tests, etc.) continue to work via `crate::voice_handoff::*`.
pub use codex_mobile_client::session::voice_handoff::{
    ConnectedServer, HandoffAction, HandoffEntry, HandoffManager, HandoffPhase, HandoffTurnConfig,
    StreamedItem, TranscriptBuffer,
};

// ---------------------------------------------------------------------------
// C FFI
// ---------------------------------------------------------------------------

/// Create a new `HandoffManager`. Returns an opaque pointer.
/// Caller must free with `codex_handoff_destroy`.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_create(
    local_server_id: *const c_char,
    local_server_id_len: usize,
) -> *mut c_void {
    let id = unsafe { string_from_raw(local_server_id, local_server_id_len) };
    let manager = Arc::new(HandoffManager::new(&id));
    Arc::into_raw(manager) as *mut c_void
}

/// Destroy a `HandoffManager`.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_destroy(handle: *mut c_void) {
    if handle.is_null() {
        return;
    }
    unsafe {
        let _ = Arc::from_raw(handle as *const HandoffManager);
    }
}

/// Register a connected server.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_register_server(
    handle: *mut c_void,
    server_id: *const c_char,
    server_id_len: usize,
    name: *const c_char,
    name_len: usize,
    hostname: *const c_char,
    hostname_len: usize,
    is_local: bool,
    is_connected: bool,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let server = ConnectedServer {
        server_id: unsafe { string_from_raw(server_id, server_id_len) },
        name: unsafe { string_from_raw(name, name_len) },
        hostname: unsafe { string_from_raw(hostname, hostname_len) },
        is_local,
        is_connected,
    };
    manager.register_server(server);
    // Don't drop — Arc is borrowed.
    std::mem::forget(manager);
}

/// Unregister a server.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_unregister_server(
    handle: *mut c_void,
    server_id: *const c_char,
    server_id_len: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    manager.unregister_server(&unsafe { string_from_raw(server_id, server_id_len) });
    std::mem::forget(manager);
}

/// Set turn config (model/effort/fast).
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_set_turn_config(
    handle: *mut c_void,
    model: *const c_char,
    model_len: usize,
    effort: *const c_char,
    effort_len: usize,
    fast_mode: bool,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let config = HandoffTurnConfig {
        model: nonempty_string_from_raw(model, model_len),
        effort: nonempty_string_from_raw(effort, effort_len),
        fast_mode,
    };
    manager.set_turn_config(config);
    std::mem::forget(manager);
}

/// Process a handoff request. Call `codex_handoff_drain_actions` afterward.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_request(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
    voice_server_id: *const c_char,
    voice_server_id_len: usize,
    voice_thread_id: *const c_char,
    voice_thread_id_len: usize,
    input_transcript: *const c_char,
    input_transcript_len: usize,
    active_transcript: *const c_char,
    active_transcript_len: usize,
    server_hint: *const c_char,
    server_hint_len: usize,
    fallback_transcript: *const c_char,
    fallback_transcript_len: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    let voice_key = codex_mobile_client::types::models::ThreadKey {
        server_id: unsafe { string_from_raw(voice_server_id, voice_server_id_len) },
        thread_id: unsafe { string_from_raw(voice_thread_id, voice_thread_id_len) },
    };
    let input = unsafe { string_from_raw(input_transcript, input_transcript_len) };
    let active = unsafe { string_from_raw(active_transcript, active_transcript_len) };
    let hint = nonempty_string_from_raw(server_hint, server_hint_len);
    let fallback = nonempty_string_from_raw(fallback_transcript, fallback_transcript_len);

    manager.handle_handoff_request(
        &hid,
        voice_key,
        &input,
        &active,
        hint.as_deref(),
        fallback.as_deref(),
    );
    std::mem::forget(manager);
}

/// Report thread created. Call after StartThread action completes.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_report_thread_created(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
    server_id: *const c_char,
    server_id_len: usize,
    thread_id: *const c_char,
    thread_id_len: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    let key = codex_mobile_client::types::models::ThreadKey {
        server_id: unsafe { string_from_raw(server_id, server_id_len) },
        thread_id: unsafe { string_from_raw(thread_id, thread_id_len) },
    };
    manager.report_thread_created(&hid, key);
    std::mem::forget(manager);
}

/// Report thread creation failed.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_report_thread_failed(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
    error: *const c_char,
    error_len: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    let err = unsafe { string_from_raw(error, error_len) };
    manager.report_thread_creation_failed(&hid, &err);
    std::mem::forget(manager);
}

/// Report turn was sent.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_report_turn_sent(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
    base_item_count: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    manager.report_turn_sent(&hid, base_item_count);
    std::mem::forget(manager);
}

/// Report turn send failed.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_report_turn_failed(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
    error: *const c_char,
    error_len: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    let err = unsafe { string_from_raw(error, error_len) };
    manager.report_turn_send_failed(&hid, &err);
    std::mem::forget(manager);
}

/// Report finalization completed.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_report_finalized(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    manager.report_finalized(&hid);
    std::mem::forget(manager);
}

/// Reset all handoff state (call when voice session ends).
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_reset(handle: *mut c_void) {
    let manager = unsafe { arc_from_raw(handle) };
    manager.reset();
    std::mem::forget(manager);
}

/// Get the number of pending actions.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_action_count(handle: *mut c_void) -> usize {
    let manager = unsafe { arc_from_raw(handle) };
    let count = manager.action_count();
    std::mem::forget(manager);
    count
}

/// Drain all pending actions as a JSON array string.
/// Caller must free the returned pointer with `codex_handoff_free_string`.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_drain_actions_json(
    handle: *mut c_void,
    out_len: *mut usize,
) -> *mut c_char {
    let manager = unsafe { arc_from_raw(handle) };
    let actions = manager.drain_actions();
    std::mem::forget(manager);

    let json_actions: Vec<serde_json::Value> = actions.into_iter().map(action_to_json).collect();
    let json_str = serde_json::to_string(&json_actions).unwrap_or_else(|_| "[]".to_string());

    let bytes = json_str.into_bytes();
    let len = bytes.len();
    let ptr = bytes.as_ptr() as *mut c_char;
    std::mem::forget(bytes);
    unsafe {
        *out_len = len;
    }
    ptr
}

/// Free a string returned by `codex_handoff_drain_actions_json`.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_free_string(ptr: *mut c_char, len: usize) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        let _ = Vec::from_raw_parts(ptr as *mut u8, len, len);
    }
}

/// Poll stream progress with item data. Items is a JSON array of
/// `[{"id": "...", "text": "..."}]`.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_poll_stream(
    handle: *mut c_void,
    handoff_id: *const c_char,
    handoff_id_len: usize,
    items_json: *const c_char,
    items_json_len: usize,
    turn_active: bool,
) {
    let manager = unsafe { arc_from_raw(handle) };
    let hid = unsafe { string_from_raw(handoff_id, handoff_id_len) };
    let json_str = unsafe { string_from_raw(items_json, items_json_len) };

    let items: Vec<StreamedItem> = serde_json::from_str::<Vec<ItemJson>>(&json_str)
        .unwrap_or_default()
        .into_iter()
        .map(|j| StreamedItem {
            item_id: j.id,
            text: j.text,
        })
        .collect();

    manager.poll_stream_progress(&hid, &items, turn_active);
    std::mem::forget(manager);
}

/// Accumulate a transcript delta. Returns the full text via out params.
/// If the speaker changed, `out_previous_text` is non-null and should be flushed.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_accumulate_transcript(
    handle: *mut c_void,
    delta: *const c_char,
    delta_len: usize,
    speaker: *const c_char,
    speaker_len: usize,
    out_full_text: *mut *mut c_char,
    out_full_text_len: *mut usize,
    out_previous_text: *mut *mut c_char,
    out_previous_text_len: *mut usize,
) -> bool {
    let manager = unsafe { arc_from_raw(handle) };
    let d = unsafe { string_from_raw(delta, delta_len) };
    let s = unsafe { string_from_raw(speaker, speaker_len) };
    let (full, previous, changed) = manager.accumulate_transcript_delta(&d, &s);
    std::mem::forget(manager);

    unsafe {
        let full_bytes = full.into_bytes();
        *out_full_text_len = full_bytes.len();
        *out_full_text = full_bytes.as_ptr() as *mut c_char;
        std::mem::forget(full_bytes);

        if let Some(prev) = previous {
            let prev_bytes = prev.into_bytes();
            *out_previous_text_len = prev_bytes.len();
            *out_previous_text = prev_bytes.as_ptr() as *mut c_char;
            std::mem::forget(prev_bytes);
        } else {
            *out_previous_text = std::ptr::null_mut();
            *out_previous_text_len = 0;
        }
    }
    changed
}

/// Get the list_servers JSON response.
#[unsafe(no_mangle)]
pub extern "C" fn codex_handoff_list_servers_json(
    handle: *mut c_void,
    out_len: *mut usize,
) -> *mut c_char {
    let manager = unsafe { arc_from_raw(handle) };
    let json = manager.list_servers_response();
    std::mem::forget(manager);

    let bytes = json.into_bytes();
    let len = bytes.len();
    let ptr = bytes.as_ptr() as *mut c_char;
    std::mem::forget(bytes);
    unsafe {
        *out_len = len;
    }
    ptr
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

#[derive(serde::Deserialize)]
struct ItemJson {
    id: String,
    text: String,
}

unsafe fn string_from_raw(ptr: *const c_char, len: usize) -> String {
    if ptr.is_null() || len == 0 {
        return String::new();
    }
    let bytes = unsafe { std::slice::from_raw_parts(ptr as *const u8, len) };
    String::from_utf8_lossy(bytes).into_owned()
}

fn nonempty_string_from_raw(ptr: *const c_char, len: usize) -> Option<String> {
    let s = unsafe { string_from_raw(ptr, len) };
    if s.trim().is_empty() { None } else { Some(s) }
}

unsafe fn arc_from_raw(handle: *mut c_void) -> Arc<HandoffManager> {
    unsafe { Arc::from_raw(handle as *const HandoffManager) }
}

fn action_to_json(action: HandoffAction) -> serde_json::Value {
    match action {
        HandoffAction::StartThread {
            handoff_id,
            target_server_id,
            is_local,
            cwd,
        } => serde_json::json!({
            "type": "start_thread",
            "handoff_id": handoff_id,
            "target_server_id": target_server_id,
            "is_local": is_local,
            "cwd": cwd,
        }),
        HandoffAction::SendTurn {
            handoff_id,
            target_server_id,
            thread_id,
            transcript,
            config,
        } => serde_json::json!({
            "type": "send_turn",
            "handoff_id": handoff_id,
            "target_server_id": target_server_id,
            "thread_id": thread_id,
            "transcript": transcript,
            "model": config.model,
            "effort": config.effort,
            "fast_mode": config.fast_mode,
        }),
        HandoffAction::ResolveHandoff {
            handoff_id,
            voice_thread_key,
            text,
        } => serde_json::json!({
            "type": "resolve_handoff",
            "handoff_id": handoff_id,
            "voice_server_id": voice_thread_key.server_id,
            "voice_thread_id": voice_thread_key.thread_id,
            "text": text,
        }),
        HandoffAction::FinalizeHandoff {
            handoff_id,
            voice_thread_key,
        } => serde_json::json!({
            "type": "finalize_handoff",
            "handoff_id": handoff_id,
            "voice_server_id": voice_thread_key.server_id,
            "voice_thread_id": voice_thread_key.thread_id,
        }),
        HandoffAction::UpdateHandoffItem {
            handoff_id,
            voice_thread_key,
            remote_thread_key,
        } => serde_json::json!({
            "type": "update_handoff_item",
            "handoff_id": handoff_id,
            "voice_server_id": voice_thread_key.server_id,
            "voice_thread_id": voice_thread_key.thread_id,
            "remote_server_id": remote_thread_key.server_id,
            "remote_thread_id": remote_thread_key.thread_id,
        }),
        HandoffAction::CompleteHandoffItem {
            handoff_id,
            voice_thread_key,
        } => serde_json::json!({
            "type": "complete_handoff_item",
            "handoff_id": handoff_id,
            "voice_server_id": voice_thread_key.server_id,
            "voice_thread_id": voice_thread_key.thread_id,
        }),
        HandoffAction::SetVoicePhase { phase_name } => serde_json::json!({
            "type": "set_voice_phase",
            "phase": phase_name,
        }),
        HandoffAction::Error {
            handoff_id,
            message,
        } => serde_json::json!({
            "type": "error",
            "handoff_id": handoff_id,
            "message": message,
        }),
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use codex_mobile_client::types::models::ThreadKey;

    #[test]
    fn test_basic_handoff_lifecycle() {
        let mgr = HandoffManager::new("local");

        mgr.register_server(ConnectedServer {
            server_id: "local".into(),
            name: "local".into(),
            hostname: "localhost".into(),
            is_local: true,
            is_connected: true,
        });
        mgr.register_server(ConnectedServer {
            server_id: "remote-1".into(),
            name: "devbox".into(),
            hostname: "devbox.example.com".into(),
            is_local: false,
            is_connected: true,
        });

        let voice_key = ThreadKey {
            server_id: "local".into(),
            thread_id: "voice-thread".into(),
        };

        mgr.handle_handoff_request(
            "handoff-1",
            voice_key.clone(),
            "please run tests on devbox",
            "",
            Some("devbox"),
            None,
        );

        let actions = mgr.drain_actions();
        assert!(actions.len() >= 2); // SetVoicePhase + StartThread

        let start = actions
            .iter()
            .find(|a| matches!(a, HandoffAction::StartThread { .. }));
        assert!(start.is_some());

        // Report thread created.
        let remote_key = ThreadKey {
            server_id: "remote-1".into(),
            thread_id: "remote-thread-1".into(),
        };
        mgr.report_thread_created("handoff-1", remote_key.clone());

        let actions = mgr.drain_actions();
        let send = actions
            .iter()
            .find(|a| matches!(a, HandoffAction::SendTurn { .. }));
        assert!(send.is_some());

        // Report turn sent.
        mgr.report_turn_sent("handoff-1", 0);
        assert_eq!(
            mgr.handoff_phase("handoff-1"),
            Some(HandoffPhase::Streaming)
        );

        // Poll with items.
        let items = vec![StreamedItem {
            item_id: "item-1".into(),
            text: "Tests passed!".into(),
        }];
        mgr.poll_stream_progress("handoff-1", &items, false);

        let actions = mgr.drain_actions();
        let resolve = actions
            .iter()
            .find(|a| matches!(a, HandoffAction::ResolveHandoff { .. }));
        assert!(resolve.is_some());
        let finalize = actions
            .iter()
            .find(|a| matches!(a, HandoffAction::FinalizeHandoff { .. }));
        assert!(finalize.is_some());

        // Report finalized.
        mgr.report_finalized("handoff-1");
        assert_eq!(
            mgr.handoff_phase("handoff-1"),
            Some(HandoffPhase::Completed)
        );

        // Thread should be reusable.
        assert_eq!(mgr.reused_thread("remote-1"), Some(remote_key));
    }

    #[test]
    fn test_transcript_buffering() {
        let mgr = HandoffManager::new("local");

        let (full, prev, changed) = mgr.accumulate_transcript_delta("Hello", "Codex");
        assert_eq!(full, "Hello");
        assert!(prev.is_none());
        assert!(changed); // first delta always changes speaker

        let (full, prev, changed) = mgr.accumulate_transcript_delta(" world", "Codex");
        assert_eq!(full, "Hello world");
        assert!(prev.is_none());
        assert!(!changed);

        let (full, prev, changed) = mgr.accumulate_transcript_delta("Hi", "You");
        assert_eq!(full, "Hi");
        assert_eq!(prev, Some("Hello world".to_string()));
        assert!(changed);
    }

    #[test]
    fn test_server_not_found() {
        let mgr = HandoffManager::new("local");

        let voice_key = ThreadKey {
            server_id: "local".into(),
            thread_id: "voice-thread".into(),
        };

        mgr.handle_handoff_request(
            "handoff-2",
            voice_key,
            "run something",
            "",
            Some("nonexistent"),
            None,
        );

        let actions = mgr.drain_actions();
        let resolve = actions
            .iter()
            .find(|a| matches!(a, HandoffAction::ResolveHandoff { text, .. } if text.contains("not available")));
        assert!(resolve.is_some());
        assert_eq!(mgr.handoff_phase("handoff-2"), Some(HandoffPhase::Failed));
    }

    #[test]
    fn test_list_servers_response() {
        let mgr = HandoffManager::new("local");
        mgr.register_server(ConnectedServer {
            server_id: "local".into(),
            name: "my-machine".into(),
            hostname: "localhost".into(),
            is_local: true,
            is_connected: true,
        });
        let json = mgr.list_servers_response();
        assert!(json.contains("\"name\":\"local\""));
        assert!(json.contains("\"isLocal\":true"));
    }
}

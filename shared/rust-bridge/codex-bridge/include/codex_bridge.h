#pragma once
#include <stdint.h>
#include <stddef.h>

/// Start the codex app-server on a random loopback port.
/// On success returns 0 and writes the port to *out_port.
/// On failure returns a negative error code.
int codex_start_server(uint16_t *out_port);

/// Stop the codex app-server (currently a no-op).
void codex_stop_server(void);

// ---------------------------------------------------------------------------
// In-process channel transport (no WebSocket, no TCP)
// ---------------------------------------------------------------------------

/// Callback invoked from a background thread for every server-to-client message.
/// `json` points to a UTF-8 JSON-RPC string of `json_len` bytes (not null-terminated).
/// The callback must not block.
typedef void (*codex_message_callback)(void *ctx, const char *json, size_t json_len);

/// Open an in-process channel to the codex app-server.
/// Performs the initialize handshake internally before returning.
/// On success returns 0 and writes an opaque handle to *out_handle.
/// The callback will be invoked from a background thread.
int codex_channel_open(codex_message_callback callback, void *ctx, void **out_handle);

/// Send a JSON-RPC message from client to server.
/// `json` is a UTF-8 JSON-RPC string of `json_len` bytes.
/// Returns 0 on success, negative on failure.
int codex_channel_send(void *handle, const char *json, size_t json_len);

/// Close the channel and release resources.
void codex_channel_close(void *handle);

// ---------------------------------------------------------------------------
// Voice Handoff Manager
// ---------------------------------------------------------------------------

/// Create a new handoff manager. Caller must free with codex_handoff_destroy.
void *codex_handoff_create(const char *local_server_id, size_t local_server_id_len);

/// Destroy a handoff manager.
void codex_handoff_destroy(void *handle);

/// Register a connected server.
void codex_handoff_register_server(
    void *handle,
    const char *server_id, size_t server_id_len,
    const char *name, size_t name_len,
    const char *hostname, size_t hostname_len,
    _Bool is_local,
    _Bool is_connected
);

/// Unregister a server.
void codex_handoff_unregister_server(void *handle, const char *server_id, size_t server_id_len);

/// Set turn config (model/effort/fast).
void codex_handoff_set_turn_config(
    void *handle,
    const char *model, size_t model_len,
    const char *effort, size_t effort_len,
    _Bool fast_mode
);

/// Process a handoff_request item from the realtime session.
void codex_handoff_request(
    void *handle,
    const char *handoff_id, size_t handoff_id_len,
    const char *voice_server_id, size_t voice_server_id_len,
    const char *voice_thread_id, size_t voice_thread_id_len,
    const char *input_transcript, size_t input_transcript_len,
    const char *active_transcript, size_t active_transcript_len,
    const char *server_hint, size_t server_hint_len,
    const char *fallback_transcript, size_t fallback_transcript_len
);

/// Report that a thread was created for a handoff.
void codex_handoff_report_thread_created(
    void *handle,
    const char *handoff_id, size_t handoff_id_len,
    const char *server_id, size_t server_id_len,
    const char *thread_id, size_t thread_id_len
);

/// Report that thread creation failed.
void codex_handoff_report_thread_failed(
    void *handle,
    const char *handoff_id, size_t handoff_id_len,
    const char *error, size_t error_len
);

/// Report that the turn was sent.
void codex_handoff_report_turn_sent(
    void *handle,
    const char *handoff_id, size_t handoff_id_len,
    size_t base_item_count
);

/// Report that the turn send failed.
void codex_handoff_report_turn_failed(
    void *handle,
    const char *handoff_id, size_t handoff_id_len,
    const char *error, size_t error_len
);

/// Report that finalization completed.
void codex_handoff_report_finalized(
    void *handle,
    const char *handoff_id, size_t handoff_id_len
);

/// Reset all handoff state.
void codex_handoff_reset(void *handle);

/// Get the number of pending actions.
size_t codex_handoff_action_count(void *handle);

/// Drain all pending actions as a JSON array string.
/// Caller must free with codex_handoff_free_string.
char *codex_handoff_drain_actions_json(void *handle, size_t *out_len);

/// Free a string returned by handoff FFI functions.
void codex_handoff_free_string(char *ptr, size_t len);

/// Poll stream progress with items JSON.
void codex_handoff_poll_stream(
    void *handle,
    const char *handoff_id, size_t handoff_id_len,
    const char *items_json, size_t items_json_len,
    _Bool turn_active
);

/// Accumulate a transcript delta. Returns whether the speaker changed.
_Bool codex_handoff_accumulate_transcript(
    void *handle,
    const char *delta, size_t delta_len,
    const char *speaker, size_t speaker_len,
    char **out_full_text, size_t *out_full_text_len,
    char **out_previous_text, size_t *out_previous_text_len
);

/// Get the list_servers JSON response.
char *codex_handoff_list_servers_json(void *handle, size_t *out_len);

// ---------------------------------------------------------------------------
// Conversation Hydration
// ---------------------------------------------------------------------------

/// Hydrate a JSON array of upstream Turn objects into a JSON array of
/// ConversationItem suitable for UI rendering.
/// Returns a heap-allocated null-terminated UTF-8 JSON string on success
/// (caller must free with codex_free_string), or NULL on failure.
char *codex_hydrate_turns(const char *turns_json, size_t turns_json_len, size_t *out_len);

/// Free a string returned by codex_hydrate_turns.
void codex_free_string(char *ptr);

// ---------------------------------------------------------------------------
// MobileClient FFI
// ---------------------------------------------------------------------------

typedef void (*codex_message_callback)(void *ctx, const char *json, size_t json_len);

/// Create a new MobileClient. Returns 0 on success.
int codex_mobile_client_init(codex_message_callback callback, void *ctx, void **out_handle);

/// Destroy a MobileClient.
void codex_mobile_client_destroy(void *handle);

/// Call a method on the MobileClient. The response is delivered via response_cb.
int codex_mobile_client_call(void *handle,
                             const char *json, size_t json_len,
                             codex_message_callback response_cb, void *response_ctx);

/// Subscribe to events from the MobileClient.
int codex_mobile_client_subscribe_events(void *handle,
                                         codex_message_callback event_cb, void *event_ctx);

//! PowerShell-over-SSH wraps its error and progress streams in CLIXML
//! frames that look like:
//!
//! ```text
//! #< CLIXML
//! <Objs Version="1.1.0.1" xmlns="..."><S S="Error">'node' is not recognized…</S></Objs>
//! ```
//!
//! `strip_clixml` is the entry point: it removes the CLIXML envelope from
//! each line and recovers any human-readable text that lived inside `<S>`
//! tags, so a remote PowerShell error doesn't reach the user as gibberish.

/// Remove CLIXML lines from `output`, preserving extracted text from `<S>`
/// tags.
pub(super) fn strip_clixml(output: &str) -> String {
    let mut result_lines: Vec<&str> = Vec::new();
    let mut extracted: Vec<String> = Vec::new();

    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("#< CLIXML") {
            continue;
        }
        if trimmed.starts_with("<Objs ") || trimmed.starts_with("<Objs>") {
            let text = extract_clixml_text(trimmed);
            if !text.is_empty() {
                extracted.push(text);
            }
            continue;
        }
        result_lines.push(line);
    }

    let mut out = result_lines.join("\n");
    if !extracted.is_empty() {
        let joined = extracted.join("\n");
        if out.is_empty() {
            out = joined;
        } else {
            out.push('\n');
            out.push_str(&joined);
        }
    }
    out
}

/// Extract human-readable text from a CLIXML `<Objs>` line by parsing
/// `<S S="...">text</S>` tags and decoding CLIXML escape sequences like
/// `_x000D__x000A_` (CRLF).
fn extract_clixml_text(clixml: &str) -> String {
    let mut texts = Vec::new();
    let mut remaining = clixml;
    while let Some(s_start) = remaining.find("<S ") {
        let after = &remaining[s_start..];
        let Some(tag_end) = after.find('>') else {
            break;
        };
        let content_start = &after[tag_end + 1..];
        let Some(close) = content_start.find("</S>") else {
            break;
        };
        let raw = &content_start[..close];
        let decoded = raw
            .replace("_x000D__x000A_", "\n")
            .replace("_x000A_", "\n")
            .replace("_x000D_", "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'");
        let trimmed = decoded.trim();
        if !trimmed.is_empty() {
            texts.push(trimmed.to_string());
        }
        remaining = &content_start[close + 4..];
    }
    texts.join("\n")
}

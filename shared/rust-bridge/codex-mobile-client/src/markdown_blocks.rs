#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MarkdownBlock {
    Markdown(String),
    CodeBlock {
        language: Option<String>,
        code: String,
    },
    ThematicBreak,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum MarkdownLineKind {
    Paragraph,
    Heading,
    BlockQuote,
    ListItem,
}

pub fn render_markdown_blocks(input: &str) -> Vec<MarkdownBlock> {
    let mut blocks = Vec::new();
    let mut current_markdown = String::new();
    let mut current_kind: Option<MarkdownLineKind> = None;
    let mut current_fence: Option<CodeFenceState> = None;

    for raw_line in split_lines_preserving_terminator(input) {
        let line = raw_line.trim_end_matches('\n').trim_end_matches('\r');
        let trimmed = line.trim();

        if let Some(fence) = current_fence.as_mut() {
            if is_closing_code_fence(trimmed, fence.fence_char, fence.fence_len) {
                blocks.push(MarkdownBlock::CodeBlock {
                    language: fence.language.take(),
                    code: std::mem::take(&mut fence.code),
                });
                current_fence = None;
            } else {
                fence.code.push_str(line);
                if raw_line.ends_with('\n') {
                    fence.code.push('\n');
                }
            }
            continue;
        }

        if let Some(fence) = parse_opening_code_fence(trimmed) {
            flush_markdown_block(&mut blocks, &mut current_markdown, &mut current_kind);
            current_fence = Some(fence);
            continue;
        }

        if trimmed.is_empty() {
            flush_markdown_block(&mut blocks, &mut current_markdown, &mut current_kind);
            continue;
        }

        if is_thematic_break(trimmed) {
            flush_markdown_block(&mut blocks, &mut current_markdown, &mut current_kind);
            blocks.push(MarkdownBlock::ThematicBreak);
            continue;
        }

        let next_kind = classify_markdown_line(trimmed);
        let should_continue = match current_kind {
            None => false,
            Some(MarkdownLineKind::Paragraph) => next_kind == MarkdownLineKind::Paragraph,
            Some(MarkdownLineKind::Heading) => false,
            Some(MarkdownLineKind::BlockQuote) => next_kind == MarkdownLineKind::BlockQuote,
            Some(MarkdownLineKind::ListItem) => should_continue_list_item(trimmed, next_kind),
        };

        if !should_continue {
            flush_markdown_block(&mut blocks, &mut current_markdown, &mut current_kind);
            current_kind = Some(next_kind);
        }

        if current_markdown.is_empty() {
            current_markdown.push_str(line);
        } else {
            current_markdown.push('\n');
            current_markdown.push_str(line);
        }
    }

    if let Some(fence) = current_fence.take() {
        let mut markdown = String::new();
        markdown.push_str("```");
        if let Some(language) = fence.language
            && !language.is_empty()
        {
            markdown.push_str(&language);
        }
        markdown.push('\n');
        markdown.push_str(&fence.code);
        flush_string_block(&mut blocks, markdown);
    }

    flush_markdown_block(&mut blocks, &mut current_markdown, &mut current_kind);
    blocks
}

#[derive(Debug, Clone)]
struct CodeFenceState {
    fence_char: char,
    fence_len: usize,
    language: Option<String>,
    code: String,
}

fn split_lines_preserving_terminator(input: &str) -> Vec<&str> {
    if input.is_empty() {
        return Vec::new();
    }

    let mut lines = Vec::new();
    let mut start = 0usize;

    for (index, character) in input.char_indices() {
        if character == '\n' {
            lines.push(&input[start..=index]);
            start = index + 1;
        }
    }

    if start < input.len() {
        lines.push(&input[start..]);
    }

    lines
}

fn parse_opening_code_fence(trimmed: &str) -> Option<CodeFenceState> {
    let first = trimmed.chars().next()?;
    if first != '`' && first != '~' {
        return None;
    }

    let fence_len = trimmed
        .chars()
        .take_while(|character| *character == first)
        .count();
    if fence_len < 3 {
        return None;
    }

    let language = trimmed[first.len_utf8() * fence_len..].trim();
    Some(CodeFenceState {
        fence_char: first,
        fence_len,
        language: if language.is_empty() {
            None
        } else {
            Some(language.to_string())
        },
        code: String::new(),
    })
}

fn is_closing_code_fence(trimmed: &str, fence_char: char, fence_len: usize) -> bool {
    let close_len = trimmed
        .chars()
        .take_while(|character| *character == fence_char)
        .count();
    close_len >= fence_len
        && trimmed[fence_char.len_utf8() * close_len..]
            .trim()
            .is_empty()
}

fn is_thematic_break(trimmed: &str) -> bool {
    let compact: String = trimmed
        .chars()
        .filter(|character| !character.is_whitespace())
        .collect();
    compact.len() >= 3
        && compact
            .chars()
            .all(|character| character == '-' || character == '*' || character == '_')
        && compact
            .chars()
            .all(|character| character == compact.chars().next().unwrap())
}

fn classify_markdown_line(trimmed: &str) -> MarkdownLineKind {
    if is_heading_line(trimmed) {
        MarkdownLineKind::Heading
    } else if trimmed.starts_with('>') {
        MarkdownLineKind::BlockQuote
    } else if is_list_item_line(trimmed) {
        MarkdownLineKind::ListItem
    } else {
        MarkdownLineKind::Paragraph
    }
}

fn is_heading_line(trimmed: &str) -> bool {
    let hashes = trimmed
        .chars()
        .take_while(|character| *character == '#')
        .count();
    hashes > 0 && hashes <= 6 && trimmed[hashes..].starts_with(' ')
}

fn is_list_item_line(trimmed: &str) -> bool {
    if trimmed.starts_with("- ") || trimmed.starts_with("* ") || trimmed.starts_with("+ ") {
        return true;
    }

    let digits = trimmed
        .chars()
        .take_while(|character| character.is_ascii_digit())
        .count();
    digits > 0 && trimmed[digits..].starts_with(". ")
}

fn should_continue_list_item(trimmed: &str, next_kind: MarkdownLineKind) -> bool {
    if trimmed.starts_with("  ") || trimmed.starts_with('\t') {
        return true;
    }
    next_kind == MarkdownLineKind::Paragraph
}

fn flush_markdown_block(
    blocks: &mut Vec<MarkdownBlock>,
    current_markdown: &mut String,
    current_kind: &mut Option<MarkdownLineKind>,
) {
    let next = std::mem::take(current_markdown);
    *current_kind = None;
    flush_string_block(blocks, next);
}

fn flush_string_block(blocks: &mut Vec<MarkdownBlock>, markdown: String) {
    let trimmed = markdown.trim();
    if !trimmed.is_empty() {
        blocks.push(MarkdownBlock::Markdown(trimmed.to_string()));
    }
}

#[cfg(test)]
mod tests {
    use super::{MarkdownBlock, render_markdown_blocks};

    #[test]
    fn splits_markdown_and_code_blocks() {
        let rendered = render_markdown_blocks(
            "# Heading\n\nParagraph with **bold**.\n\n- first item\ncontinued\n\n```swift\nlet x = 1\n```\n\n> quote",
        );

        assert_eq!(
            rendered,
            vec![
                MarkdownBlock::Markdown("# Heading".to_string()),
                MarkdownBlock::Markdown("Paragraph with **bold**.".to_string()),
                MarkdownBlock::Markdown("- first item\ncontinued".to_string()),
                MarkdownBlock::CodeBlock {
                    language: Some("swift".to_string()),
                    code: "let x = 1\n".to_string(),
                },
                MarkdownBlock::Markdown("> quote".to_string()),
            ]
        );
    }

    #[test]
    fn preserves_thematic_breaks() {
        let rendered = render_markdown_blocks("Before\n\n---\n\nAfter");

        assert_eq!(
            rendered,
            vec![
                MarkdownBlock::Markdown("Before".to_string()),
                MarkdownBlock::ThematicBreak,
                MarkdownBlock::Markdown("After".to_string()),
            ]
        );
    }
}

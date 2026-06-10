use crate::hydration::AppMessageRenderBlock;
use crate::hydration::AppMessageSegment;
use crate::parser::{AppCodeReviewPayload, AppToolCallCard};

#[derive(uniffi::Object)]
pub struct MessageParser;

#[uniffi::export]
impl MessageParser {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self
    }

    pub fn parse_tool_calls_typed(&self, text: String) -> Vec<AppToolCallCard> {
        crate::parser::parse_tool_call_message(&text)
            .iter()
            .map(AppToolCallCard::from)
            .collect()
    }

    pub fn parse_code_review_typed(&self, text: String) -> Option<AppCodeReviewPayload> {
        crate::parser::parse_code_review_message(&text)
            .as_ref()
            .map(AppCodeReviewPayload::from)
    }

    pub fn extract_segments_typed(&self, text: String) -> Vec<AppMessageSegment> {
        crate::hydration::extract_message_segments(&text)
            .into_iter()
            .map(AppMessageSegment::from)
            .collect()
    }

    pub fn extract_render_blocks_typed(&self, text: String) -> Vec<AppMessageRenderBlock> {
        crate::hydration::extract_message_render_blocks(&text)
            .into_iter()
            .map(AppMessageRenderBlock::from)
            .collect()
    }
}

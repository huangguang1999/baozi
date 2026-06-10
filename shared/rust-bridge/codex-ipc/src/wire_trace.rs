use std::sync::Arc;
use std::sync::OnceLock;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RawFrameDirection {
    In,
    Out,
}

type RawFrameTraceObserver = dyn Fn(RawFrameDirection, &str) + Send + Sync + 'static;

static RAW_FRAME_TRACE_OBSERVER: OnceLock<Arc<RawFrameTraceObserver>> = OnceLock::new();

pub fn install_raw_frame_trace_observer(observer: Arc<RawFrameTraceObserver>) {
    let _ = RAW_FRAME_TRACE_OBSERVER.set(observer);
}

pub(crate) fn emit_raw_frame_trace(direction: RawFrameDirection, payload: &str) {
    if let Some(observer) = RAW_FRAME_TRACE_OBSERVER.get() {
        observer(direction, payload);
    }
}

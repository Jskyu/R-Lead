import type { QueueStatus as QueueStatusType } from '../types/lead'

/**
 * AI 처리 대기열 요약 (표시 전용). 데이터는 리드 목록 화면이 주기적으로 가져온다.
 */
export function QueueStatus({ status }: { status: QueueStatusType | null }) {
  if (!status) return null
  const active = status.pending_count + status.processing_count
  if (active === 0 && status.failed_count === 0) return null

  return (
    <div
      className="d-flex flex-wrap align-items-center gap-2 mb-3 small"
      aria-label="AI 처리 대기열"
    >
      <span className="text-secondary">
        <i className="bi bi-cpu me-1"></i>AI 처리 현황
      </span>
      {status.processing_count > 0 && (
        <span className="badge text-bg-info">
          <span
            className="spinner-border spinner-border-sm me-1"
            style={{ width: '.7em', height: '.7em' }}
          ></span>
          분석 중 {status.processing_count}
        </span>
      )}
      {status.pending_count > 0 && (
        <span className="badge text-bg-secondary">대기 {status.pending_count}</span>
      )}
      {status.failed_count > 0 && (
        <span
          className="badge text-bg-danger"
          title="자동 재시도 후에도 실패한 작업. 목록에서 선택해 '재추출'하세요."
        >
          실패 {status.failed_count}
        </span>
      )}
    </div>
  )
}

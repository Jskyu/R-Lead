import { LeadStatus, STATUS_META, normalizeStatus } from '../types/lead'

/** 큐 기준 표시 상태 (status 가 uploaded 일 때만 의미가 있음) */
export type DisplayState = 'queued' | 'processing' | 'stalled' | null

/**
 * 리드 상태 배지. 업로드됨 상태에서는 큐 상태(대기/처리중/실패)를 함께 보여준다.
 */
export function StatusBadge({
  status,
  state,
  hint,
}: {
  status: string
  state?: DisplayState
  hint?: string
}) {
  const s = normalizeStatus(status)
  if (s === LeadStatus.UPLOADED && state === 'processing') {
    return (
      <span className="badge text-bg-info">
        <span
          className="spinner-border spinner-border-sm me-1"
          style={{ width: '.7em', height: '.7em' }}
        ></span>
        AI 분석 중
      </span>
    )
  }
  if (s === LeadStatus.UPLOADED && state === 'queued') {
    return (
      <span className="badge text-bg-secondary">
        <i className="bi bi-hourglass me-1"></i>분석 대기
      </span>
    )
  }
  if (s === LeadStatus.UPLOADED && state === 'stalled') {
    return (
      <span className="badge text-bg-danger" title={hint}>
        <i className="bi bi-exclamation-triangle me-1"></i>추출 실패
      </span>
    )
  }
  const meta = STATUS_META[s]
  return (
    <span className={`badge text-bg-${meta.color}`}>
      <i className={`bi bi-${meta.icon} me-1`}></i>
      {meta.label}
    </span>
  )
}

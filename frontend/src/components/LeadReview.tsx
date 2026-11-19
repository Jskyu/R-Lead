import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { LeadStatus, normalizeStatus } from '../types/lead'
import type { Lead, LeadUpdate } from '../types/lead'
import {
  APIError,
  approveLead,
  bulkReextract,
  getLead,
  getLeads,
  getQueueStatus,
  updateLead,
} from '../services/api'
import { useFeedback } from '../ui/feedback-context'
import { StatusBadge } from '../ui/StatusBadge'

interface LeadReviewProps {
  leadId: number
  onBack: () => void
  /** 승인/거부 후 이동할 다음 리드 (없으면 null → 목록) */
  onMove: (nextLeadId: number | null) => void
}

type FieldKey =
  'name' | 'company' | 'department' | 'job_title' | 'email' | 'phone' | 'address' | 'website'

const FIELDS: {
  key: FieldKey
  label: string
  icon: string
  inputMode?: 'email' | 'tel' | 'url'
}[] = [
  { key: 'name', label: '이름', icon: 'person' },
  { key: 'company', label: '회사', icon: 'building' },
  { key: 'department', label: '부서', icon: 'diagram-3' },
  { key: 'job_title', label: '직함', icon: 'award' },
  { key: 'email', label: '이메일', icon: 'envelope', inputMode: 'email' },
  { key: 'phone', label: '전화번호', icon: 'telephone', inputMode: 'tel' },
  { key: 'address', label: '주소', icon: 'geo-alt' },
  { key: 'website', label: '웹사이트', icon: 'globe', inputMode: 'url' },
]

const ISSUE_LABEL: Record<string, string> = {
  empty: '빈 칸',
  missing: '빈 칸',
  invalid_format: '형식 오류',
  invalid: '형식 오류',
  missing_in_original: '원문에 없음',
  suspicious: '의심',
}

/** 신뢰도 점수 → 색상/문구 (규칙 기반 점수: 90/70/30/0) */
function confidenceInfo(value: number): { color: string; label: string } {
  if (value >= 80) return { color: 'success', label: '높음' }
  if (value >= 60) return { color: 'info', label: '보통' }
  if (value > 0) return { color: 'warning', label: '낮음' }
  return { color: 'danger', label: '없음/의심' }
}

type Draft = Record<FieldKey, string>

const toDraft = (lead: Lead): Draft => {
  const d = {} as Draft
  FIELDS.forEach(f => (d[f.key] = lead[f.key] ?? ''))
  return d
}

export function LeadReview({ leadId, onBack, onMove }: LeadReviewProps) {
  const { toast, confirm } = useFeedback()
  const [lead, setLead] = useState<Lead | null>(null)
  const [pendingIds, setPendingIds] = useState<number[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [zoom, setZoom] = useState(false)
  const [failReason, setFailReason] = useState('')

  const load = useCallback(async () => {
    setIsLoading(true)
    setError('')
    setIsEditing(false)
    try {
      const [data, pending, queue] = await Promise.all([
        getLead(leadId),
        getLeads(LeadStatus.EXTRACTED).catch(() => [] as Lead[]),
        getQueueStatus().catch(() => null),
      ])
      setFailReason(queue?.failed_leads?.find(f => f.id === leadId)?.error ?? '')
      const normalized = { ...data, status: normalizeStatus(data.status) }
      setLead(normalized)
      setDraft(toDraft(normalized))
      // 확인 대기 목록은 오래된 순(수집 순서)으로 이동
      setPendingIds(pending.map(p => p.id).sort((a, b) => a - b))
    } catch (e) {
      setLead(null)
      setError(
        e instanceof APIError && e.status === 404
          ? '리드를 찾을 수 없습니다. 삭제되었을 수 있습니다.'
          : e instanceof Error
            ? e.message
            : '불러오지 못했습니다.'
      )
    } finally {
      setIsLoading(false)
    }
  }, [leadId])

  useEffect(() => {
    void load()
  }, [load])

  // ESC 로 확대 닫기
  useEffect(() => {
    if (!zoom) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setZoom(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [zoom])

  const issuesByField = useMemo(() => {
    const map: Record<string, string[]> = {}
    lead?.validation_issues?.forEach(i => {
      const list = map[i.field] ?? []
      list.push(`${ISSUE_LABEL[i.issue_type] ?? i.issue_type}: ${i.message}`)
      map[i.field] = list
    })
    return map
  }, [lead])

  const nextId = useMemo(() => pendingIds.find(id => id !== leadId) ?? null, [pendingIds, leadId])
  const pendingPosition = pendingIds.indexOf(leadId)

  const changedUpdates = (): LeadUpdate => {
    if (!lead || !draft) return {}
    const updates: LeadUpdate = {}
    FIELDS.forEach(f => {
      if (draft[f.key].trim() !== (lead[f.key] ?? '')) updates[f.key] = draft[f.key].trim()
    })
    return updates
  }

  const handleSave = async () => {
    const updates = changedUpdates()
    if (Object.keys(updates).length === 0) {
      setIsEditing(false)
      return
    }
    setIsSaving(true)
    try {
      const updated = await updateLead(leadId, updates)
      const normalized = { ...updated, status: normalizeStatus(updated.status) }
      setLead(normalized)
      setDraft(toDraft(normalized))
      setIsEditing(false)
      toast('수정 내용을 저장했습니다.', 'success')
    } catch (e) {
      toast(e instanceof Error ? `저장 실패: ${e.message}` : '저장에 실패했습니다.', 'danger')
    } finally {
      setIsSaving(false)
    }
  }

  const handleDecision = async (approved: boolean) => {
    if (!lead || !draft) return
    const current = isEditing ? draft : toDraft(lead)
    const missing = [!current.name.trim() && '이름', !current.company.trim() && '회사'].filter(
      Boolean
    )

    let message = approved
      ? '이 리드를 승인합니다. 승인된 값만 CRM 등록 대상이 됩니다.'
      : '이 리드를 거부합니다.'
    if (approved && missing.length > 0)
      message += `\n\n⚠ ${missing.join(', ')} 항목이 비어 있습니다.`
    if (approved && issuesByField && Object.keys(issuesByField).length > 0)
      message += '\n\n⚠ 검증 문제가 있는 항목이 있습니다. 내용을 확인하셨나요?'

    const ok = await confirm({
      title: approved ? '승인' : '거부',
      message,
      confirmLabel: approved ? '승인' : '거부',
      variant: approved ? 'success' : 'danger',
    })
    if (!ok) return

    setIsSaving(true)
    try {
      await approveLead(leadId, approved, isEditing ? changedUpdates() : undefined)
      toast(approved ? '승인했습니다.' : '거부했습니다.', approved ? 'success' : 'info')
      onMove(nextId)
    } catch (e) {
      toast(e instanceof Error ? `처리 실패: ${e.message}` : '처리에 실패했습니다.', 'danger')
    } finally {
      setIsSaving(false)
    }
  }

  const handleReextract = async () => {
    const ok = await confirm({
      title: '재추출',
      message: 'AI로 이 명함을 다시 읽습니다. 기존 추출 결과는 초기화됩니다.',
      confirmLabel: '재추출',
    })
    if (!ok) return
    try {
      await bulkReextract([leadId])
      toast('재추출을 등록했습니다. 잠시 후 목록에서 확인하세요.', 'success')
      onBack()
    } catch (e) {
      toast(e instanceof Error ? e.message : '재추출에 실패했습니다.', 'danger')
    }
  }

  if (isLoading) {
    return (
      <div className="text-center py-5 text-secondary">
        <div className="spinner-border spinner-border-sm me-2"></div>불러오는 중...
      </div>
    )
  }

  if (error || !lead || !draft) {
    return (
      <div>
        <button type="button" className="btn btn-link px-0" onClick={onBack}>
          <i className="bi bi-arrow-left me-1"></i>목록으로
        </button>
        <div className="alert alert-danger mt-2">
          <i className="bi bi-exclamation-triangle me-2"></i>
          {error || '리드를 찾을 수 없습니다.'}
        </div>
      </div>
    )
  }

  const decided = lead.status !== LeadStatus.EXTRACTED && lead.status !== LeadStatus.REVIEWED
  const canDecide = lead.status !== LeadStatus.UPLOADED
  const modified = new Set(lead.modified_fields ?? [])

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3 gap-2">
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onBack}>
          <i className="bi bi-arrow-left me-1"></i>목록
        </button>
        <div className="d-flex align-items-center gap-2 small text-secondary">
          {pendingPosition >= 0 && (
            <span>
              확인 대기 {pendingPosition + 1} / {pendingIds.length}
            </span>
          )}
          <StatusBadge status={lead.status} />
        </div>
      </div>

      {lead.card_index != null && (
        <div className="alert alert-light border d-flex align-items-center" role="note">
          <i className="bi bi-collection me-2"></i>
          <span>
            한 사진에서 여러 장의 명함이 인식되었습니다. 이 리드는{' '}
            <strong>{lead.card_index + 1}번째</strong> 명함입니다. (사진은 같은 원본을 함께
            보여줍니다)
          </span>
        </div>
      )}

      {lead.duplicate_of_lead_id && (
        <div className="alert alert-warning d-flex align-items-center" role="alert">
          <i className="bi bi-files me-2"></i>
          <span>
            기존 리드{' '}
            <Link to={`/leads/${lead.duplicate_of_lead_id}`} className="alert-link">
              #{lead.duplicate_of_lead_id}
            </Link>
            와 중복일 수 있습니다. (이메일·회사 기준)
          </span>
        </div>
      )}

      {lead.status === LeadStatus.UPLOADED && (
        <div className="alert alert-info d-flex flex-wrap justify-content-between align-items-center gap-2">
          <span>
            <i className="bi bi-cpu me-2"></i>
            {failReason
              ? `AI 추출에 실패했습니다: ${failReason}`
              : '아직 AI 추출 결과가 없습니다. 분석 중이거나 추출에 실패했을 수 있습니다.'}
          </span>
          <span className="d-flex gap-2">
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => void load()}
            >
              새로고침
            </button>
            <button
              type="button"
              className="btn btn-sm btn-info"
              onClick={() => void handleReextract()}
            >
              <i className="bi bi-arrow-repeat me-1"></i>재추출
            </button>
          </span>
        </div>
      )}

      {lead.status === LeadStatus.CRM_FAILED && lead.crm_error_message && (
        <div className="alert alert-danger">CRM 등록 실패: {lead.crm_error_message}</div>
      )}

      <div className="row g-3">
        {/* 명함 원본 */}
        <div className="col-lg-5">
          <div className="card border-0 shadow-sm sticky-lg-top">
            <div className="card-body">
              <h2 className="h6 mb-2">
                <i className="bi bi-image me-1"></i>명함 원본
                <span className="text-secondary small ms-2">(클릭하면 확대)</span>
              </h2>
              {lead.card_image_url ? (
                <img
                  src={lead.card_image_url}
                  alt="명함 원본"
                  className="card-image"
                  onClick={() => setZoom(true)}
                />
              ) : (
                <div className="text-center text-secondary py-5 bg-body-secondary rounded">
                  이미지 없음 (보관 기간 만료 가능)
                </div>
              )}
              {lead.image_delete_at && (
                <div className="form-text mt-2">
                  <i className="bi bi-shield-lock me-1"></i>
                  사진은 {new Date(lead.image_delete_at).toLocaleDateString('ko-KR')}에 자동
                  삭제됩니다.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 추출 정보 */}
        <div className="col-lg-7">
          {canDecide && (
            <div className="card border-0 shadow-sm mb-3">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <h2 className="h6 mb-0">
                    <i className="bi bi-card-checklist me-1"></i>추출된 정보
                  </h2>
                  {canDecide &&
                    (isEditing ? (
                      <span className="d-flex gap-2">
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary"
                          onClick={() => {
                            setIsEditing(false)
                            setDraft(toDraft(lead))
                          }}
                          disabled={isSaving}
                        >
                          취소
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-primary"
                          onClick={() => void handleSave()}
                          disabled={isSaving}
                        >
                          <i className="bi bi-check2 me-1"></i>저장
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-primary"
                        onClick={() => setIsEditing(true)}
                      >
                        <i className="bi bi-pencil me-1"></i>수정
                      </button>
                    ))}
                </div>

                {/* 신뢰도 범례 */}
                <div className="small text-secondary mb-3 d-flex flex-wrap gap-2 align-items-center">
                  <span>데이터 정확도:</span>
                  {[90, 70, 30, 0].map(v => {
                    const info = confidenceInfo(v)
                    return (
                      <span key={v} className={`badge text-bg-${info.color}`}>
                        {v} {info.label}
                      </span>
                    )
                  })}
                  <span className="ms-1">(형식·빈 칸·원문 대조 규칙으로 계산)</span>
                </div>

                <div className="vstack gap-3">
                  {FIELDS.map(f => {
                    const issues = issuesByField[f.key]
                    const confidence = lead.field_confidences?.[`${f.key}_confidence`]
                    const info = confidence !== undefined ? confidenceInfo(confidence) : null
                    const isModified = modified.has(f.key)
                    return (
                      <div
                        key={f.key}
                        className={issues ? 'field-issue' : isModified ? 'field-modified' : ''}
                      >
                        <div className="d-flex justify-content-between align-items-center">
                          <label htmlFor={`f-${f.key}`} className="field-label">
                            <i className={`bi bi-${f.icon} me-1`}></i>
                            {f.label}
                            {isModified && <span className="badge text-bg-info ms-2">수정됨</span>}
                          </label>
                          {info && confidence !== undefined && (
                            <span
                              className={`badge text-bg-${info.color}`}
                              title={`정확도 ${confidence} (${info.label})`}
                            >
                              {confidence}
                            </span>
                          )}
                        </div>
                        {isEditing ? (
                          <input
                            id={`f-${f.key}`}
                            className={`form-control ${issues ? 'is-invalid' : ''}`}
                            inputMode={f.inputMode}
                            value={draft[f.key]}
                            onChange={e => setDraft({ ...draft, [f.key]: e.target.value })}
                            disabled={isSaving}
                          />
                        ) : (
                          <div className="field-value">
                            {lead[f.key] || <span className="text-secondary">-</span>}
                          </div>
                        )}
                        {issues?.map((m, i) => (
                          <div key={i} className="small text-warning-emphasis mt-1">
                            <i className="bi bi-exclamation-triangle me-1"></i>
                            {m}
                          </div>
                        ))}
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          )}

          <div className="card border-0 shadow-sm mb-3">
            <div className="card-body small">
              <h2 className="h6 mb-3">
                <i className="bi bi-info-circle me-1"></i>수집 정보
              </h2>
              <dl className="row mb-0 gy-1">
                <dt className="col-4 col-sm-3 text-secondary fw-normal">행사 / 부스</dt>
                <dd className="col-8 col-sm-9 mb-0">
                  {[lead.event_name, lead.booth_number].filter(Boolean).join(' · ') || '-'}
                </dd>
                <dt className="col-4 col-sm-3 text-secondary fw-normal">수집 담당자</dt>
                <dd className="col-8 col-sm-9 mb-0">{lead.collected_by || '-'}</dd>
                <dt className="col-4 col-sm-3 text-secondary fw-normal">수집 일시</dt>
                <dd className="col-8 col-sm-9 mb-0">
                  {lead.collected_at ? new Date(lead.collected_at).toLocaleString('ko-KR') : '-'}
                </dd>
                <dt className="col-4 col-sm-3 text-secondary fw-normal">관심 제품</dt>
                <dd className="col-8 col-sm-9 mb-0">
                  {lead.interested_products?.length
                    ? lead.interested_products.map(p => (
                        <span key={p} className="badge text-bg-light border me-1">
                          {p}
                        </span>
                      ))
                    : '-'}
                </dd>
                <dt className="col-4 col-sm-3 text-secondary fw-normal">마케팅 수신</dt>
                <dd className="col-8 col-sm-9 mb-0">
                  {lead.marketing_consent ? (
                    <span className="text-success">
                      <i className="bi bi-check-circle me-1"></i>동의
                      {lead.marketing_consent_at &&
                        ` (${new Date(lead.marketing_consent_at).toLocaleDateString('ko-KR')})`}
                    </span>
                  ) : (
                    <span className="text-secondary">동의 안 함</span>
                  )}
                </dd>
              </dl>
            </div>
          </div>

          {/* 승인/거부 */}
          {canDecide && (
            <div className="action-bar p-2 d-flex gap-2 align-items-center">
              <button
                type="button"
                className="btn btn-outline-danger text-nowrap"
                onClick={() => void handleDecision(false)}
                disabled={isSaving || lead.status === LeadStatus.REJECTED}
              >
                <i className="bi bi-x-lg me-1"></i>거부
              </button>
              <button
                type="button"
                className="btn btn-success flex-grow-1 text-nowrap"
                onClick={() => void handleDecision(true)}
                disabled={
                  isSaving ||
                  lead.status === LeadStatus.APPROVED ||
                  lead.status === LeadStatus.CRM_REGISTERED
                }
              >
                {isSaving ? (
                  <span className="spinner-border spinner-border-sm me-2"></span>
                ) : (
                  <i className="bi bi-check-lg me-1"></i>
                )}
                {decided && lead.status === LeadStatus.APPROVED
                  ? '승인됨'
                  : nextId
                    ? '승인하고 다음'
                    : '승인'}
              </button>
              {nextId && (
                <button
                  type="button"
                  className="btn btn-outline-secondary text-nowrap"
                  onClick={() => onMove(nextId)}
                  title="다음 확인 대기 명함으로 (승인·거부 없이)"
                  aria-label="건너뛰기"
                >
                  <span className="d-none d-sm-inline">건너뛰기 </span>
                  <i className="bi bi-skip-forward"></i>
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {zoom && lead.card_image_url && (
        <div
          className="zoom-overlay"
          onClick={() => setZoom(false)}
          role="dialog"
          aria-label="명함 확대"
        >
          <img src={lead.card_image_url} alt="명함 확대" />
        </div>
      )}
    </div>
  )
}

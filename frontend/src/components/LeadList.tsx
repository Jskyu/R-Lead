import { useCallback, useEffect, useMemo, useState } from 'react'
import { LeadStatus, normalizeStatus } from '../types/lead'
import type { Lead, QueueStatus as QueueStatusType } from '../types/lead'
import {
  APIError,
  bulkDelete,
  bulkReextract,
  deleteLead,
  getLeads,
  getQueueStatus,
} from '../services/api'
import { useFeedback } from '../ui/feedback-context'
import { StatusBadge } from '../ui/StatusBadge'
import { averageConfidence, confidenceColor } from '../ui/confidence'
import type { DisplayState } from '../ui/StatusBadge'
import { QueueStatus } from './QueueStatus'

interface LeadListProps {
  onSelectLead: (leadId: number) => void
}

type Filter = 'all' | LeadStatus

const FILTERS: { key: Filter; label: string; icon: string }[] = [
  { key: 'all', label: '전체', icon: 'list-ul' },
  { key: LeadStatus.EXTRACTED, label: '확인 대기', icon: 'hourglass-split' },
  { key: LeadStatus.APPROVED, label: '승인', icon: 'check-circle' },
  { key: LeadStatus.REJECTED, label: '거부', icon: 'x-circle' },
  { key: LeadStatus.CRM_REGISTERED, label: 'CRM', icon: 'send-check' },
  { key: LeadStatus.UPLOADED, label: '처리 중/실패', icon: 'cloud-upload' },
]

const PAGE_SIZE = 20

export function LeadList({ onSelectLead }: LeadListProps) {
  const { toast, confirm } = useFeedback()
  const [leads, setLeads] = useState<Lead[]>([])
  const [queue, setQueue] = useState<QueueStatusType | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [isBusy, setIsBusy] = useState(false)

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true)
    try {
      const [data, q] = await Promise.all([getLeads('all'), getQueueStatus().catch(() => null)])
      setLeads(data.map(l => ({ ...l, status: normalizeStatus(l.status) })))
      setQueue(q)
      setLoadError('')
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '리드를 불러오지 못했습니다.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  // 분석이 진행 중이면 자주(5초), 아니면 느리게(20초) 갱신한다.
  const hasActive = (queue?.pending_count ?? 0) + (queue?.processing_count ?? 0) > 0
  useEffect(() => {
    const timer = window.setInterval(() => void load(true), hasActive ? 5000 : 20000)
    return () => window.clearInterval(timer)
  }, [load, hasActive])

  const stateOf = useCallback(
    (lead: Lead): DisplayState => {
      if (lead.status !== LeadStatus.UPLOADED) return null
      if (queue?.processing_leads.some(l => l.id === lead.id)) return 'processing'
      if (queue?.pending_leads.some(l => l.id === lead.id)) return 'queued'
      return queue ? 'stalled' : null
    },
    [queue]
  )

  const failureReason = useCallback(
    (lead: Lead): string | undefined => queue?.failed_leads?.find(f => f.id === lead.id)?.error,
    [queue]
  )

  const counts = useMemo(() => {
    const c: Record<string, number> = { all: leads.length }
    leads.forEach(l => (c[l.status] = (c[l.status] ?? 0) + 1))
    return c
  }, [leads])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return leads.filter(l => {
      if (filter !== 'all' && l.status !== filter) return false
      if (!q) return true
      return [l.name, l.company, l.email, l.phone, l.event_name, String(l.id)].some(v =>
        v?.toLowerCase().includes(q)
      )
    })
  }, [leads, filter, search])

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const visible = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  useEffect(() => setPage(1), [filter, search])

  const toggleOne = (id: number) =>
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const allVisibleSelected = visible.length > 0 && visible.every(l => selected.has(l.id))
  const toggleAllVisible = () =>
    setSelected(prev => {
      const next = new Set(prev)
      if (allVisibleSelected) visible.forEach(l => next.delete(l.id))
      else visible.forEach(l => next.add(l.id))
      return next
    })

  const run = async (action: () => Promise<string>, failMessage: string) => {
    setIsBusy(true)
    try {
      toast(await action(), 'success')
      setSelected(new Set())
      await load(true)
    } catch (error) {
      toast(error instanceof APIError ? `${failMessage} (${error.message})` : failMessage, 'danger')
    } finally {
      setIsBusy(false)
    }
  }

  const handleReextract = async () => {
    const ids = Array.from(selected)
    const ok = await confirm({
      title: '재추출',
      message: `선택한 ${ids.length}건을 AI로 다시 추출합니다.\n기존 추출 결과는 초기화됩니다.`,
      confirmLabel: '재추출',
    })
    if (ok)
      await run(
        async () => `재추출 ${(await bulkReextract(ids)).job_count}건을 등록했습니다.`,
        '재추출에 실패했습니다.'
      )
  }

  const handleBulkDelete = async () => {
    const ids = Array.from(selected)
    const ok = await confirm({
      title: '일괄 삭제',
      message: `선택한 ${ids.length}건을 삭제합니다.\n명함 사진과 처리 작업도 함께 삭제되며 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      variant: 'danger',
    })
    if (ok)
      await run(
        async () => `${(await bulkDelete(ids)).deleted_count}건을 삭제했습니다.`,
        '삭제에 실패했습니다.'
      )
  }

  const handleDeleteOne = async (lead: Lead) => {
    const ok = await confirm({
      title: '리드 삭제',
      message: `${lead.name ? `"${lead.name}" ` : `#${lead.id} `}리드를 삭제합니다.\n명함 사진과 처리 작업도 함께 삭제되며 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      variant: 'danger',
    })
    if (ok)
      await run(async () => {
        await deleteLead(lead.id)
        return '삭제했습니다.'
      }, '삭제에 실패했습니다.')
  }

  const confidenceBadge = (lead: Lead) => {
    const avg = averageConfidence(lead.field_confidences)
    if (avg === null) return <span className="text-secondary">-</span>
    return <span className={`badge rounded-pill text-bg-${confidenceColor(avg)}`}>{avg}%</span>
  }

  const dateText = (lead: Lead) =>
    lead.collected_at
      ? new Date(lead.collected_at).toLocaleString('ko-KR', {
          month: 'numeric',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        })
      : '-'

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h4 mb-0">
          <i className="bi bi-people me-2 text-primary"></i>리드 관리
        </h1>
        <button
          type="button"
          className="btn btn-outline-secondary btn-sm"
          onClick={() => void load()}
          disabled={isLoading}
        >
          <i className="bi bi-arrow-clockwise me-1"></i>새로고침
        </button>
      </div>

      <QueueStatus status={queue} />

      {/* 필터 */}
      <ul
        className="nav nav-pills flex-nowrap overflow-auto pb-2 mb-2"
        style={{ scrollbarWidth: 'thin' }}
      >
        {FILTERS.map(f => (
          <li className="nav-item" key={f.key}>
            <button
              type="button"
              className={`nav-link text-nowrap ${filter === f.key ? 'active' : ''}`}
              onClick={() => setFilter(f.key)}
            >
              <i className={`bi bi-${f.icon} me-1`}></i>
              {f.label}
              <span
                className={`badge ms-1 ${filter === f.key ? 'text-bg-light' : 'text-bg-secondary'}`}
              >
                {counts[f.key] ?? 0}
              </span>
            </button>
          </li>
        ))}
      </ul>

      <div className="input-group mb-3">
        <span className="input-group-text">
          <i className="bi bi-search"></i>
        </span>
        <input
          type="search"
          className="form-control"
          placeholder="이름, 회사, 이메일, 전화번호, 행사명 검색"
          value={search}
          onChange={e => setSearch(e.target.value)}
          aria-label="리드 검색"
        />
      </div>

      {/* 일괄 작업 */}
      {selected.size > 0 && (
        <div
          className="alert alert-primary d-flex flex-wrap align-items-center gap-2 py-2"
          role="region"
          aria-label="일괄 작업"
        >
          <strong>{selected.size}건 선택</strong>
          <span className="ms-auto d-flex gap-2">
            <button
              type="button"
              className="btn btn-sm btn-outline-primary"
              onClick={handleReextract}
              disabled={isBusy}
            >
              <i className="bi bi-arrow-repeat me-1"></i>재추출
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-danger"
              onClick={handleBulkDelete}
              disabled={isBusy}
            >
              <i className="bi bi-trash me-1"></i>삭제
            </button>
            <button
              type="button"
              className="btn btn-sm btn-link text-secondary"
              onClick={() => setSelected(new Set())}
            >
              선택 해제
            </button>
          </span>
        </div>
      )}

      {isLoading ? (
        <div className="text-center py-5 text-secondary">
          <div className="spinner-border spinner-border-sm me-2"></div>불러오는 중...
        </div>
      ) : loadError ? (
        <div className="alert alert-danger d-flex justify-content-between align-items-center">
          <span>
            <i className="bi bi-exclamation-triangle me-2"></i>
            {loadError}
          </span>
          <button
            type="button"
            className="btn btn-sm btn-outline-danger"
            onClick={() => void load()}
          >
            다시 시도
          </button>
        </div>
      ) : filtered.length === 0 ? (
        <div className="card border-0 shadow-sm">
          <div className="card-body text-center py-5 text-secondary">
            <i className="bi bi-inbox display-5 d-block mb-2"></i>
            {leads.length === 0
              ? '아직 수집한 명함이 없습니다. 먼저 명함을 촬영해 보세요.'
              : '조건에 맞는 리드가 없습니다.'}
          </div>
        </div>
      ) : (
        <>
          {/* 데스크톱: 테이블 */}
          <div className="card border-0 shadow-sm d-none d-md-block">
            <div className="table-responsive">
              <table className="table table-hover mb-0">
                <thead className="table-light">
                  <tr>
                    <th style={{ width: 40 }}>
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={allVisibleSelected}
                        onChange={toggleAllVisible}
                        aria-label="현재 페이지 전체 선택"
                      />
                    </th>
                    <th>이름 / 회사</th>
                    <th>연락처</th>
                    <th>행사</th>
                    <th>신뢰도</th>
                    <th>상태</th>
                    <th>수집</th>
                    <th style={{ width: 90 }}></th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map(lead => (
                    <tr
                      key={lead.id}
                      className="row-clickable"
                      onClick={() => onSelectLead(lead.id)}
                    >
                      <td onClick={e => e.stopPropagation()}>
                        <input
                          className="form-check-input"
                          type="checkbox"
                          checked={selected.has(lead.id)}
                          onChange={() => toggleOne(lead.id)}
                          aria-label={`리드 ${lead.id} 선택`}
                        />
                      </td>
                      <td>
                        <div className="fw-semibold">
                          {lead.name || (
                            <span className="text-secondary">(이름 없음) #{lead.id}</span>
                          )}
                          {lead.card_index != null && (
                            <span
                              className="badge text-bg-light border ms-2"
                              title="한 사진에서 여러 장이 인식된 명함"
                            >
                              <i className="bi bi-collection me-1"></i>
                              {lead.card_index + 1}번째
                            </span>
                          )}
                          {lead.duplicate_of_lead_id && (
                            <span
                              className="badge text-bg-warning ms-2"
                              title={`리드 #${lead.duplicate_of_lead_id}와 중복 가능`}
                            >
                              중복?
                            </span>
                          )}
                        </div>
                        <div className="small text-secondary">{lead.company || '-'}</div>
                        {stateOf(lead) === 'stalled' && failureReason(lead) && (
                          <div
                            className="small text-danger text-truncate"
                            style={{ maxWidth: 260 }}
                            title={failureReason(lead)}
                          >
                            {failureReason(lead)}
                          </div>
                        )}
                      </td>
                      <td className="small">
                        <div>{lead.email || '-'}</div>
                        <div className="text-secondary">{lead.phone || '-'}</div>
                      </td>
                      <td className="small text-secondary">{lead.event_name || '-'}</td>
                      <td>{confidenceBadge(lead)}</td>
                      <td>
                        <StatusBadge
                          status={lead.status}
                          state={stateOf(lead)}
                          hint={failureReason(lead)}
                        />
                      </td>
                      <td className="small text-secondary text-nowrap">{dateText(lead)}</td>
                      <td className="text-end text-nowrap" onClick={e => e.stopPropagation()}>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary border-0"
                          title="삭제"
                          aria-label="삭제"
                          onClick={() => void handleDeleteOne(lead)}
                          disabled={isBusy}
                        >
                          <i className="bi bi-trash"></i>
                        </button>
                        <i className="bi bi-chevron-right text-secondary ms-1"></i>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* 모바일: 카드 */}
          <div className="d-md-none">
            {visible.map(lead => (
              <div className="card border-0 shadow-sm mb-2" key={lead.id}>
                <div className="card-body py-2 d-flex gap-2 align-items-start">
                  <input
                    className="form-check-input mt-2"
                    type="checkbox"
                    checked={selected.has(lead.id)}
                    onChange={() => toggleOne(lead.id)}
                    aria-label={`리드 ${lead.id} 선택`}
                  />
                  <button
                    type="button"
                    className="btn btn-link text-start text-reset text-decoration-none p-0 flex-grow-1 min-w-0"
                    onClick={() => onSelectLead(lead.id)}
                  >
                    <div className="d-flex justify-content-between gap-2">
                      <div className="fw-semibold text-truncate">
                        {lead.name || `(이름 없음) #${lead.id}`}
                        {lead.card_index != null && (
                          <span className="badge text-bg-light border ms-1">
                            {lead.card_index + 1}번째
                          </span>
                        )}
                        {lead.duplicate_of_lead_id && (
                          <span className="badge text-bg-warning ms-1">중복?</span>
                        )}
                      </div>
                      <StatusBadge
                        status={lead.status}
                        state={stateOf(lead)}
                        hint={failureReason(lead)}
                      />
                    </div>
                    <div className="small text-secondary text-truncate">{lead.company || '-'}</div>
                    {stateOf(lead) === 'stalled' && failureReason(lead) && (
                      <div className="small text-danger text-truncate">{failureReason(lead)}</div>
                    )}
                    <div className="small text-secondary d-flex justify-content-between mt-1">
                      <span className="text-truncate">{lead.email || lead.phone || '-'}</span>
                      <span className="d-flex align-items-center gap-2 text-nowrap">
                        {confidenceBadge(lead)}
                        {dateText(lead)}
                      </span>
                    </div>
                  </button>
                </div>
              </div>
            ))}
            <div className="form-check mt-2 ms-1">
              <input
                className="form-check-input"
                type="checkbox"
                id="selall"
                checked={allVisibleSelected}
                onChange={toggleAllVisible}
              />
              <label className="form-check-label small" htmlFor="selall">
                현재 페이지 전체 선택
              </label>
            </div>
          </div>

          {pageCount > 1 && (
            <nav className="mt-3" aria-label="페이지">
              <ul className="pagination pagination-sm justify-content-center mb-0">
                <li className={`page-item ${currentPage === 1 ? 'disabled' : ''}`}>
                  <button
                    type="button"
                    className="page-link"
                    onClick={() => setPage(currentPage - 1)}
                  >
                    이전
                  </button>
                </li>
                <li className="page-item disabled">
                  <span className="page-link">
                    {currentPage} / {pageCount}
                  </span>
                </li>
                <li className={`page-item ${currentPage === pageCount ? 'disabled' : ''}`}>
                  <button
                    type="button"
                    className="page-link"
                    onClick={() => setPage(currentPage + 1)}
                  >
                    다음
                  </button>
                </li>
              </ul>
            </nav>
          )}
        </>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { LeadStatus, STATUS_META } from '../types/lead'
import type { LeadStats } from '../types/lead'
import { downloadCsv, getStats } from '../services/api'
import { useFeedback } from '../ui/feedback-context'

const EXPORT_FILTERS: ('all' | LeadStatus)[] = [
  'all',
  LeadStatus.APPROVED,
  LeadStatus.EXTRACTED,
  LeadStatus.REJECTED,
  LeadStatus.CRM_REGISTERED,
]

export function ExportPanel() {
  const { toast } = useFeedback()
  const [stats, setStats] = useState<LeadStats | null>(null)
  const [filter, setFilter] = useState<'all' | LeadStatus>(LeadStatus.APPROVED)
  const [isExporting, setIsExporting] = useState(false)

  useEffect(() => {
    getStats()
      .then(setStats)
      .catch(() => toast('통계를 불러오지 못했습니다.', 'warning'))
  }, [toast])

  const countFor = (key: 'all' | LeadStatus): number | null => {
    if (!stats) return null
    return key === 'all' ? stats.total_leads : (stats.status_counts[key] ?? 0)
  }

  const handleExport = async () => {
    setIsExporting(true)
    try {
      await downloadCsv(filter)
      toast('CSV 파일을 내려받았습니다.', 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : '내보내기에 실패했습니다.', 'danger')
    } finally {
      setIsExporting(false)
    }
  }

  const selectedCount = countFor(filter)

  return (
    <div>
      <h1 className="h4 mb-3">
        <i className="bi bi-download me-2 text-primary"></i>내보내기
      </h1>

      <div className="card border-0 shadow-sm">
        <div className="card-body">
          <p className="text-secondary small">
            CRM 연동 전까지는 CSV로 내려받아 CRM에 가져오기(import)할 수 있습니다. 일반적으로는{' '}
            <strong>승인된 리드</strong>만 내보내세요.
          </p>

          <label className="form-label fw-semibold">내보낼 리드</label>
          <div className="list-group mb-3">
            {EXPORT_FILTERS.map(key => (
              <label
                key={key}
                className={`list-group-item d-flex align-items-center gap-2 ${filter === key ? 'active' : ''}`}
                style={{ cursor: 'pointer' }}
              >
                <input
                  className="form-check-input mt-0"
                  type="radio"
                  name="exportFilter"
                  checked={filter === key}
                  onChange={() => setFilter(key)}
                />
                <span className="flex-grow-1">
                  {key === 'all' ? '전체' : STATUS_META[key].label}
                </span>
                <span className={`badge ${filter === key ? 'text-bg-light' : 'text-bg-secondary'}`}>
                  {countFor(key) ?? '-'}
                </span>
              </label>
            ))}
          </div>

          <button
            type="button"
            className="btn btn-primary btn-lg w-100"
            onClick={() => void handleExport()}
            disabled={isExporting || selectedCount === 0}
          >
            {isExporting ? (
              <>
                <span className="spinner-border spinner-border-sm me-2"></span>내보내는 중...
              </>
            ) : (
              <>
                <i className="bi bi-filetype-csv me-2"></i>CSV 다운로드{' '}
                {selectedCount !== null && `(${selectedCount}건)`}
              </>
            )}
          </button>
          {selectedCount === 0 && (
            <div className="form-text text-center mt-2">내보낼 리드가 없습니다.</div>
          )}

          <div className="form-text mt-3">
            <i className="bi bi-info-circle me-1"></i>Excel 한글 호환(UTF-8 BOM)으로 저장됩니다.
            이름·연락처가 포함되므로 외부 공유에 주의하세요.
          </div>
        </div>
      </div>
    </div>
  )
}

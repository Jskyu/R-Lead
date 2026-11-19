import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getStats } from '../services/api'
import type { LeadStats } from '../types/lead'

interface StatCardProps {
  icon: string
  color: string
  value: number
  label: string
  to?: string
}

function StatCard({ icon, color, value, label, to }: StatCardProps) {
  const body = (
    <div className="card h-100 shadow-sm border-0">
      <div className="card-body d-flex align-items-center gap-3 py-3">
        <span className={`stat-icon bg-${color}-subtle text-${color}-emphasis`}>
          <i className={`bi bi-${icon}`}></i>
        </span>
        <div className="min-w-0">
          <div className="fs-3 fw-bold lh-1">{value}</div>
          <div className="text-secondary small mt-1 text-truncate">{label}</div>
        </div>
      </div>
    </div>
  )
  return to ? (
    <Link to={to} className="text-decoration-none text-reset">
      {body}
    </Link>
  ) : (
    body
  )
}

/**
 * 오늘의 수집 현황 요약 (명함 촬영 화면 상단).
 */
export function Dashboard() {
  const [stats, setStats] = useState<LeadStats | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const data = await getStats()
        if (alive) {
          setStats(data)
          setFailed(false)
        }
      } catch {
        if (alive) setFailed(true)
      }
    }
    void load()
    const timer = window.setInterval(load, 30000)
    return () => {
      alive = false
      window.clearInterval(timer)
    }
  }, [])

  if (failed && !stats) {
    return (
      <div className="alert alert-secondary py-2 small">
        <i className="bi bi-bar-chart me-1"></i>통계를 불러올 수 없습니다. (서버 연결 확인)
      </div>
    )
  }

  if (!stats) {
    return (
      <div className="row g-2 mb-3" aria-busy="true">
        {[0, 1, 2, 3].map(i => (
          <div className="col-6 col-md-3" key={i}>
            <div className="card border-0 shadow-sm placeholder-glow">
              <div className="card-body py-4">
                <span className="placeholder col-8"></span>
              </div>
            </div>
          </div>
        ))}
      </div>
    )
  }

  const counts = stats.status_counts
  const done = (counts.approved ?? 0) + (counts.crm_registered ?? 0)

  return (
    <div className="row g-2 mb-3">
      <div className="col-6 col-md-3">
        <StatCard icon="calendar-day" color="primary" value={stats.today_leads} label="오늘 수집" />
      </div>
      <div className="col-6 col-md-3">
        <StatCard
          icon="hourglass-split"
          color="warning"
          value={stats.pending_review}
          label="확인 대기"
          to="/leads"
        />
      </div>
      <div className="col-6 col-md-3">
        <StatCard icon="check2-circle" color="success" value={done} label="승인 완료" to="/leads" />
      </div>
      <div className="col-6 col-md-3">
        <StatCard
          icon="person-vcard"
          color="secondary"
          value={stats.total_leads}
          label="전체 리드"
          to="/leads"
        />
      </div>
    </div>
  )
}

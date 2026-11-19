import { useEffect, useState } from 'react'
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  NavLink,
  useNavigate,
  useParams,
} from 'react-router-dom'
import { CameraCapture } from './components/CameraCapture'
import { OfflineQueueManager } from './components/OfflineQueueManager'
import { LeadList } from './components/LeadList'
import { LeadReview } from './components/LeadReview'
import { ExportPanel } from './components/ExportPanel'
import { Dashboard } from './components/Dashboard'
import { FeedbackProvider } from './ui/Feedback'
import { getStats } from './services/api'

function HomePage() {
  return (
    <div className="container py-3" style={{ maxWidth: 960 }}>
      <Dashboard />
      <CameraCapture />
    </div>
  )
}

function LeadListPage() {
  const navigate = useNavigate()
  return (
    <div className="container py-3">
      <LeadList onSelectLead={id => navigate(`/leads/${id}`)} />
    </div>
  )
}

function LeadReviewPage() {
  const { leadId } = useParams()
  const navigate = useNavigate()
  const id = Number(leadId)

  if (!Number.isInteger(id) || id <= 0) {
    return <Navigate to="/leads" replace />
  }

  return (
    <div className="container py-3" style={{ maxWidth: 1100 }}>
      <LeadReview
        leadId={id}
        onBack={() => navigate('/leads')}
        onMove={nextId =>
          nextId ? navigate(`/leads/${nextId}`, { replace: true }) : navigate('/leads')
        }
      />
    </div>
  )
}

function ExportPage() {
  return (
    <div className="container py-3" style={{ maxWidth: 720 }}>
      <ExportPanel />
    </div>
  )
}

/** 확인 대기 건수를 주기적으로 가져온다 (내비게이션 배지용). */
function usePendingCount(): number {
  const [count, setCount] = useState(0)
  useEffect(() => {
    let alive = true
    const load = () =>
      getStats()
        .then(s => alive && setCount(s.pending_review))
        .catch(() => undefined)
    load()
    const timer = window.setInterval(load, 15000)
    return () => {
      alive = false
      window.clearInterval(timer)
    }
  }, [])
  return count
}

const NAV_ITEMS = [
  { to: '/', label: '명함 촬영', icon: 'camera', end: true },
  { to: '/leads', label: '리드 관리', icon: 'people', end: false },
  { to: '/export', label: '내보내기', icon: 'download', end: false },
]

function Shell() {
  const pending = usePendingCount()
  const [online, setOnline] = useState(navigator.onLine)

  useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => {
      window.removeEventListener('online', on)
      window.removeEventListener('offline', off)
    }
  }, [])

  return (
    <>
      {/* 데스크톱 상단 내비게이션 */}
      <nav className="navbar navbar-expand bg-body border-bottom sticky-top">
        <div className="container">
          <NavLink to="/" className="navbar-brand fw-bold d-flex align-items-center">
            <span className="brand-mark">
              <i className="bi bi-person-vcard"></i>
            </span>
            R-Lead
          </NavLink>
          <ul className="navbar-nav ms-auto d-none d-md-flex">
            {NAV_ITEMS.map(item => (
              <li className="nav-item" key={item.to}>
                <NavLink to={item.to} end={item.end} className="nav-link">
                  <i className={`bi bi-${item.icon} me-1`}></i>
                  {item.label}
                  {item.to === '/leads' && pending > 0 && (
                    <span className="badge text-bg-warning ms-1">{pending}</span>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>
          <span className="navbar-text d-md-none small">명함 → 리드 자동화</span>
        </div>
      </nav>

      {!online && (
        <div className="alert alert-warning rounded-0 mb-0 text-center py-2 small" role="status">
          <i className="bi bi-wifi-off me-1"></i>
          오프라인 상태입니다. 촬영한 명함은 이 기기에 저장되고, 연결되면 자동으로 전송됩니다.
        </div>
      )}

      <OfflineQueueManager />

      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/leads" element={<LeadListPage />} />
        <Route path="/leads/:leadId" element={<LeadReviewPage />} />
        <Route path="/export" element={<ExportPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      {/* 모바일 하단 탭 */}
      <nav className="bottom-nav d-flex d-md-none" aria-label="주요 메뉴">
        {NAV_ITEMS.map(item => (
          <NavLink key={item.to} to={item.to} end={item.end}>
            <i className={`bi bi-${item.icon}`}></i>
            {item.label}
            {item.to === '/leads' && pending > 0 && (
              <span className="badge rounded-pill text-bg-warning">{pending}</span>
            )}
          </NavLink>
        ))}
      </nav>
    </>
  )
}

function App() {
  return (
    <BrowserRouter>
      <FeedbackProvider>
        <Shell />
      </FeedbackProvider>
    </BrowserRouter>
  )
}

export default App

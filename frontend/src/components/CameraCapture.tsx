import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { resizeAndFixOrientation, generateUUID } from '../utils/image'
import { APIError, uploadCard, isOnline } from '../services/api'
import { offlineQueue } from '../services/offline-queue'
import { useFeedback } from '../ui/feedback-context'
import type { CardUploadRequest } from '../types/lead'

type ItemState = 'ready' | 'uploading' | 'done' | 'duplicate' | 'queued' | 'failed'

interface CaptureItem {
  /** 업로드 재시도 시 중복 생성을 막기 위한 고정 UUID */
  uuid: string
  file: File
  preview: string
  state: ItemState
  message?: string
}

// FIXME: 관심 제품 목록은 회사 제품 구성에 맞게 확정 필요
const AVAILABLE_PRODUCTS = [
  '산업용 로봇',
  '협동 로봇',
  '자율주행 AGV',
  '비전 시스템',
  'AI 솔루션',
  '자동화 시스템',
]

const STORAGE_KEY = 'rlead-capture-defaults'

interface Defaults {
  eventName: string
  boothNumber: string
  collectedBy: string
}

/** 행사명·부스·수집자는 매번 입력하지 않도록 이 기기에 기억한다. */
function loadDefaults(): Defaults {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw)
      return {
        eventName: '',
        boothNumber: '',
        collectedBy: '',
        ...(JSON.parse(raw) as Partial<Defaults>),
      }
  } catch {
    /* 저장소를 쓸 수 없는 환경이면 무시 */
  }
  return { eventName: '', boothNumber: '', collectedBy: '' }
}

function saveDefaults(d: Defaults): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(d))
  } catch {
    /* ignore */
  }
}

const STATE_BADGE: Record<ItemState, { cls: string; label: string }> = {
  ready: { cls: 'text-bg-light', label: '대기' },
  uploading: { cls: 'text-bg-primary', label: '전송 중' },
  done: { cls: 'text-bg-success', label: '완료' },
  duplicate: { cls: 'text-bg-info', label: '중복' },
  queued: { cls: 'text-bg-warning', label: '기기에 저장' },
  failed: { cls: 'text-bg-danger', label: '실패' },
}

export function CameraCapture() {
  const { toast } = useFeedback()
  const [items, setItems] = useState<CaptureItem[]>([])
  const [isPreparing, setIsPreparing] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [finished, setFinished] = useState(false)
  const [defaults, setDefaults] = useState<Defaults>(loadDefaults)
  const [marketingConsent, setMarketingConsent] = useState(false)
  const [interestedProducts, setInterestedProducts] = useState<string[]>([])
  const cameraInputRef = useRef<HTMLInputElement>(null)
  const albumInputRef = useRef<HTMLInputElement>(null)
  const itemsRef = useRef<CaptureItem[]>([])
  itemsRef.current = items

  // 화면을 떠날 때 미리보기 URL 해제
  useEffect(() => {
    return () => itemsRef.current.forEach(i => URL.revokeObjectURL(i.preview))
  }, [])

  const updateDefaults = (patch: Partial<Defaults>) => {
    const next = { ...defaults, ...patch }
    setDefaults(next)
    saveDefaults(next)
  }

  const toggleProduct = (product: string) =>
    setInterestedProducts(prev =>
      prev.includes(product) ? prev.filter(p => p !== product) : [...prev, product]
    )

  const handleSelect = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length === 0) return

    setIsPreparing(true)
    setFinished(false)
    const added: CaptureItem[] = []
    let failedCount = 0
    for (const file of files) {
      try {
        const processed = await resizeAndFixOrientation(file)
        added.push({
          uuid: generateUUID(),
          file: processed,
          preview: URL.createObjectURL(processed),
          state: 'ready',
        })
      } catch {
        failedCount++
      }
    }
    setItems(prev => [...prev.filter(i => i.state === 'ready'), ...added])
    setIsPreparing(false)
    if (failedCount > 0)
      toast(
        `${failedCount}개 이미지는 처리하지 못했습니다. (이미지 파일인지 확인해 주세요)`,
        'danger'
      )
  }

  const removeItem = (uuid: string) => {
    setItems(prev => {
      const target = prev.find(i => i.uuid === uuid)
      if (target) URL.revokeObjectURL(target.preview)
      return prev.filter(i => i.uuid !== uuid)
    })
  }

  const patchItem = (uuid: string, patch: Partial<CaptureItem>) =>
    setItems(prev => prev.map(i => (i.uuid === uuid ? { ...i, ...patch } : i)))

  const handleUpload = async () => {
    const targets = items.filter(i => i.state === 'ready' || i.state === 'failed')
    if (targets.length === 0) return

    setIsUploading(true)
    setFinished(false)
    const clean = (v: string): string | undefined => (v.trim() ? v.trim() : undefined)
    saveDefaults(defaults)

    let done = 0
    let queued = 0
    let failed = 0

    for (const item of targets) {
      const metadata: CardUploadRequest = {
        client_uuid: item.uuid,
        event_name: clean(defaults.eventName),
        booth_number: clean(defaults.boothNumber),
        collected_by: clean(defaults.collectedBy),
        interested_products: interestedProducts.length > 0 ? interestedProducts : undefined,
        marketing_consent: marketingConsent,
      }

      patchItem(item.uuid, { state: 'uploading', message: undefined })

      const saveToQueue = async (reason: string) => {
        try {
          await offlineQueue.addToQueue(item.uuid, item.file, metadata)
          patchItem(item.uuid, { state: 'queued', message: reason })
          queued++
        } catch {
          patchItem(item.uuid, {
            state: 'failed',
            message: '업로드와 기기 저장에 모두 실패했습니다.',
          })
          failed++
        }
      }

      if (!isOnline()) {
        await saveToQueue('오프라인 — 연결되면 자동 전송')
        continue
      }

      try {
        const response = await uploadCard(item.file, metadata)
        if (response.duplicate) {
          patchItem(item.uuid, {
            state: 'duplicate',
            message: `기존 리드 #${response.duplicate_of_lead_id ?? response.lead_id}`,
          })
        } else {
          patchItem(item.uuid, { state: 'done', message: `리드 #${response.lead_id}` })
        }
        done++
      } catch (error) {
        const status = error instanceof APIError ? error.status : undefined
        // 4xx(요청 자체가 잘못됨)는 재시도해도 소용없으므로 큐에 넣지 않는다.
        if (status && status >= 400 && status < 500 && status !== 408 && status !== 429) {
          patchItem(item.uuid, {
            state: 'failed',
            message: error instanceof Error ? error.message : '업로드 실패',
          })
          failed++
        } else {
          await saveToQueue('서버 연결 실패 — 기기에 저장됨, 자동 재전송')
        }
      }
    }

    setIsUploading(false)
    setFinished(true)
    if (done > 0 && queued === 0 && failed === 0)
      toast(`${done}건 업로드 완료. AI가 분석을 시작합니다.`, 'success')
    else if (queued > 0)
      toast(`${queued}건은 기기에 저장했습니다. 연결되면 자동으로 전송됩니다.`, 'warning')
    if (failed > 0) toast(`${failed}건 업로드에 실패했습니다. 다시 시도해 주세요.`, 'danger')
    // 큐 배너 갱신
    window.dispatchEvent(new Event('rlead-queue-changed'))
  }

  const resetAll = () => {
    items.forEach(i => URL.revokeObjectURL(i.preview))
    setItems([])
    setFinished(false)
    setMarketingConsent(false)
    setInterestedProducts([])
  }

  const pendingCount = items.filter(i => i.state === 'ready' || i.state === 'failed').length
  const successCount = items.filter(i => i.state === 'done' || i.state === 'duplicate').length

  return (
    <div className="card shadow-sm border-0">
      <div className="card-body">
        <h2 className="h5 card-title mb-3">
          <i className="bi bi-camera me-2 text-primary"></i>명함 촬영
        </h2>

        {/* 수집 정보 (기기에 기억) */}
        <div className="row g-2 mb-3">
          <div className="col-12 col-md-5">
            <label htmlFor="eventName" className="form-label small mb-1">
              행사명
            </label>
            <input
              id="eventName"
              className="form-control"
              value={defaults.eventName}
              onChange={e => updateDefaults({ eventName: e.target.value })}
              placeholder="예: 로보월드 2026"
              disabled={isUploading}
            />
          </div>
          <div className="col-6 col-md-3">
            <label htmlFor="boothNumber" className="form-label small mb-1">
              부스
            </label>
            <input
              id="boothNumber"
              className="form-control"
              value={defaults.boothNumber}
              onChange={e => updateDefaults({ boothNumber: e.target.value })}
              placeholder="예: A-123"
              disabled={isUploading}
            />
          </div>
          <div className="col-6 col-md-4">
            <label htmlFor="collectedBy" className="form-label small mb-1">
              수집 담당자
            </label>
            <input
              id="collectedBy"
              className="form-control"
              value={defaults.collectedBy}
              onChange={e => updateDefaults({ collectedBy: e.target.value })}
              placeholder="이름"
              disabled={isUploading}
            />
          </div>
          <div className="col-12 form-text mt-1">
            <i className="bi bi-info-circle me-1"></i>행사·부스·담당자는 이 기기에 기억되어 다음
            촬영에도 유지됩니다.
          </div>
        </div>

        {/* 촬영 / 선택 */}
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          multiple
          hidden
          onChange={handleSelect}
        />
        <input
          ref={albumInputRef}
          type="file"
          accept="image/*"
          multiple
          hidden
          onChange={handleSelect}
        />
        <div className="row g-2">
          <div className="col-12 col-sm-8">
            <button
              type="button"
              className="btn btn-primary w-100 capture-btn"
              onClick={() => cameraInputRef.current?.click()}
              disabled={isPreparing || isUploading}
            >
              {isPreparing ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2"></span>이미지 처리 중...
                </>
              ) : (
                <>
                  <i className="bi bi-camera-fill me-2"></i>명함 촬영
                </>
              )}
            </button>
          </div>
          <div className="col-12 col-sm-4">
            <button
              type="button"
              className="btn btn-outline-secondary w-100 h-100 py-3"
              onClick={() => albumInputRef.current?.click()}
              disabled={isPreparing || isUploading}
            >
              <i className="bi bi-images me-1"></i>앨범에서 선택
            </button>
          </div>
        </div>

        <details className="mt-3 small text-secondary">
          <summary>촬영 가이드</summary>
          <ul className="mb-0 mt-2 ps-3">
            <li>명함을 평평하게 놓고 정면에서 촬영하세요.</li>
            <li>밝은 곳에서, 명함 전체가 화면에 들어오도록 찍으세요.</li>
            <li>여러 장을 한 번에 선택해 올릴 수 있습니다.</li>
            <li>연결이 불안정해도 걱정 마세요. 기기에 저장해 두었다가 연결되면 자동 전송됩니다.</li>
          </ul>
        </details>

        {/* 미리보기 */}
        {items.length > 0 && (
          <div className="mt-4">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <h3 className="h6 mb-0">명함 {items.length}장</h3>
              <button
                type="button"
                className="btn btn-sm btn-link text-secondary"
                onClick={resetAll}
                disabled={isUploading}
              >
                모두 지우기
              </button>
            </div>
            <div className="row g-2">
              {items.map(item => {
                const badge = STATE_BADGE[item.state]
                return (
                  <div className="col-6 col-md-4" key={item.uuid}>
                    <div className="preview-thumb">
                      <img src={item.preview} alt="명함 미리보기" />
                      {(item.state === 'ready' || item.state === 'failed') && !isUploading && (
                        <button
                          type="button"
                          className="btn btn-sm btn-dark btn-remove rounded-circle"
                          aria-label="이 명함 제외"
                          onClick={() => removeItem(item.uuid)}
                        >
                          <i className="bi bi-x-lg"></i>
                        </button>
                      )}
                      <span className={`badge thumb-state ${badge.cls}`}>
                        {item.state === 'uploading' && (
                          <span
                            className="spinner-border spinner-border-sm me-1"
                            style={{ width: '.7em', height: '.7em' }}
                          ></span>
                        )}
                        {badge.label}
                      </span>
                    </div>
                    {item.message && (
                      <div className="small text-secondary mt-1 text-truncate">{item.message}</div>
                    )}
                  </div>
                )
              })}
            </div>

            {pendingCount > 0 && (
              <div className="mt-3">
                <label className="form-label small mb-1">관심 제품 (복수 선택)</label>
                <div className="d-flex flex-wrap gap-2 mb-3">
                  {AVAILABLE_PRODUCTS.map(product => (
                    <span key={product}>
                      <input
                        type="checkbox"
                        className="btn-check"
                        id={`prod-${product}`}
                        checked={interestedProducts.includes(product)}
                        onChange={() => toggleProduct(product)}
                        disabled={isUploading}
                      />
                      <label
                        className="btn btn-sm btn-outline-primary rounded-pill"
                        htmlFor={`prod-${product}`}
                      >
                        {product}
                      </label>
                    </span>
                  ))}
                </div>

                <div className="form-check form-switch mb-3">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    role="switch"
                    id="consent"
                    checked={marketingConsent}
                    onChange={e => setMarketingConsent(e.target.checked)}
                    disabled={isUploading}
                  />
                  <label className="form-check-label" htmlFor="consent">
                    마케팅 정보 수신에 동의함{' '}
                    <span className="text-secondary small">(고객 동의를 받은 경우에만 체크)</span>
                  </label>
                </div>

                <button
                  type="button"
                  className="btn btn-success btn-lg w-100"
                  onClick={handleUpload}
                  disabled={isUploading}
                >
                  {isUploading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2"></span>업로드 중...
                    </>
                  ) : (
                    <>
                      <i className="bi bi-cloud-arrow-up me-2"></i>
                      {pendingCount}장 업로드
                    </>
                  )}
                </button>
              </div>
            )}

            {finished && (
              <div className="alert alert-light border mt-3 mb-0 d-flex flex-wrap justify-content-between align-items-center gap-2">
                <span>
                  {successCount > 0 && <>업로드 {successCount}건 완료. </>}
                  AI 분석은 보통 10~20초 걸립니다.
                </span>
                <span className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-primary"
                    onClick={resetAll}
                  >
                    <i className="bi bi-plus-lg me-1"></i>새 명함
                  </button>
                  <Link to="/leads" className="btn btn-sm btn-primary">
                    리드 확인하기 <i className="bi bi-arrow-right"></i>
                  </Link>
                </span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

import { useCallback, useEffect, useRef, useState } from 'react'
import { offlineQueue } from '../services/offline-queue'
import { APIError, uploadCard, isOnline } from '../services/api'
import { useFeedback } from '../ui/feedback-context'

/**
 * 기기(IndexedDB)에 저장된 미전송 명함을 보여주고 재전송한다.
 * iOS Safari 는 Background Sync 를 지원하지 않으므로 앱을 열 때, 온라인 전환 시, 업로드 직후에 재전송한다.
 */
export function OfflineQueueManager() {
  const { toast } = useFeedback()
  const [queueSize, setQueueSize] = useState(0)
  const [isProcessing, setIsProcessing] = useState(false)
  const processingRef = useRef(false)

  const refreshSize = useCallback(async () => {
    try {
      setQueueSize(await offlineQueue.getQueueSize())
    } catch {
      setQueueSize(0)
    }
  }, [])

  const processQueue = useCallback(
    async (manual = false) => {
      if (processingRef.current) return
      if (!isOnline()) {
        if (manual) toast('오프라인 상태입니다. 연결 후 다시 시도해 주세요.', 'warning')
        return
      }
      processingRef.current = true
      setIsProcessing(true)

      let success = 0
      let failed = 0
      let dropped = 0
      try {
        const queued = await offlineQueue.getAllQueued()
        for (const item of queued) {
          try {
            const file = new File([item.imageBlob], `card-${item.id}.jpg`, {
              type: item.imageBlob.type || 'image/jpeg',
            })
            await uploadCard(file, item.metadata)
            await offlineQueue.removeFromQueue(item.id)
            success++
          } catch (error) {
            const status = error instanceof APIError ? error.status : undefined
            if (status && status >= 400 && status < 500 && status !== 408 && status !== 429) {
              // 재시도해도 성공할 수 없는 요청은 큐에서 제거하고 알린다.
              await offlineQueue.removeFromQueue(item.id)
              dropped++
            } else {
              await offlineQueue.incrementRetryCount(item.id)
              failed++
            }
          }
        }
      } catch {
        if (manual) toast('저장된 명함을 불러오지 못했습니다.', 'danger')
      } finally {
        processingRef.current = false
        setIsProcessing(false)
        await refreshSize()
      }

      if (success > 0) toast(`저장해 둔 명함 ${success}건을 전송했습니다.`, 'success')
      if (dropped > 0)
        toast(`${dropped}건은 서버가 거부하여 삭제했습니다. (이미지 형식 등)`, 'danger')
      if (failed > 0 && manual)
        toast(`${failed}건은 아직 전송하지 못했습니다. 잠시 후 다시 시도해 주세요.`, 'warning')
    },
    [refreshSize, toast]
  )

  useEffect(() => {
    void refreshSize().then(() => processQueue())

    const onOnline = () => void processQueue()
    const onChanged = () => void refreshSize()
    window.addEventListener('online', onOnline)
    window.addEventListener('rlead-queue-changed', onChanged)
    // 서버 일시 장애 대비: 1분마다 재시도
    const timer = window.setInterval(() => void processQueue(), 60000)
    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('rlead-queue-changed', onChanged)
      window.clearInterval(timer)
    }
  }, [processQueue, refreshSize])

  if (queueSize === 0) return null

  return (
    <div className="container pt-3" style={{ maxWidth: 960 }}>
      <div
        className="alert alert-warning d-flex flex-wrap justify-content-between align-items-center gap-2 mb-0"
        role="status"
      >
        <span>
          <i className="bi bi-phone me-2"></i>
          전송 대기 중인 명함 <span className="badge text-bg-warning border">{queueSize}</span>건이
          이 기기에 저장되어 있습니다.
        </span>
        <button
          type="button"
          className="btn btn-sm btn-warning"
          onClick={() => void processQueue(true)}
          disabled={isProcessing}
        >
          {isProcessing ? (
            <>
              <span className="spinner-border spinner-border-sm me-1"></span>전송 중...
            </>
          ) : (
            <>
              <i className="bi bi-cloud-arrow-up me-1"></i>지금 전송
            </>
          )}
        </button>
      </div>
    </div>
  )
}

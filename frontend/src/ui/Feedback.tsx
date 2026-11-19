import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { FeedbackContext } from './feedback-context'
import type { ConfirmOptions, ToastKind } from './feedback-context'

interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

const TOAST_ICON: Record<ToastKind, string> = {
  success: 'check-circle-fill',
  danger: 'exclamation-triangle-fill',
  warning: 'exclamation-circle-fill',
  info: 'info-circle-fill',
}

/**
 * alert()/confirm() 대신 사용하는 Bootstrap 토스트와 확인 모달.
 */
export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const [dialog, setDialog] = useState<(ConfirmOptions & { resolve: (v: boolean) => void }) | null>(
    null
  )
  const nextId = useRef(1)

  const toast = useCallback((message: string, kind: ToastKind = 'info') => {
    const id = nextId.current++
    setToasts(prev => [...prev, { id, kind, message }])
    window.setTimeout(
      () => setToasts(prev => prev.filter(t => t.id !== id)),
      kind === 'danger' ? 6000 : 3500
    )
  }, [])

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>(resolve => setDialog({ ...options, resolve })),
    []
  )

  const close = (result: boolean) => {
    dialog?.resolve(result)
    setDialog(null)
  }

  useEffect(() => {
    if (!dialog) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        dialog.resolve(false)
        setDialog(null)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [dialog])

  return (
    <FeedbackContext.Provider value={{ toast, confirm }}>
      {children}

      <div className="toast-container position-fixed top-0 end-0 p-3 app-toasts" aria-live="polite">
        {toasts.map(t => (
          <div
            key={t.id}
            className={`toast show align-items-center text-bg-${t.kind} border-0 mb-2`}
            role="alert"
          >
            <div className="d-flex">
              <div className="toast-body">
                <i className={`bi bi-${TOAST_ICON[t.kind]} me-2`}></i>
                {t.message}
              </div>
              <button
                type="button"
                className="btn-close btn-close-white me-2 m-auto"
                aria-label="닫기"
                onClick={() => setToasts(prev => prev.filter(x => x.id !== t.id))}
              ></button>
            </div>
          </div>
        ))}
      </div>

      {dialog && (
        <>
          <div className="modal fade show d-block" tabIndex={-1} role="dialog" aria-modal="true">
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">{dialog.title ?? '확인'}</h5>
                  <button
                    type="button"
                    className="btn-close"
                    aria-label="닫기"
                    onClick={() => close(false)}
                  ></button>
                </div>
                <div className="modal-body" style={{ whiteSpace: 'pre-line' }}>
                  {dialog.message}
                </div>
                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => close(false)}
                  >
                    취소
                  </button>
                  <button
                    type="button"
                    className={`btn btn-${dialog.variant ?? 'primary'}`}
                    onClick={() => close(true)}
                    autoFocus
                  >
                    {dialog.confirmLabel ?? '확인'}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop fade show"></div>
        </>
      )}
    </FeedbackContext.Provider>
  )
}

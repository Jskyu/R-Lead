import { createContext, useContext } from 'react'

export type ToastKind = 'success' | 'danger' | 'warning' | 'info'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmLabel?: string
  /** Bootstrap 버튼 색상 */
  variant?: 'primary' | 'danger' | 'success' | 'warning'
}

export interface FeedbackApi {
  toast: (message: string, kind?: ToastKind) => void
  confirm: (options: ConfirmOptions) => Promise<boolean>
}

export const FeedbackContext = createContext<FeedbackApi | null>(null)

export function useFeedback(): FeedbackApi {
  const ctx = useContext(FeedbackContext)
  if (!ctx) throw new Error('useFeedback must be used inside FeedbackProvider')
  return ctx
}

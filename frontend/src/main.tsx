import React from 'react'
import ReactDOM from 'react-dom/client'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
import App from './App'
import './index.css'
import { logBackendInfo } from './config/backend'

// 백엔드 설정 정보 출력 (개발 환경에서만)
if (import.meta.env.DEV) {
  logBackendInfo()
}

// Register service worker for PWA
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js')
      .then(registration => {
        console.log('SW registered:', registration)
      })
      .catch(error => {
        console.error('SW registration failed:', error)
      })
  })
}

// 시스템 다크 모드 연동 (Bootstrap 5.3 color modes)
const media = window.matchMedia('(prefers-color-scheme: dark)')
const applyTheme = () =>
  document.documentElement.setAttribute('data-bs-theme', media.matches ? 'dark' : 'light')
applyTheme()
media.addEventListener('change', applyTheme)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)

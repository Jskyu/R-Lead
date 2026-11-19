/**
 * 백엔드 설정 모듈.
 * 환경 변수에 따라 FastAPI 또는 Spring Boot 백엔드를 선택합니다.
 */

export type BackendType = 'fastapi' | 'springboot'

interface BackendConfig {
  type: BackendType
  baseUrl: string
}

/**
 * 환경 변수에서 백엔드 타입을 가져옵니다.
 */
function getBackendType(): BackendType {
  const type = import.meta.env.VITE_BACKEND_TYPE?.toLowerCase()

  if (type === 'springboot') {
    return 'springboot'
  }

  // 기본값은 fastapi
  return 'fastapi'
}

/**
 * 백엔드 타입에 따라 기본 URL을 가져옵니다.
 */
function getBackendBaseUrl(type: BackendType): string {
  if (type === 'springboot') {
    return import.meta.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080'
  }

  return import.meta.env.VITE_FASTAPI_URL || 'http://localhost:8000'
}

/**
 * 현재 백엔드 설정을 가져옵니다.
 */
export function getBackendConfig(): BackendConfig {
  const type = getBackendType()
  const baseUrl = getBackendBaseUrl(type)

  return { type, baseUrl }
}

/**
 * API 기본 URL을 가져옵니다.
 *
 * 개발(Vite 프록시)과 운영(nginx 프록시) 모두 같은 출처의 `/api` 를 쓰므로 CORS 가 필요 없고,
 * 백엔드 선택(FastAPI/Spring)은 프록시 대상만 바꾸면 된다.
 * 다른 주소의 API 를 직접 쓰려면 VITE_API_BASE_URL 을 지정한다 (예: https://api.example.com/api).
 */
export function getApiBaseUrl(): string {
  const override = import.meta.env.VITE_API_BASE_URL as string | undefined
  return override ? override.replace(/\/$/, '') : '/api'
}

/**
 * 현재 백엔드 타입을 반환합니다.
 */
export function getCurrentBackendType(): BackendType {
  return getBackendConfig().type
}

/**
 * 백엔드 정보를 콘솔에 출력합니다 (디버깅용).
 */
export function logBackendInfo(): void {
  const config = getBackendConfig()
  console.log('🔧 Backend Configuration:')
  console.log(`  Type: ${config.type}`)
  console.log(`  Base URL: ${config.baseUrl}`)
  console.log(`  API URL: ${getApiBaseUrl()}`)
}

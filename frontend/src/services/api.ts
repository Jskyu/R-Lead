import type {
  BatchUploadResponse,
  CardUploadRequest,
  CardUploadResponse,
  Lead,
  LeadStats,
  LeadStatus,
  LeadUpdate,
  QueueStatus,
} from '../types/lead'
import { getApiBaseUrl } from '../config/backend'

const API_BASE_URL = getApiBaseUrl()

export class APIError extends Error {
  constructor(
    message: string,
    public status?: number,
    public data?: unknown
  ) {
    super(message)
    this.name = 'APIError'
  }
}

/**
 * 명함 사진을 업로드합니다.
 */
export async function uploadCard(
  imageFile: File,
  metadata: CardUploadRequest
): Promise<CardUploadResponse> {
  const formData = new FormData()
  formData.append('image', imageFile)
  formData.append('metadata', JSON.stringify(metadata))

  try {
    const response = await fetch(`${API_BASE_URL}/cards/upload`, {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new APIError(
        errorData.message || `Upload failed: ${response.statusText}`,
        response.status,
        errorData
      )
    }

    return await response.json()
  } catch (error) {
    if (error instanceof APIError) {
      throw error
    }
    throw new APIError('네트워크 오류가 발생했습니다.')
  }
}

/**
 * 여러 명함 사진을 한 번에 업로드합니다.
 */
export async function uploadCardsBatch(
  imageFiles: File[],
  metadata: Omit<CardUploadRequest, 'client_uuid'>
): Promise<BatchUploadResponse> {
  const formData = new FormData()

  // 모든 이미지 파일 추가 (동일한 필드명 사용)
  imageFiles.forEach(file => {
    formData.append('images', file)
  })

  // 메타데이터 추가 (client_uuid 제외)
  formData.append('metadata', JSON.stringify(metadata))

  try {
    const response = await fetch(`${API_BASE_URL}/cards/upload-batch`, {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new APIError(
        errorData.message || `Upload failed: ${response.statusText}`,
        response.status,
        errorData
      )
    }

    return await response.json()
  } catch (error) {
    if (error instanceof APIError) {
      throw error
    }
    throw new APIError('네트워크 오류가 발생했습니다.')
  }
}

/**
 * 온라인 상태인지 확인합니다.
 */
export function isOnline(): boolean {
  return navigator.onLine
}

/**
 * 처리 대기 중인 작업 상태를 조회합니다.
 */
export async function getQueueStatus(): Promise<QueueStatus> {
  try {
    const response = await fetch(`${API_BASE_URL}/leads/queue/status`)

    if (!response.ok) {
      throw new APIError(`Failed to fetch queue status: ${response.statusText}`, response.status)
    }

    return await response.json()
  } catch (error) {
    if (error instanceof APIError) {
      throw error
    }
    throw new APIError('네트워크 오류가 발생했습니다.')
  }
}

/**
 * 리드를 삭제합니다.
 */
export async function deleteLead(leadId: number): Promise<{ message: string }> {
  try {
    const response = await fetch(`${API_BASE_URL}/leads/${leadId}`, {
      method: 'DELETE',
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new APIError(
        errorData.message || `Failed to delete lead: ${response.statusText}`,
        response.status,
        errorData
      )
    }

    return await response.json()
  } catch (error) {
    if (error instanceof APIError) {
      throw error
    }
    throw new APIError('네트워크 오류가 발생했습니다.')
  }
}

/**
 * JSON API 공통 요청 (오류 메시지 추출 포함).
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, init)
  } catch {
    throw new APIError('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }

  if (!response.ok) {
    const data = (await response.json().catch(() => ({}))) as { message?: string; detail?: unknown }
    throw new APIError(
      data.message ||
        (typeof data.detail === 'string' ? data.detail : '') ||
        `요청에 실패했습니다. (${response.status})`,
      response.status,
      data
    )
  }
  return (await response.json()) as T
}

const jsonInit = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export function getLeads(status?: LeadStatus | 'all'): Promise<Lead[]> {
  const query = status && status !== 'all' ? `&status=${status}` : ''
  return request<Lead[]>(`/leads/?limit=500${query}`)
}

export function getLead(leadId: number): Promise<Lead> {
  return request<Lead>(`/leads/${leadId}`)
}

export function updateLead(leadId: number, data: LeadUpdate): Promise<Lead> {
  return request<Lead>(`/leads/${leadId}`, jsonInit('PATCH', data))
}

export function approveLead(
  leadId: number,
  approved: boolean,
  updates?: LeadUpdate
): Promise<Lead> {
  return request<Lead>(
    `/leads/${leadId}/approve`,
    jsonInit('POST', { approved, updates: updates ?? null })
  )
}

export function bulkReextract(leadIds: number[]): Promise<{ message: string; job_count: number }> {
  return request(`/leads/bulk-reextract`, jsonInit('POST', { lead_ids: leadIds }))
}

export function bulkDelete(leadIds: number[]): Promise<{ message: string; deleted_count: number }> {
  return request(`/leads/bulk-delete`, jsonInit('POST', { lead_ids: leadIds }))
}

export function getStats(): Promise<LeadStats> {
  return request<LeadStats>(`/export/stats`)
}

/**
 * CSV 를 내려받습니다.
 */
export async function downloadCsv(status?: LeadStatus | 'all'): Promise<void> {
  const query = status && status !== 'all' ? `?status=${status}` : ''
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/export/csv${query}`)
  } catch {
    throw new APIError('서버에 연결할 수 없습니다.')
  }
  if (!response.ok)
    throw new APIError(`내보내기에 실패했습니다. (${response.status})`, response.status)

  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition')
  const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] || 'leads.csv'
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

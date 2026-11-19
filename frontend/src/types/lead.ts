export enum LeadStatus {
  UPLOADED = 'uploaded',
  EXTRACTED = 'extracted',
  REVIEWED = 'reviewed',
  APPROVED = 'approved',
  REJECTED = 'rejected',
  CRM_REGISTERED = 'crm_registered',
  CRM_FAILED = 'crm_failed',
}

export interface ValidationIssue {
  field: string
  /** FastAPI: missing/invalid/suspicious, Spring: empty/invalid_format/missing_in_original */
  issue_type: string
  message: string
}

export interface StatusMeta {
  label: string
  /** Bootstrap 색상 이름 */
  color: 'secondary' | 'info' | 'primary' | 'success' | 'danger' | 'warning' | 'dark'
  icon: string
}

export const STATUS_META: Record<LeadStatus, StatusMeta> = {
  [LeadStatus.UPLOADED]: { label: '업로드됨', color: 'secondary', icon: 'cloud-upload' },
  [LeadStatus.EXTRACTED]: { label: '확인 대기', color: 'warning', icon: 'hourglass-split' },
  [LeadStatus.REVIEWED]: { label: '확인됨', color: 'info', icon: 'eye' },
  [LeadStatus.APPROVED]: { label: '승인됨', color: 'success', icon: 'check-circle' },
  [LeadStatus.REJECTED]: { label: '거부됨', color: 'danger', icon: 'x-circle' },
  [LeadStatus.CRM_REGISTERED]: { label: 'CRM 등록', color: 'primary', icon: 'send-check' },
  [LeadStatus.CRM_FAILED]: { label: 'CRM 실패', color: 'dark', icon: 'exclamation-octagon' },
}

/** 백엔드(FastAPI 소문자 / Spring 대문자)에 상관없이 상태값을 정규화한다. */
export function normalizeStatus(value: string | null | undefined): LeadStatus {
  const v = (value ?? '').toLowerCase()
  return (Object.values(LeadStatus) as string[]).includes(v)
    ? (v as LeadStatus)
    : LeadStatus.UPLOADED
}

export interface Lead {
  id: number
  name: string | null
  company: string | null
  department: string | null
  job_title: string | null
  email: string | null
  phone: string | null
  address: string | null
  website: string | null
  interested_products: string[] | null
  event_name: string | null
  booth_number: string | null
  collected_by: string | null
  collected_at: string | null
  marketing_consent: boolean
  marketing_consent_at: string | null
  status: LeadStatus
  ai_extracted_data: Record<string, unknown> | null
  validation_issues: ValidationIssue[] | null
  field_confidences: Record<string, number> | null
  modified_fields: string[] | null
  card_image_url: string | null
  card_image_key: string | null
  image_delete_at: string | null
  /** 한 사진에 명함이 여러 장일 때 몇 번째인지 (0부터). 한 장이면 null */
  card_index?: number | null
  duplicate_of_lead_id: number | null
  crm_id: string | null
  crm_registered_at: string | null
  crm_error_message: string | null
  client_uuid: string | null
  created_at: string
  updated_at: string
}

export interface LeadUpdate {
  name?: string | null
  company?: string | null
  department?: string | null
  job_title?: string | null
  email?: string | null
  phone?: string | null
  address?: string | null
  website?: string | null
  interested_products?: string[] | null
  marketing_consent?: boolean
}

export interface LeadStats {
  total_leads: number
  status_counts: Record<string, number>
  today_leads: number
  this_week_leads: number
  pending_review: number
}

export interface CardUploadRequest {
  client_uuid: string
  event_name?: string
  booth_number?: string
  collected_by?: string
  interested_products?: string[]
  marketing_consent: boolean
}

export interface CardUploadResponse {
  lead_id: number
  message: string
  duplicate: boolean
  duplicate_of_lead_id?: number
}

export interface BatchUploadResult {
  index: number
  success: boolean
  lead_id?: number
  message: string
  duplicate: boolean
}

export interface BatchUploadResponse {
  total: number
  success_count: number
  results: BatchUploadResult[]
}

export interface QueueLead {
  id: number
  event_name: string | null
  created_at: string
}

export interface QueueStatus {
  pending_count: number
  processing_count: number
  failed_count: number
  pending_leads: QueueLead[]
  processing_leads: QueueLead[]
  /** 추출 실패 사유 (Spring 백엔드) */
  failed_leads?: { id: number; error: string }[]
}

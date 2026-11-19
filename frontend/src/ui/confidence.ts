/** 평균 신뢰도(0~100) 계산. */
export function averageConfidence(conf: Record<string, number> | null): number | null {
  if (!conf) return null
  const values = Object.values(conf)
  if (values.length === 0) return null
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length)
}

export function confidenceColor(value: number | null | undefined): string {
  if (value === null || value === undefined) return 'secondary'
  if (value >= 80) return 'success'
  if (value >= 50) return 'warning'
  return 'danger'
}

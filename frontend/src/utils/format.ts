import type { RoomStatus } from '../types'

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  COLLECTING_PREFERENCES: '收集偏好',
  MEMBERS_LOCKED: '确认偏好',
  READY_TO_PLAN: '可以规划',
  PLANNING: '规划中',
  WAITING_INPUT: '等待回答',
  PLANS_READY: '方案已就绪',
  FINALIZED: '已确定',
  FAILED: '规划失败',
}

export function statusLabel(status?: RoomStatus | string): string {
  return status ? STATUS_LABELS[status] || status : '加载中'
}

export function formatDate(value?: string, includeDate = false): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('zh-CN', includeDate
    ? { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }
    : { hour: '2-digit', minute: '2-digit' }).format(date)
}

export function avatarText(name?: string, userId?: number): string {
  const text = name?.trim() || String(userId || '?')
  return text.slice(-2)
}

export function splitTags(value: string): string[] {
  return [...new Set(value.split(/[,，、\s]+/).map((item) => item.trim()).filter(Boolean))]
}

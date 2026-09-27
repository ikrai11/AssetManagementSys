import http from './http'
import type { PageResult, Result, SiteMessage } from '@/types/api'

export function listMessages(params: { box?: string; page?: number; pageSize?: number }) {
  return http.get<Result<PageResult<SiteMessage>>>('/messages', { params })
}

export function unreadCount() {
  return http.get<Result<number>>('/messages/unread-count')
}

export function markMessagesRead(payload: { ids?: number[]; all?: boolean }) {
  return http.post<Result<null>>('/messages/read', payload)
}

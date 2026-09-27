import http from './http'
import type { MailRecord, PageResult, Result } from '@/types/api'

export function listMailRecords(params: { status?: string; page?: number; pageSize?: number }) {
  return http.get<Result<PageResult<MailRecord>>>('/mail-records', { params })
}

export function retryMail(id: number) {
  return http.post<Result<MailRecord>>(`/mail-records/${id}/retry`)
}

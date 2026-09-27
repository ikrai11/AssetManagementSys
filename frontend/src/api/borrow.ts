import http from './http'
import type { BorrowOrder, BorrowRenew, BorrowTodos, PageResult, Result } from '@/types/api'

export function createBorrow(payload: {
  assetId: number
  purpose?: string
  expectedReturnDate?: string
  remark?: string
  submit: boolean
}) {
  return http.post<Result<BorrowOrder>>('/borrows', payload)
}

export function listBorrows(params: { status?: string; page?: number; pageSize?: number }) {
  return http.get<Result<PageResult<BorrowOrder>>>('/borrows', { params })
}

export function getBorrow(id: number) {
  return http.get<Result<BorrowOrder>>(`/borrows/${id}`)
}

export function listTodos() {
  return http.get<Result<BorrowTodos>>('/borrows/todos')
}

export function submitBorrow(id: number) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/submit`)
}

export function withdrawBorrow(id: number) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/withdraw`)
}

export function approveBorrow(id: number) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/approve`)
}

export function rejectBorrow(id: number, comment: string) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/reject`, { comment })
}

export function issueBorrow(id: number) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/issue`)
}

export function applyRenew(id: number, payload: { newReturnDate: string; reason: string }) {
  return http.post<Result<BorrowRenew>>(`/borrows/${id}/renew`, payload)
}

export function approveRenew(renewId: number) {
  return http.post<Result<BorrowRenew>>(`/borrows/renews/${renewId}/approve`)
}

export function rejectRenew(renewId: number, comment: string) {
  return http.post<Result<BorrowRenew>>(`/borrows/renews/${renewId}/reject`, { comment })
}

export function requestReturn(id: number) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/return-request`)
}

export function confirmReturn(id: number, comment?: string) {
  return http.post<Result<BorrowOrder>>(`/borrows/${id}/return-confirm`, { comment })
}

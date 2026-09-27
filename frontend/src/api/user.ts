import http from './http'
import type { PageResult, Result, UserAccount, UserQuery, UserSavePayload } from '@/types/api'

export function listUsers(params: UserQuery) {
  return http.get<Result<PageResult<UserAccount>>>('/users', { params })
}

export function createUser(payload: UserSavePayload) {
  return http.post<Result<UserAccount>>('/users', payload)
}

export function updateUser(id: number, payload: UserSavePayload) {
  return http.put<Result<UserAccount>>(`/users/${id}`, payload)
}

export function resetUserPassword(id: number, password: string) {
  return http.post<Result<null>>(`/users/${id}/reset-password`, { password })
}

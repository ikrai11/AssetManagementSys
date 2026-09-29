import http from './http'
import type { LoginResult, Result, UserProfile } from '@/types/api'

export function login(username: string, password: string) {
  return http.post<Result<LoginResult>>('/auth/login', { username, password }, { skipErrorMessage: true })
}

export function logout() {
  return http.post<Result<null>>('/auth/logout')
}

export function getMe() {
  return http.get<Result<UserProfile>>('/auth/me')
}

export function changePassword(oldPassword: string, newPassword: string) {
  return http.put<Result<null>>('/auth/password', { oldPassword, newPassword })
}

export function updateProfile(realName: string, email: string) {
  return http.put<Result<UserProfile>>('/auth/profile', { realName, email })
}

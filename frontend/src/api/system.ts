import http from './http'
import type { Result, SysParams } from '@/types/api'

export function getSystemParams() {
  return http.get<Result<SysParams>>('/system-params')
}

export function updateSystemParams(payload: SysParams) {
  return http.put<Result<SysParams>>('/system-params', payload)
}

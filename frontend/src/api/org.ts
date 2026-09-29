import http from './http'
import type { OrgNode, OrgSavePayload, Result } from '@/types/api'

export function listOrgDepts() {
  return http.get<Result<OrgNode[]>>('/org/depts')
}

export function createOrgDept(payload: OrgSavePayload) {
  return http.post<Result<OrgNode>>('/org/depts', payload)
}

export function updateOrgDept(id: number, payload: OrgSavePayload) {
  return http.put<Result<OrgNode>>(`/org/depts/${id}`, payload)
}

export function removeOrgDept(id: number) {
  return http.delete<Result<null>>(`/org/depts/${id}`)
}

export function listOrgLocations() {
  return http.get<Result<OrgNode[]>>('/org/locations')
}

export function createOrgLocation(payload: OrgSavePayload) {
  return http.post<Result<OrgNode>>('/org/locations', payload)
}

export function updateOrgLocation(id: number, payload: OrgSavePayload) {
  return http.put<Result<OrgNode>>(`/org/locations/${id}`, payload)
}

export function removeOrgLocation(id: number) {
  return http.delete<Result<null>>(`/org/locations/${id}`)
}

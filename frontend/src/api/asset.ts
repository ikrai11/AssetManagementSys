import http from './http'
import type { AssetItem, AssetQuery, PageResult, Result } from '@/types/api'

export function listAssets(params: AssetQuery) {
  return http.get<Result<PageResult<AssetItem>>>('/assets', { params })
}

export function getAsset(id: number) {
  return http.get<Result<AssetItem>>(`/assets/${id}`)
}

export function createAsset(payload: Partial<AssetItem>) {
  return http.post<Result<AssetItem>>('/assets', payload)
}

export function updateAsset(id: number, payload: Partial<AssetItem>) {
  return http.put<Result<AssetItem>>(`/assets/${id}`, payload)
}

export function removeAsset(id: number) {
  return http.delete<Result<null>>(`/assets/${id}`)
}

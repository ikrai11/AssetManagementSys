import http from './http'
import type { Result } from '@/types/api'

export interface StocktakeItem {
  id: number
  assetId: number
  assetNo: string
  assetName: string
  assetStatus: string
  assetStatusLabel?: string
  deptName?: string
  locationName?: string
  result: string
  resultLabel?: string
  comment?: string
}

export interface StocktakeTask {
  id: number
  title: string
  scopeType: string
  scopeLabel?: string
  status: string
  statusLabel?: string
  total?: number
  pending?: number
  difference?: number
  createdAt?: string
  finishedAt?: string
  items?: StocktakeItem[]
}

export function listStocktakes() {
  return http.get<Result<StocktakeTask[]>>('/stocktakes')
}

export function createStocktake(payload: { scopeType: 'DEPT' | 'LOCATION'; deptId?: number; locationId?: number }) {
  return http.post<Result<StocktakeTask>>('/stocktakes', payload)
}

export function getStocktake(id: number) {
  return http.get<Result<StocktakeTask>>(`/stocktakes/${id}`)
}

export function markStocktake(id: number, itemId: number, payload: { result: string; comment?: string }) {
  return http.post<Result<StocktakeTask>>(`/stocktakes/${id}/items/${itemId}/mark`, payload)
}

export function finishStocktake(id: number) {
  return http.post<Result<StocktakeTask>>(`/stocktakes/${id}/finish`)
}

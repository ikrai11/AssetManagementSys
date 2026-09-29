import axios from 'axios'
import http from './http'
import type { AssetFile, AssetImportResult, AssetItem, AssetQuery, PageResult, Result } from '@/types/api'

function filenameFrom(disposition: string | undefined, fallback: string) {
  if (!disposition) return fallback
  const encodedName = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(disposition)?.[1]
  if (encodedName) {
    return decodeURIComponent(encodedName.replace(/"/g, ''))
  }
  const plainName = /filename="?([^"]+)"?/i.exec(disposition)?.[1]
  return plainName ? decodeURIComponent(plainName) : fallback
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

async function download(path: string, fallback: string, params?: AssetQuery) {
  try {
    const response = await http.get<Blob>(path, {
      params,
      responseType: 'blob',
      timeout: 60000,
      skipErrorMessage: true,
    })
    const blob = response.data
    if (blob.type.includes('application/json')) {
      const body = JSON.parse(await blob.text()) as Result<null>
      throw new Error(body.message || '下载失败')
    }
    saveBlob(blob, filenameFrom(response.headers['content-disposition'], fallback))
  } catch (error) {
    if (error instanceof Error && !(axios.isAxiosError(error))) {
      throw error
    }
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      const body = JSON.parse(await error.response.data.text()) as Result<null>
      throw new Error(body.message || '下载失败')
    }
    throw error
  }
}

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

export function downloadImportTemplate() {
  return download('/assets/import-template', '设备导入模板.xlsx')
}

export function downloadImportFailures() {
  return download('/assets/import-failures', '导入失败明细.xlsx')
}

export function exportAssets(params: AssetQuery) {
  return download('/assets/export', '设备台账.xlsx', params)
}

export function transferAsset(id: number, payload: { deptId?: number; locationId?: number; reason: string }) {
  return http.post<Result<null>>(`/assets/${id}/transfer`, payload)
}

export function startRepair(id: number, payload: { fault: string; sentDate?: string }) {
  return http.post<Result<null>>(`/assets/${id}/repair`, payload)
}

export function finishRepair(id: number, payload: { result: string; finishedDate?: string }) {
  return http.post<Result<null>>(`/assets/${id}/repair/finish`, payload)
}

export function scrapAsset(id: number, reason: string) {
  return http.post<Result<null>>(`/assets/${id}/scrap`, { reason })
}

export function listAssetFiles(assetId: number) {
  return http.get<Result<AssetFile[]>>(`/assets/${assetId}/files`)
}

export function uploadAssetFile(assetId: number, kind: string, file: File) {
  const form = new FormData()
  form.append('kind', kind)
  form.append('file', file)
  return http.post<Result<AssetFile>>(`/assets/${assetId}/files`, form)
}

export function deleteAssetFile(assetId: number, fileId: number) {
  return http.delete<Result<null>>(`/assets/${assetId}/files/${fileId}`)
}

export async function fetchAssetFile(assetId: number, fileId: number, filename: string) {
  const blob = await loadAssetFileBlob(assetId, fileId)
  saveBlob(blob, filename)
}

export async function loadAssetFileBlob(assetId: number, fileId: number) {
  try {
    const response = await http.get<Blob>(`/assets/${assetId}/files/${fileId}`, {
      responseType: 'blob',
      skipErrorMessage: true,
    })
    const blob = response.data
    if (blob.type.includes('application/json')) {
      const body = JSON.parse(await blob.text()) as Result<null>
      throw new Error(body.message || '下载失败')
    }
    return blob
  } catch (error) {
    if (error instanceof Error && !(axios.isAxiosError(error))) {
      throw error
    }
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      const body = JSON.parse(await error.response.data.text()) as Result<null>
      throw new Error(body.message || '下载失败')
    }
    throw error
  }
}

export function importAssets(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<Result<AssetImportResult>>('/assets/import', form, {
    timeout: 60000,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

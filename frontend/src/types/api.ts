export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface DictItem {
  id: number
  name: string
}

export interface LoginResult {
  token: string
  role: 'ADMIN' | 'USER'
  realName: string
  mustChangePassword: boolean
}

export interface UserProfile {
  id: number
  username: string
  realName: string
  email?: string
  mobile?: string
  deptId?: number
  role: 'ADMIN' | 'USER'
  mustChangePassword: boolean
}

export interface AssetItem {
  id: number
  assetNo: string
  name: string
  categoryId: number
  categoryName?: string
  brand?: string
  model?: string
  serialNo?: string
  status: string
  statusLabel?: string
  purchaseDate?: string
  purchasePrice?: number
  supplier?: string
  warrantyUntil?: string
  deptId?: number
  deptName?: string
  locationId?: number
  locationName?: string
  holderUserId?: number
  holderName?: string
  borrowStartDate?: string
  expectedReturnDate?: string
  currentBorrowId?: number
  remark?: string
  version?: number
  overdue?: boolean
  updatedAt?: string
  logs?: BorrowLog[]
}

export interface BorrowLog {
  id: number
  action: string
  operatorId: number
  operatorName?: string
  comment?: string
  createdAt?: string
}

export interface BorrowOrder {
  id: number
  orderNo: string
  assetId: number
  assetNo?: string
  assetName?: string
  applicantId: number
  applicantName?: string
  purpose?: string
  expectedReturnDate?: string
  remark?: string
  status: string
  statusLabel?: string
  overdue?: boolean
  approveComment?: string
  issuedAt?: string
  returnRequestedAt?: string
  returnedAt?: string
  returnComment?: string
  createdAt?: string
  logs?: BorrowLog[]
}

export interface BorrowTodos {
  pending: BorrowOrder[]
  approved: BorrowOrder[]
  returnPending: BorrowOrder[]
}

export interface AssetQuery {
  categoryIds?: number[]
  statuses?: string[]
  keyword?: string
  deptId?: number
  locationId?: number
  borrowStartFrom?: string
  borrowStartTo?: string
  dueFrom?: string
  dueTo?: string
  page?: number
  pageSize?: number
}

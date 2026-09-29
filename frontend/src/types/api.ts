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
  userId: number
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
  deptName?: string
  role: 'ADMIN' | 'USER'
  roleLabel?: string
  enabled?: boolean
  mustChangePassword: boolean
  createdAt?: string
}

export interface UserAccount extends UserProfile {
  enabled: boolean
}

export interface UserQuery {
  keyword?: string
  role?: string
  deptId?: number
  enabled?: boolean
  page?: number
  pageSize?: number
}

export interface StatsOverview {
  total: number
  inStock: number
  borrowed: number
  pending: number
  repairing: number
  scrapped: number
  dueSoon: number
  overdue: number
  pendingApproval: number
  myApplying: number
  myUsing: number
  myDueSoon: number
  myOverdue: number
}

export interface StatsNameCount {
  id: number
  name: string
  count: number
}

export interface StatsTrend {
  month: string
  count: number
}

export interface UserSavePayload {
  username?: string
  realName: string
  email?: string
  mobile?: string
  deptId?: number
  role: 'ADMIN' | 'USER'
  password?: string
  enabled?: boolean
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
  lifecycleLogs?: AssetLog[]
  repairSentDate?: string
  repairFault?: string
}

export interface AssetLog {
  id: number
  action: string
  operatorId?: number
  operatorName?: string
  comment?: string
  createdAt?: string
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
  pendingRenewId?: number
  pendingRenewDate?: string
  pendingRenewReason?: string
  logs?: BorrowLog[]
}

export interface BorrowRenew {
  id: number
  borrowId: number
  orderNo?: string
  assetNo?: string
  assetName?: string
  applicantName?: string
  oldReturnDate?: string
  newReturnDate?: string
  reason?: string
  status: string
  statusLabel?: string
  approveComment?: string
}

export interface BorrowTodos {
  pending: BorrowOrder[]
  approved: BorrowOrder[]
  returnPending: BorrowOrder[]
  renewPending: BorrowRenew[]
}

export interface SysParams {
  remindLeadDays: string
  borrowMaxDays: number
  renewMaxDaysFromIssue: number
  mailChannelEnabled: boolean
  loginMaxFailures: number
  loginLockMinutes: number
}

export interface AssetImportFail {
  rowNum: number
  assetNo?: string
  reason: string
}

export interface AssetImportResult {
  successCount: number
  failCount: number
  failures: AssetImportFail[]
}

export interface SiteMessage {
  id: number
  title: string
  content: string
  msgType: string
  msgTypeLabel?: string
  borrowId?: number
  read: boolean
  createdAt?: string
}

export interface MailRecord {
  id: number
  receiverId: number
  receiverName?: string
  email?: string
  subject: string
  content: string
  borrowId?: number
  status: string
  statusLabel?: string
  failReason?: string
  retryCount: number
  sentAt?: string
  createdAt?: string
}

export interface OrgNode {
  id: number
  name: string
  parentId?: number
  parentName?: string
  enabled: boolean
  referenced: boolean
  hasChildren: boolean
}

export interface OrgSavePayload {
  name: string
  parentId?: number | null
  enabled: boolean
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

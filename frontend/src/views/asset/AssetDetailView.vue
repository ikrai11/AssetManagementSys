<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteAssetFile, fetchAssetFile, finishRepair, getAsset, listAssetFiles, loadAssetFileBlob, scrapAsset, startRepair, transferAsset, uploadAssetFile } from '@/api/asset'
import { listDepts, listLocations } from '@/api/dict'
import { withCurrentOption } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'
import { actionLabel, assetTagClass, assetTagType } from '@/utils/status'
import type { AssetFile, AssetItem, DictItem } from '@/types/api'
import type { UploadRequestOptions } from 'element-plus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const acting = ref(false)
const asset = ref<AssetItem>()
const depts = ref<DictItem[]>([])
const locations = ref<DictItem[]>([])
const isAdmin = computed(() => auth.role === 'ADMIN')
const scrapped = computed(() => asset.value?.status === 'SCRAPPED')
const files = ref<AssetFile[]>([])
const previews = ref<Record<number, string>>({})
const fileKind = ref('PHOTO')
const uploading = ref(false)
const photos = computed(() => files.value.filter((item) => item.kind === 'PHOTO'))
const invoices = computed(() => files.value.filter((item) => item.kind === 'INVOICE'))

const transferOpen = ref(false)
const transferReason = ref('')
const transferDeptId = ref<number>()
const transferLocationId = ref<number>()

const repairOpen = ref(false)
const repairFault = ref('')
const repairSentDate = ref('')

const finishOpen = ref(false)
const finishResult = ref('')
const finishDate = ref('')

function today() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAsset(Number(route.params.id))
    asset.value = data.data
    await loadFiles()
  } finally {
    loading.value = false
  }
}

function revokePreviews() {
  Object.values(previews.value).forEach((url) => URL.revokeObjectURL(url))
  previews.value = {}
}

async function loadFiles() {
  if (!asset.value) return
  revokePreviews()
  const { data } = await listAssetFiles(asset.value.id)
  files.value = data.data
  const images = files.value.filter((item) => item.contentType.startsWith('image/'))
  await Promise.all(images.map(async (item) => {
    try {
      const blob = await loadAssetFileBlob(asset.value!.id, item.id)
      previews.value[item.id] = URL.createObjectURL(blob)
    } catch {
      // 预览失败时仍保留文件名，可手动下载
    }
  }))
}

async function uploadRequest(options: UploadRequestOptions) {
  if (!asset.value) return
  uploading.value = true
  try {
    await uploadAssetFile(asset.value.id, fileKind.value, options.file)
    ElMessage.success(fileKind.value === 'INVOICE' ? '票据已上传' : '照片已上传')
    await loadFiles()
  } finally {
    uploading.value = false
  }
}

async function removeFile(file: AssetFile) {
  if (!asset.value) return
  try {
    await ElMessageBox.confirm(`删除后不能恢复。确认删除「${file.originalName}」？`, '删除附件', { type: 'warning' })
  } catch {
    return
  }
  await deleteAssetFile(asset.value.id, file.id)
  ElMessage.success('已删除')
  await loadFiles()
}

function isImage(file: AssetFile) {
  return file.contentType.startsWith('image/')
}

async function saveFile(file: AssetFile) {
  if (!asset.value) return
  try {
    await fetchAssetFile(asset.value.id, file.id, file.originalName)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败')
  }
}

function openTransfer() {
  depts.value = withCurrentOption(depts.value, asset.value?.deptId, asset.value?.deptName)
  locations.value = withCurrentOption(locations.value, asset.value?.locationId, asset.value?.locationName)
  transferDeptId.value = asset.value?.deptId
  transferLocationId.value = asset.value?.locationId
  transferReason.value = ''
  transferOpen.value = true
}

function openRepair() {
  repairFault.value = ''
  repairSentDate.value = today()
  repairOpen.value = true
}

function openFinish() {
  finishResult.value = ''
  finishDate.value = today()
  finishOpen.value = true
}

async function submitTransfer() {
  if (!asset.value) return
  if (!transferReason.value.trim()) {
    ElMessage.warning('请填写调拨原因')
    return
  }
  acting.value = true
  try {
    await transferAsset(asset.value.id, {
      deptId: transferDeptId.value,
      locationId: transferLocationId.value,
      reason: transferReason.value.trim(),
    })
    transferOpen.value = false
    ElMessage.success('已调拨')
    await load()
  } finally {
    acting.value = false
  }
}

async function submitRepair() {
  if (!asset.value) return
  if (!repairFault.value.trim()) {
    ElMessage.warning('请填写故障说明')
    return
  }
  acting.value = true
  try {
    await startRepair(asset.value.id, {
      fault: repairFault.value.trim(),
      sentDate: repairSentDate.value || undefined,
    })
    repairOpen.value = false
    ElMessage.success('已送修')
    await load()
  } finally {
    acting.value = false
  }
}

async function submitFinish() {
  if (!asset.value) return
  if (!finishResult.value.trim()) {
    ElMessage.warning('请填写维修结果')
    return
  }
  acting.value = true
  try {
    await finishRepair(asset.value.id, {
      result: finishResult.value.trim(),
      finishedDate: finishDate.value || undefined,
    })
    finishOpen.value = false
    ElMessage.success('维修已完成，设备回到在库')
    await load()
  } finally {
    acting.value = false
  }
}

async function scrap() {
  if (!asset.value) return
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      '报废后设备只读，不能再领用、调拨或修改，历史领用记录会保留。请填写报废原因。',
      '确认报废',
      {
        inputPattern: /\S+/,
        inputErrorMessage: '报废必须填写原因',
        confirmButtonText: '确认报废',
        type: 'warning',
      },
    )
    reason = result.value.trim()
  } catch {
    return
  }
  acting.value = true
  try {
    await scrapAsset(asset.value.id, reason)
    ElMessage.success('已报废')
    await load()
  } finally {
    acting.value = false
  }
}

onUnmounted(revokePreviews)

onMounted(async () => {
  if (isAdmin.value) {
    const [deptRes, locationRes] = await Promise.all([listDepts(), listLocations()])
    depts.value = deptRes.data.data
    locations.value = locationRes.data.data
  }
  await load()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel" v-if="asset">
      <el-page-header content="设备详情" @back="router.back()" />
      <div class="detail-head">
        <div>
          <h2>{{ asset.name }}</h2>
          <p>{{ asset.assetNo }}</p>
        </div>
        <el-tag :type="assetTagType(asset.status, asset.overdue)" :class="assetTagClass(asset.status)">
          {{ asset.overdue ? '已逾期' : asset.statusLabel }}
        </el-tag>
      </div>
      <div class="detail-actions">
        <el-button v-if="isAdmin && !scrapped" :disabled="acting" @click="router.push({ name: 'asset-edit', params: { id: asset.id } })">
          编辑
        </el-button>
        <el-button v-if="isAdmin && !scrapped" :disabled="acting" @click="openTransfer">调拨</el-button>
        <el-button v-if="isAdmin && asset.status === 'IN_STOCK'" :disabled="acting" @click="openRepair">送修</el-button>
        <el-button v-if="isAdmin && asset.status === 'REPAIRING'" type="primary" :disabled="acting" @click="openFinish">
          维修完成
        </el-button>
        <el-button
          v-if="isAdmin && (asset.status === 'IN_STOCK' || asset.status === 'REPAIRING')"
          type="danger"
          :disabled="acting"
          @click="scrap"
        >
          报废
        </el-button>
        <el-button
          v-if="asset.status === 'IN_STOCK'"
          type="primary"
          @click="router.push({ name: 'borrow-apply', query: { assetId: asset.id } })"
        >
          申请领用
        </el-button>
        <el-button
          v-if="!isAdmin && asset.status === 'BORROWED' && asset.currentBorrowId"
          type="primary"
          @click="router.push({ name: 'borrow-detail', params: { id: asset.currentBorrowId } })"
        >
          申请归还
        </el-button>
        <el-button
          v-if="isAdmin && asset.currentBorrowId"
          type="primary"
          @click="router.push({ name: 'borrow-detail', params: { id: asset.currentBorrowId } })"
        >
          查看领用单
        </el-button>
      </div>
      <el-alert
        v-if="asset.status === 'REPAIRING'"
        type="warning"
        :closable="false"
        show-icon
        :title="`维修中：${asset.repairSentDate || ''} ${asset.repairFault || ''}`"
      />
      <el-alert
        v-if="scrapped"
        type="info"
        :closable="false"
        show-icon
        title="已报废设备只读，历史领用记录仍保留。"
      />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="类型">{{ asset.categoryName }}</el-descriptions-item>
        <el-descriptions-item label="品牌 / 型号">{{ asset.brand }} {{ asset.model }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ asset.serialNo }}</el-descriptions-item>
        <el-descriptions-item label="存放地点">{{ asset.locationName }}</el-descriptions-item>
        <el-descriptions-item label="责任部门">{{ asset.deptName }}</el-descriptions-item>
        <el-descriptions-item label="领用人">{{ asset.holderName }}</el-descriptions-item>
        <el-descriptions-item label="领用时间">{{ asset.borrowStartDate }}</el-descriptions-item>
        <el-descriptions-item label="预计归还日">{{ asset.expectedReturnDate }}</el-descriptions-item>
        <el-descriptions-item v-if="isAdmin" label="购置价格">{{ asset.purchasePrice }}</el-descriptions-item>
        <el-descriptions-item v-if="isAdmin" label="供应商">{{ asset.supplier }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ asset.remark }}</el-descriptions-item>
      </el-descriptions>
      <h3 class="section-title">照片与票据</h3>
      <div v-if="isAdmin && !scrapped" class="file-upload">
        <el-radio-group v-model="fileKind">
          <el-radio value="PHOTO">照片</el-radio>
          <el-radio value="INVOICE">票据</el-radio>
        </el-radio-group>
        <el-upload :show-file-list="false" :http-request="uploadRequest" accept=".jpg,.jpeg,.png,.gif,.webp,.pdf">
          <el-button :loading="uploading">上传</el-button>
        </el-upload>
        <span class="file-hint">图片或 pdf，单个不超过 10MB。普通用户只能看到照片。</span>
      </div>
      <div v-if="photos.length" class="file-grid">
        <div v-for="file in photos" :key="file.id" class="file-card">
          <img v-if="isImage(file) && previews[file.id]" :src="previews[file.id]" :alt="file.originalName" />
          <button type="button" class="file-name" @click="saveFile(file)">
            {{ file.originalName }}
          </button>
          <el-button v-if="isAdmin && !scrapped" link type="danger" @click="removeFile(file)">删除</el-button>
        </div>
      </div>
      <el-empty v-else description="暂无照片" />
      <template v-if="isAdmin">
        <h3 class="section-title">票据</h3>
        <div v-if="invoices.length" class="file-list">
          <div v-for="file in invoices" :key="file.id" class="file-row">
            <button type="button" class="file-name" @click="saveFile(file)">
              {{ file.originalName }}
            </button>
            <el-button v-if="!scrapped" link type="danger" @click="removeFile(file)">删除</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无票据" />
      </template>
      <h3 class="section-title">设备履历</h3>
      <el-timeline v-if="asset.lifecycleLogs?.length">
        <el-timeline-item v-for="item in asset.lifecycleLogs" :key="item.id" :timestamp="item.createdAt">
          {{ actionLabel[item.action] || item.action }} · {{ item.operatorName }}
          <span v-if="item.comment">：{{ item.comment }}</span>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无调拨、维修或报废记录" />
      <h3 class="section-title">领用履历</h3>
      <el-timeline v-if="asset.logs?.length">
        <el-timeline-item v-for="item in asset.logs" :key="item.id" :timestamp="item.createdAt">
          {{ actionLabel[item.action] || item.action }} · {{ item.operatorName }}
          <span v-if="item.comment">：{{ item.comment }}</span>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无领用记录" />
    </div>

    <el-dialog v-model="transferOpen" title="调拨" width="480px">
      <el-form label-width="90px">
        <el-form-item label="责任部门">
          <el-select v-model="transferDeptId" placeholder="选择部门" style="width: 100%">
            <el-option v-for="item in depts" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放地点">
          <el-select v-model="transferLocationId" placeholder="选择地点" style="width: 100%">
            <el-option v-for="item in locations" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="transferReason" type="textarea" maxlength="200" show-word-limit placeholder="请填写调拨原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferOpen = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitTransfer">确认调拨</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="repairOpen" title="送修" width="480px">
      <p class="dialog-hint">仅在库设备可送修。已领用设备请先归还。</p>
      <el-form label-width="90px">
        <el-form-item label="送修日">
          <el-date-picker v-model="repairSentDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" />
        </el-form-item>
        <el-form-item label="故障" required>
          <el-input v-model="repairFault" type="textarea" maxlength="200" show-word-limit placeholder="请填写故障说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repairOpen = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitRepair">确认送修</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finishOpen" title="维修完成" width="480px">
      <p v-if="asset?.repairFault" class="dialog-hint">故障：{{ asset.repairFault }}，送修日 {{ asset.repairSentDate }}</p>
      <el-form label-width="90px">
        <el-form-item label="完成日">
          <el-date-picker v-model="finishDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" />
        </el-form-item>
        <el-form-item label="结果" required>
          <el-input v-model="finishResult" type="textarea" maxlength="200" show-word-limit placeholder="请填写维修结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishOpen = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitFinish">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-head {
  margin-top: 20px;
}
.section-title {
  margin: 24px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
.dialog-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
}
.el-alert {
  margin-bottom: 16px;
}
.file-upload {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.file-hint {
  color: var(--ams-text-secondary);
  font-size: 13px;
}
.file-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.file-card {
  width: 160px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.file-card img {
  width: 160px;
  height: 110px;
  object-fit: cover;
  border-radius: 4px;
  background: #f5f7fa;
}
.file-name {
  padding: 0;
  border: none;
  background: none;
  color: var(--el-color-primary);
  cursor: pointer;
  text-align: left;
  word-break: break-all;
}
.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.file-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>

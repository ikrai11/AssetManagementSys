<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applyRenew, listBorrows, requestReturn, withdrawBorrow } from '@/api/borrow'
import { useAuthStore } from '@/stores/auth'
import { useMessageStore } from '@/stores/message'
import { borrowTagType } from '@/utils/status'
import type { BorrowOrder } from '@/types/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const messages = useMessageStore()
const loading = ref(false)
const tab = ref(typeof route.query.tab === 'string' ? route.query.tab : 'active')
const all = ref<BorrowOrder[]>([])
const renewOpen = ref(false)
const renewTarget = ref<BorrowOrder>()
const renewDate = ref('')
const renewReason = ref('')

const groups = computed(() => ({
  active: all.value.filter((item) => ['DRAFT', 'PENDING', 'APPROVED'].includes(item.status)),
  using: all.value.filter((item) => ['BORROWING', 'RETURN_PENDING'].includes(item.status)),
  done: all.value.filter((item) => ['RETURNED', 'REJECTED', 'WITHDRAWN'].includes(item.status)),
}))

const current = computed(() => groups.value[tab.value as keyof typeof groups.value] ?? [])

async function load() {
  loading.value = true
  try {
    const { data } = await listBorrows({ page: 1, pageSize: 100 })
    all.value = data.data.list
  } finally {
    loading.value = false
  }
}

async function withdraw(row: BorrowOrder) {
  await withdrawBorrow(row.id)
  await load()
  await messages.refresh()
}

async function applyReturn(row: BorrowOrder) {
  await requestReturn(row.id)
  await load()
  await messages.refresh()
}

function canRenew(row: BorrowOrder) {
  return auth.role !== 'ADMIN' && row.status === 'BORROWING' && !row.pendingRenewId
}

function openRenew(row: BorrowOrder) {
  renewTarget.value = row
  renewDate.value = ''
  renewReason.value = ''
  renewOpen.value = true
}

async function submitRenew() {
  if (!renewTarget.value || !renewDate.value || !renewReason.value.trim()) {
    ElMessage.warning('请填写新预计归还日和理由')
    return
  }
  await applyRenew(renewTarget.value.id, {
    newReturnDate: renewDate.value,
    reason: renewReason.value.trim(),
  })
  ElMessage.success('已提交续借')
  renewOpen.value = false
  await load()
  await messages.refresh()
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="table-panel">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`申请中 (${groups.active.length})`" name="active" />
        <el-tab-pane :label="`使用中 (${groups.using.length})`" name="using" />
        <el-tab-pane :label="`已完成 (${groups.done.length})`" name="done" />
      </el-tabs>
      <el-table :data="current" stripe>
        <el-table-column prop="orderNo" label="单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="assetNo" label="资产编号" min-width="110" show-overflow-tooltip />
        <el-table-column prop="assetName" label="设备" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="borrowTagType(row.status, row.overdue)">{{ row.overdue ? '已逾期' : row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expectedReturnDate" label="预计归还日" width="112" />
        <el-table-column v-if="tab === 'using'" label="天数" width="112">
          <template #default="{ row }">
            <span v-if="row.overdueDays != null">逾期 {{ row.overdueDays }} 天</span>
            <span v-else-if="row.remainingDays != null">剩余 {{ row.remainingDays }} 天</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="248" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'borrow-detail', params: { id: row.id } })">
              详情
            </el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" @click="withdraw(row)">撤回</el-button>
            <el-button v-if="canRenew(row)" link type="primary" @click="openRenew(row)">申请续借</el-button>
            <el-button v-if="row.status === 'BORROWING' && !row.pendingRenewId" link type="primary" @click="applyReturn(row)">
              申请归还
            </el-button>
            <span v-if="row.pendingRenewId">续借待审批</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="renewOpen" title="申请续借" width="420px">
      <el-form label-width="110px">
        <el-form-item label="新预计归还日">
          <el-date-picker v-model="renewDate" type="date" value-format="YYYY-MM-DD" placeholder="须晚于原日期" />
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="renewReason" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewOpen = false">取消</el-button>
        <el-button type="primary" @click="submitRenew">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

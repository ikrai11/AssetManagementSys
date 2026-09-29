<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveBorrow, approveRenew, confirmReturn, issueBorrow, listTodos, rejectBorrow, rejectRenew } from '@/api/borrow'
import { useMessageStore } from '@/stores/message'
import { borrowTagType } from '@/utils/status'
import type { BorrowOrder, BorrowRenew, BorrowTodos } from '@/types/api'

const router = useRouter()
const messages = useMessageStore()
const loading = ref(false)
const tab = ref('pending')
const todos = ref<BorrowTodos>({ pending: [], approved: [], returnPending: [], renewPending: [] })
const current = computed(() => {
  if (tab.value === 'renew') return []
  return todos.value[tab.value as 'pending' | 'approved' | 'returnPending'] ?? []
})

async function load() {
  loading.value = true
  try {
    const { data } = await listTodos()
    todos.value = data.data
  } finally {
    loading.value = false
  }
}

async function finish() {
  await load()
  await messages.refresh()
}

async function approve(row: BorrowOrder) {
  await approveBorrow(row.id)
  ElMessage.success('已通过，设备仍为审批中，待发放')
  await finish()
}

async function reject(row: BorrowOrder) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回申请', {
    inputPattern: /\S+/,
    inputErrorMessage: '驳回必须填写原因',
  })
  await rejectBorrow(row.id, value)
  ElMessage.success('已驳回')
  await finish()
}

async function issue(row: BorrowOrder) {
  await ElMessageBox.confirm(`确认将 ${row.assetNo} 发放给 ${row.applicantName}？发放后设备变为已领用。`, '确认发放')
  await issueBorrow(row.id)
  ElMessage.success('已发放')
  await finish()
}

async function confirm(row: BorrowOrder) {
  const { value } = await ElMessageBox.prompt('可填写验收说明', '确认归还', {
    inputPlaceholder: '外观与附件情况，可选',
  }).catch(() => ({ value: '' }))
  await confirmReturn(row.id, value)
  ElMessage.success('已归还，设备回到在库')
  await finish()
}

async function passRenew(row: BorrowRenew) {
  await approveRenew(row.id)
  ElMessage.success('续借已通过，预计归还日已更新')
  await finish()
}

async function denyRenew(row: BorrowRenew) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回续借', {
    inputPattern: /\S+/,
    inputErrorMessage: '驳回必须填写原因',
  })
  await rejectRenew(row.id, value)
  ElMessage.success('已驳回，原预计归还日不变')
  await finish()
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="table-panel">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`待审批 (${todos.pending.length})`" name="pending" />
        <el-tab-pane :label="`待发放 (${todos.approved.length})`" name="approved" />
        <el-tab-pane :label="`待归还 (${todos.returnPending.length})`" name="returnPending" />
        <el-tab-pane :label="`待续借 (${todos.renewPending.length})`" name="renew" />
      </el-tabs>
      <el-table v-if="tab === 'renew'" :data="todos.renewPending" stripe>
        <el-table-column prop="orderNo" label="单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="88" />
        <el-table-column prop="assetNo" label="资产编号" min-width="110" show-overflow-tooltip />
        <el-table-column prop="assetName" label="设备" min-width="120" show-overflow-tooltip />
        <el-table-column prop="oldReturnDate" label="原归还日" width="112" />
        <el-table-column prop="newReturnDate" label="新归还日" width="112" />
        <el-table-column prop="reason" label="理由" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'borrow-detail', params: { id: row.borrowId } })">详情</el-button>
            <el-button link type="success" @click="passRenew(row)">通过</el-button>
            <el-button link type="danger" @click="denyRenew(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-table v-else :data="current" stripe>
        <el-table-column prop="orderNo" label="单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="88" />
        <el-table-column prop="assetNo" label="资产编号" min-width="110" show-overflow-tooltip />
        <el-table-column prop="assetName" label="设备" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="borrowTagType(row.status, row.overdue)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expectedReturnDate" label="预计归还日" width="112" />
        <el-table-column label="操作" width="176" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'borrow-detail', params: { id: row.id } })">
              详情
            </el-button>
            <template v-if="tab === 'pending'">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <el-button v-if="tab === 'approved'" link type="primary" @click="issue(row)">确认发放</el-button>
            <el-button v-if="tab === 'returnPending'" link type="primary" @click="confirm(row)">确认归还</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

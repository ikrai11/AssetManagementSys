<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applyRenew, approveBorrow, approveRenew, confirmReturn, getBorrow, issueBorrow, rejectBorrow, rejectRenew, requestReturn, submitBorrow, withdrawBorrow } from '@/api/borrow'
import { useAuthStore } from '@/stores/auth'
import { actionLabel, borrowTagType } from '@/utils/status'
import type { BorrowOrder } from '@/types/api'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const order = ref<BorrowOrder>()
const renewOpen = ref(false)
const renewDate = ref('')
const renewReason = ref('')
const isAdmin = computed(() => auth.role === 'ADMIN')
const canRenew = computed(() => !isAdmin.value && order.value?.status === 'BORROWING' && !order.value.pendingRenewId)

async function load() {
  loading.value = true
  try {
    const { data } = await getBorrow(Number(route.params.id))
    order.value = data.data
  } finally {
    loading.value = false
  }
}

async function run(action: () => Promise<unknown>, success: string) {
  await action()
  ElMessage.success(success)
  await load()
}

async function reject() {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回申请', {
    inputPattern: /\S+/,
    inputErrorMessage: '驳回必须填写原因',
  })
  await run(() => rejectBorrow(order.value!.id, value), '已驳回')
}

async function confirm() {
  const { value } = await ElMessageBox.prompt('可填写验收说明', '确认归还', {
    inputPlaceholder: '外观与附件情况，可选',
  }).catch(() => ({ value: '' }))
  await run(() => confirmReturn(order.value!.id, value), '已归还')
}

function openRenew() {
  renewDate.value = ''
  renewReason.value = ''
  renewOpen.value = true
}

async function submitRenew() {
  if (!order.value || !renewDate.value || !renewReason.value.trim()) {
    ElMessage.warning('请填写新预计归还日和理由')
    return
  }
  await run(() => applyRenew(order.value!.id, { newReturnDate: renewDate.value, reason: renewReason.value.trim() }), '已提交续借')
  renewOpen.value = false
}

async function passRenew() {
  if (!order.value?.pendingRenewId) return
  await run(() => approveRenew(order.value!.pendingRenewId!), '续借已通过')
}

async function denyRenew() {
  if (!order.value?.pendingRenewId) return
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回续借', {
    inputPattern: /\S+/,
    inputErrorMessage: '驳回必须填写原因',
  })
  await run(() => rejectRenew(order.value!.pendingRenewId!, value), '已驳回续借')
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel" v-if="order">
      <el-page-header content="领用详情" @back="$router.back()" />
      <div class="detail-head">
        <div>
          <h2>{{ order.orderNo }}</h2>
          <p>{{ order.assetNo }} {{ order.assetName }}</p>
        </div>
        <el-tag :type="borrowTagType(order.status, order.overdue)">
          {{ order.overdue ? '已逾期' : order.statusLabel }}
        </el-tag>
      </div>
      <div class="detail-actions">
        <el-button v-if="order.status === 'DRAFT'" type="primary" @click="run(() => submitBorrow(order!.id), '已提交')">提交</el-button>
        <el-button v-if="order.status === 'PENDING' && !isAdmin" @click="run(() => withdrawBorrow(order!.id), '已撤回')">撤回</el-button>
        <el-button v-if="isAdmin && order.status === 'PENDING'" type="primary" @click="run(() => approveBorrow(order!.id), '已通过')">通过</el-button>
        <el-button v-if="isAdmin && order.status === 'PENDING'" type="danger" @click="reject">驳回</el-button>
        <el-button v-if="isAdmin && order.status === 'APPROVED'" type="primary" @click="run(() => issueBorrow(order!.id), '已发放')">确认发放</el-button>
        <el-button v-if="canRenew" type="primary" @click="openRenew">申请续借</el-button>
        <el-button v-if="!isAdmin && order.status === 'BORROWING' && !order.pendingRenewId" @click="run(() => requestReturn(order!.id), '已申请归还')">申请归还</el-button>
        <el-button v-if="isAdmin && order.pendingRenewId" type="primary" @click="passRenew">通过续借</el-button>
        <el-button v-if="isAdmin && order.pendingRenewId" type="danger" @click="denyRenew">驳回续借</el-button>
        <el-button v-if="isAdmin && !order.pendingRenewId && (order.status === 'BORROWING' || order.status === 'RETURN_PENDING')" type="primary" @click="confirm">确认归还</el-button>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请人">{{ order.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="用途">{{ order.purpose }}</el-descriptions-item>
        <el-descriptions-item label="预计归还日">{{ order.expectedReturnDate }}</el-descriptions-item>
        <el-descriptions-item v-if="order.pendingRenewId" label="待审批续借">
          {{ order.pendingRenewDate }}，{{ order.pendingRenewReason }}
        </el-descriptions-item>
        <el-descriptions-item label="发放时间">{{ order.issuedAt }}</el-descriptions-item>
        <el-descriptions-item label="驳回原因">{{ order.approveComment }}</el-descriptions-item>
        <el-descriptions-item label="归还说明">{{ order.returnComment }}</el-descriptions-item>
        <el-descriptions-item label="领用说明" :span="2">{{ order.remark }}</el-descriptions-item>
      </el-descriptions>
      <h3 class="section-title">办理记录</h3>
      <el-timeline v-if="order.logs?.length">
        <el-timeline-item v-for="item in order.logs" :key="item.id" :timestamp="item.createdAt">
          {{ actionLabel[item.action] || item.action }} · {{ item.operatorName }}
          <span v-if="item.comment">：{{ item.comment }}</span>
        </el-timeline-item>
      </el-timeline>
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

<style scoped>
.detail-head {
  margin-top: 20px;
}
.section-title {
  margin: 24px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
</style>

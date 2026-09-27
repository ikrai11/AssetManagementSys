<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveBorrow, confirmReturn, getBorrow, issueBorrow, rejectBorrow, requestReturn, submitBorrow, withdrawBorrow } from '@/api/borrow'
import { useAuthStore } from '@/stores/auth'
import { actionLabel, borrowTagType } from '@/utils/status'
import type { BorrowOrder } from '@/types/api'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const order = ref<BorrowOrder>()
const isAdmin = computed(() => auth.role === 'ADMIN')

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
        <el-button v-if="order.status === 'DRAFT'" type="primary" @click="run(() => submitBorrow(order.id), '已提交')">提交</el-button>
        <el-button v-if="order.status === 'PENDING' && !isAdmin" @click="run(() => withdrawBorrow(order.id), '已撤回')">撤回</el-button>
        <el-button v-if="isAdmin && order.status === 'PENDING'" type="primary" @click="run(() => approveBorrow(order.id), '已通过')">通过</el-button>
        <el-button v-if="isAdmin && order.status === 'PENDING'" type="danger" @click="reject">驳回</el-button>
        <el-button v-if="isAdmin && order.status === 'APPROVED'" type="primary" @click="run(() => issueBorrow(order.id), '已发放')">确认发放</el-button>
        <el-button v-if="!isAdmin && order.status === 'BORROWING'" type="primary" @click="run(() => requestReturn(order.id), '已申请归还')">申请归还</el-button>
        <el-button v-if="isAdmin && (order.status === 'BORROWING' || order.status === 'RETURN_PENDING')" type="primary" @click="confirm">确认归还</el-button>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请人">{{ order.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="用途">{{ order.purpose }}</el-descriptions-item>
        <el-descriptions-item label="预计归还日">{{ order.expectedReturnDate }}</el-descriptions-item>
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

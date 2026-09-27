<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listBorrows, requestReturn, withdrawBorrow } from '@/api/borrow'
import { borrowTagType } from '@/utils/status'
import type { BorrowOrder } from '@/types/api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const tab = ref(typeof route.query.tab === 'string' ? route.query.tab : 'active')
const all = ref<BorrowOrder[]>([])

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
}

async function applyReturn(row: BorrowOrder) {
  await requestReturn(row.id)
  await load()
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
        <el-table-column label="操作" width="148" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'borrow-detail', params: { id: row.id } })">
              详情
            </el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" @click="withdraw(row)">撤回</el-button>
            <el-button v-if="row.status === 'BORROWING'" link type="primary" @click="applyReturn(row)">申请归还</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

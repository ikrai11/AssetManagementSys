<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listMailRecords, retryMail } from '@/api/mail'
import type { MailRecord } from '@/types/api'

const loading = ref(false)
const retryingId = ref<number | null>(null)
const list = ref<MailRecord[]>([])
const total = ref(0)
const page = ref(1)
const status = ref('')

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function tagType(value: string): 'success' | 'danger' | 'warning' | 'info' {
  if (value === 'SENT') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'SKIPPED') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const { data } = await listMailRecords({
      status: status.value || undefined,
      page: page.value,
      pageSize: 20,
    })
    list.value = data.data.list
    total.value = data.data.total
  } finally {
    loading.value = false
  }
}

async function retry(row: MailRecord) {
  retryingId.value = row.id
  try {
    const { data } = await retryMail(row.id)
    const index = list.value.findIndex((item) => item.id === row.id)
    if (index >= 0) list.value[index] = data.data
    ElMessage.success(data.data.status === 'SENT' ? '已发送' : data.data.failReason || '未发送')
  } finally {
    retryingId.value = null
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-panel">
      <p class="hint">邮件通道未配置时，记录显示为未发送并写明原因。站内信不受影响，仍可在消息中心查看。</p>
      <el-form inline class="search-form" @submit.prevent="search">
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部" @change="search">
            <el-option label="未发送" value="SKIPPED" />
            <el-option label="发送失败" value="FAILED" />
            <el-option label="已发送" value="SENT" />
            <el-option label="待发送" value="PENDING" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-panel">
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无数据">
        <el-table-column prop="createdAt" label="时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="receiverName" label="收件人" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="subject" label="主题" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="原因" min-width="180" />
        <el-table-column prop="retryCount" label="重试次数" width="90" />
        <el-table-column label="操作" width="90" align="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'SENT'"
              link
              type="primary"
              :loading="retryingId === row.id"
              :disabled="retryingId != null"
              @click="retry(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="total > 20" class="pager">
        <el-pagination
          v-model:current-page="page"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="20"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--ams-text-secondary);
  font-size: 13px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>

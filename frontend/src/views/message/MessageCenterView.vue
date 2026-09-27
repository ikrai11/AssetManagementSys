<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listMessages, markMessagesRead } from '@/api/message'
import { useMessageStore } from '@/stores/message'
import type { SiteMessage } from '@/types/api'

interface DeviceRow {
  assetNo: string
  name: string
  holder: string
  due: string
  overdueDays: string
}

const route = useRoute()
const router = useRouter()
const messages = useMessageStore()
const loading = ref(false)
const markingAll = ref(false)
const list = ref<SiteMessage[]>([])
const total = ref(0)
const page = ref(1)
const openId = ref<number | null>(null)

const box = computed(() => (route.query.box === 'unread' ? 'unread' : 'all'))

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

function deviceRows(content: string): DeviceRow[] | null {
  const lines = content.split('\n').map((line) => line.trim()).filter((line) => line.length > 0)
  const header = lines[0]
  if (lines.length < 2 || header == null || !header.startsWith('资产编号')) return null
  return lines.slice(1).map((line) => {
    const [assetNo = '', name = '', holder = '', due = '', overdueDays = ''] = line.split('\t')
    return { assetNo, name, holder, due, overdueDays }
  })
}

function switchBox(next: string | number | boolean | undefined) {
  const value = next === 'unread' ? 'unread' : 'all'
  router.replace({ name: 'messages', query: value === 'unread' ? { box: 'unread' } : {} })
}

async function load() {
  loading.value = true
  try {
    const { data } = await listMessages({
      box: box.value === 'unread' ? 'unread' : undefined,
      page: page.value,
      pageSize: 20,
    })
    list.value = data.data.list
    total.value = data.data.total
    if (openId.value != null && !list.value.some((item) => item.id === openId.value)) {
      openId.value = null
    }
  } finally {
    loading.value = false
  }
}

async function open(row: SiteMessage) {
  if (openId.value === row.id) {
    openId.value = null
    return
  }
  openId.value = row.id
  if (!row.read) {
    await markMessagesRead({ ids: [row.id] })
    row.read = true
    await messages.refresh()
  }
}

async function markAll() {
  markingAll.value = true
  try {
    await markMessagesRead({ all: true })
    list.value.forEach((item) => {
      item.read = true
    })
    await messages.refresh()
    if (box.value === 'unread') {
      await load()
    }
  } finally {
    markingAll.value = false
  }
}

watch(() => route.query.box, () => {
  page.value = 1
  load()
}, { immediate: true })
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-toolbar">
      <el-radio-group :model-value="box" @change="switchBox">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="unread">未读</el-radio-button>
      </el-radio-group>
      <el-button :disabled="messages.unread === 0" :loading="markingAll" @click="markAll">全部已读</el-button>
    </div>

    <div class="table-panel">
      <el-empty v-if="!list.length" description="暂无数据" />
      <div v-else class="msg-list">
        <button
          v-for="item in list"
          :key="item.id"
          type="button"
          class="msg-item"
          :class="{ open: openId === item.id }"
          @click="open(item)"
        >
          <span class="msg-head">
            <i class="dot" :class="{ unread: !item.read }" />
            <strong>{{ item.title }}</strong>
            <em>{{ item.msgTypeLabel }}</em>
            <time>{{ formatTime(item.createdAt) }}</time>
          </span>
          <div v-if="openId === item.id" class="msg-body" @click.stop>
            <el-table
              v-if="deviceRows(item.content)"
              :data="deviceRows(item.content) || []"
              stripe
              empty-text="暂无数据"
            >
              <el-table-column prop="assetNo" label="资产编号" min-width="120" />
              <el-table-column prop="name" label="资产名称" min-width="120" />
              <el-table-column prop="holder" label="领用人" width="100" />
              <el-table-column prop="due" label="预计归还日" width="120" />
              <el-table-column prop="overdueDays" label="逾期天数" width="90" />
            </el-table>
            <p v-else class="msg-text">{{ item.content }}</p>
          </div>
        </button>
      </div>
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
.msg-list {
  display: flex;
  flex-direction: column;
}

.msg-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
  padding: 12px 4px;
  border: none;
  border-bottom: 1px solid #ebeef5;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}

.msg-head strong {
  font-size: 14px;
  font-weight: 600;
}

.msg-head em {
  color: var(--ams-text-secondary);
  font-style: normal;
  font-size: 12px;
}

.msg-head time {
  margin-left: auto;
  color: var(--ams-text-secondary);
  font-size: 12px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
  flex: none;
}

.dot.unread {
  background: #409eff;
}

.msg-body {
  padding: 4px 16px 8px;
}

.msg-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>

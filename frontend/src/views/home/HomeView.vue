<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsCoreOption } from 'echarts'
import { getBorrowTrend, getByCategory, getOverview } from '@/api/stats'
import { listTodos } from '@/api/borrow'
import AmsChart from '@/components/AmsChart.vue'
import { useAuthStore } from '@/stores/auth'
import type { BorrowOrder, StatsNameCount, StatsOverview, StatsTrend } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const overview = ref<StatsOverview | null>(null)
const categories = ref<StatsNameCount[]>([])
const trend = ref<StatsTrend[]>([])
const todos = ref<BorrowOrder[]>([])

const isAdmin = computed(() => auth.role === 'ADMIN')

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function addDays(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

const adminCards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { label: '待审批', value: data.pendingApproval, type: 'warning', to: { name: 'borrow-todos' } },
    { label: '已逾期', value: data.overdue, type: 'danger', to: { name: 'assets', query: { statuses: 'OVERDUE' } } },
    { label: '即将到期', value: data.dueSoon, type: 'warning', to: { name: 'assets', query: { statuses: 'BORROWED', dueFrom: formatDate(new Date()), dueTo: addDays(7) } } },
    { label: '已领用', value: data.borrowed, type: '', to: { name: 'assets', query: { statuses: 'BORROWED' } } },
    { label: '审批中', value: data.pending, type: '', to: { name: 'assets', query: { statuses: 'PENDING' } } },
    { label: '在库', value: data.inStock, type: 'success', to: { name: 'assets', query: { statuses: 'IN_STOCK' } } },
    { label: '设备总数', value: data.total, type: '', to: { name: 'assets' } },
    { label: '维修中', value: data.repairing, type: 'info', to: { name: 'assets', query: { statuses: 'REPAIRING' } } },
    { label: '已报废', value: data.scrapped, type: 'info', to: { name: 'assets', query: { statuses: 'SCRAPPED' } } },
  ]
})

const userCards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { label: '我的申请中', value: data.myApplying, type: 'warning', to: { name: 'borrow-mine', query: { tab: 'active' } } },
    { label: '我的在用', value: data.myUsing, type: '', to: { name: 'borrow-mine', query: { tab: 'using' } } },
    { label: '我的即将到期', value: data.myDueSoon, type: 'warning', to: { name: 'assets', query: { statuses: 'BORROWED', dueFrom: formatDate(new Date()), dueTo: addDays(7) } } },
    { label: '我的已逾期', value: data.myOverdue, type: 'danger', to: { name: 'assets', query: { statuses: 'OVERDUE' } } },
  ]
})

const cards = computed(() => (isAdmin.value ? adminCards.value : userCards.value))

const statusOption = computed<EChartsCoreOption | null>(() => {
  const data = overview.value
  if (!data) return null
  const items = [
    { name: '在库', value: data.inStock },
    { name: '审批中', value: data.pending },
    { name: '已领用', value: data.borrowed },
  ].filter((item) => item.value > 0)
  if (!items.length) return null
  return {
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: ['42%', '68%'], data: items }],
  }
})

const categoryOption = computed<EChartsCoreOption | null>(() => {
  if (!categories.value.length) return null
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: categories.value.map((item) => item.name) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: categories.value.map((item) => item.count), barMaxWidth: 36 }],
  }
})

const trendOption = computed<EChartsCoreOption | null>(() => {
  if (!trend.value.length || trend.value.every((item) => item.count === 0)) return null
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: trend.value.map((item) => item.month) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'line', smooth: true, data: trend.value.map((item) => item.count) }],
  }
})

async function load() {
  loading.value = true
  try {
    const [overviewRes, categoryRes, trendRes] = await Promise.all([
      getOverview(),
      getByCategory(),
      getBorrowTrend(),
    ])
    overview.value = overviewRes.data.data
    categories.value = categoryRes.data.data
    trend.value = trendRes.data.data
    if (isAdmin.value) {
      const todoRes = await listTodos()
      todos.value = todoRes.data.data.pending.slice(0, 8)
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="stat-grid">
      <button
        v-for="card in cards"
        :key="card.label"
        class="stat-card"
        type="button"
        @click="router.push(card.to)"
      >
        <span class="stat-label">{{ card.label }}</span>
        <strong :class="['stat-value', card.type]">{{ card.value }}</strong>
      </button>
    </div>

    <div v-if="isAdmin" class="chart-row">
      <div class="chart-panel">
        <h2>设备状态占比</h2>
        <AmsChart v-if="statusOption" :option="statusOption" />
        <el-empty v-else description="暂无数据" />
      </div>
      <div class="chart-panel">
        <h2>设备类型分布</h2>
        <AmsChart v-if="categoryOption" :option="categoryOption" />
        <el-empty v-else description="暂无数据" />
      </div>
    </div>
    <div v-else class="chart-row">
      <div class="chart-panel">
        <h2>在用设备类型</h2>
        <AmsChart v-if="categoryOption" :option="categoryOption" />
        <el-empty v-else description="暂无数据" />
      </div>
    </div>

    <div class="chart-panel">
      <h2>{{ isAdmin ? '近 6 个月领用次数' : '近 6 个月本人领用次数' }}</h2>
      <AmsChart v-if="trendOption" :option="trendOption" />
      <el-empty v-else description="暂无数据" />
    </div>

    <div v-if="isAdmin" class="table-panel">
      <div class="todo-head">
        <h2>待审批</h2>
        <el-button link type="primary" @click="router.push({ name: 'borrow-todos' })">全部待办</el-button>
      </div>
      <el-table :data="todos" stripe empty-text="暂无数据">
        <el-table-column prop="orderNo" label="单号" min-width="140" />
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column prop="assetName" label="设备" min-width="120" />
        <el-table-column prop="statusLabel" label="状态" width="100" />
        <el-table-column label="提交时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'borrow-detail', params: { id: row.id } })">
              办理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else class="page-toolbar">
      <el-button type="primary" @click="router.push({ name: 'assets' })">浏览在库设备</el-button>
      <el-button @click="router.push({ name: 'borrow-mine' })">查看我的申请</el-button>
      <el-button @click="router.push({ name: 'messages', query: { box: 'unread' } })">打开未读消息</el-button>
    </div>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 16px;
  border: none;
  border-radius: 4px;
  background: var(--ams-card);
  text-align: left;
  cursor: pointer;
}

.stat-card:hover {
  box-shadow: 0 2px 8px rgba(0, 21, 41, 0.08);
}

.stat-label {
  color: var(--ams-text-secondary);
  font-size: 13px;
}

.stat-value {
  font-size: 26px;
  line-height: 1;
  color: var(--ams-text);
}

.stat-value.warning {
  color: #e6a23c;
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-value.success {
  color: #67c23a;
}

.stat-value.info {
  color: #909399;
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.chart-panel {
  background: var(--ams-card);
  border-radius: 4px;
  padding: 16px;
}

.chart-panel h2,
.todo-head h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.todo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

@media (max-width: 960px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>

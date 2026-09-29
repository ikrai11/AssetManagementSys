<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { exportAuditLogs, listAuditLogs } from '@/api/audit'
import type { AuditLog } from '@/types/api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const exporting = ref(false)
const list = ref<AuditLog[]>([])
const total = ref(0)

const modules = [
  { value: 'ASSET', label: '设备' },
  { value: 'PARAM', label: '系统参数' },
  { value: 'DEPT', label: '部门' },
  { value: 'LOCATION', label: '地点' },
  { value: 'STOCKTAKE', label: '盘点' },
  { value: 'AUDIT', label: '审计' },
]

const query = reactive({
  range: [] as string[],
  module: '',
  objectNo: '',
  operator: '',
  page: 1,
  pageSize: 20,
})

function queryText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function applyRouteQuery() {
  const from = queryText(route.query.from)
  const to = queryText(route.query.to)
  query.range = from && to ? [from, to] : []
  query.module = queryText(route.query.module)
  query.objectNo = queryText(route.query.objectNo)
  query.operator = queryText(route.query.operator)
  query.page = Number(route.query.page || 1)
}

function params() {
  return {
    from: query.range[0] || undefined,
    to: query.range[1] || undefined,
    module: query.module || undefined,
    objectNo: query.objectNo || undefined,
    operator: query.operator || undefined,
    page: query.page,
    pageSize: query.pageSize,
  }
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    const { data } = await listAuditLogs(params())
    list.value = data.data.list
    total.value = data.data.total
    await router.replace({
      query: {
        from: query.range[0] || undefined,
        to: query.range[1] || undefined,
        module: query.module || undefined,
        objectNo: query.objectNo || undefined,
        operator: query.operator || undefined,
        page: String(query.page),
      },
    })
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.range = []
  query.module = ''
  query.objectNo = ''
  query.operator = ''
  query.page = 1
  load()
}

async function exportCurrent() {
  exporting.value = true
  try {
    const current = params()
    await exportAuditLogs({
      from: current.from,
      to: current.to,
      module: current.module,
      objectNo: current.objectNo,
      operator: current.operator,
    })
    ElMessage.success('已开始下载')
    await load()
  } catch (error) {
    if (error instanceof Error && error.message) {
      ElMessage.error(error.message)
    }
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  applyRouteQuery()
  load()
})
</script>

<template>
  <div class="page">
    <div class="page-panel">
      <p class="hint">日志只追加，不能修改或删除。导出范围等于当前筛选，不受当前页限制。没有记录时不会生成空文件。</p>
      <el-form inline class="search-form" @submit.prevent="search">
        <el-form-item label="时间">
          <el-date-picker
            v-model="query.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日"
            end-placeholder="结束日"
            range-separator="至"
          />
        </el-form-item>
        <el-form-item label="操作者">
          <el-input v-model="query.operator" clearable placeholder="姓名或账号" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="query.module" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="item in modules" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象编号">
          <el-input v-model="query.objectNo" clearable placeholder="编号" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
          <el-button :icon="Download" :loading="exporting" @click="exportCurrent">导出</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-panel">
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无数据">
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作者" width="120" />
        <el-table-column prop="moduleLabel" label="模块" width="110" />
        <el-table-column prop="actionLabel" label="动作" width="110" />
        <el-table-column prop="objectNo" label="对象编号" min-width="140" />
        <el-table-column prop="summary" label="变更摘要" min-width="220" show-overflow-tooltip />
      </el-table>
      <div v-if="total > 0" class="pager">
        <el-pagination
          v-model:current-page="query.page"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
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

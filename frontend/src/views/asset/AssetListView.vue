<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowUp, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAssets, removeAsset } from '@/api/asset'
import { listCategories, listDepts, listLocations } from '@/api/dict'
import { useAuthStore } from '@/stores/auth'
import { assetTagType } from '@/utils/status'
import type { AssetItem, DictItem } from '@/types/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const moreFilters = ref(false)
const list = ref<AssetItem[]>([])
const total = ref(0)
const categories = ref<DictItem[]>([])
const depts = ref<DictItem[]>([])
const locations = ref<DictItem[]>([])

const query = reactive({
  keyword: '',
  categoryIds: [] as number[],
  statuses: [] as string[],
  deptId: undefined as number | undefined,
  locationId: undefined as number | undefined,
  borrowRange: null as [string, string] | null,
  dueRange: null as [string, string] | null,
  page: 1,
  pageSize: 20,
})

const isAdmin = computed(() => auth.role === 'ADMIN')

function queryText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function queryNumber(value: unknown) {
  const text = queryText(value)
  return text ? Number(text) : undefined
}

function queryNumbers(value: unknown) {
  return queryText(value)
    .split(',')
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item) && item > 0)
}

function queryList(value: unknown) {
  return queryText(value).split(',').filter(Boolean)
}

function applyRouteQuery() {
  query.keyword = queryText(route.query.keyword)
  query.categoryIds = queryNumbers(route.query.categoryIds)
  query.statuses = queryList(route.query.statuses)
  query.deptId = queryNumber(route.query.deptId)
  query.locationId = queryNumber(route.query.locationId)
  const borrowFrom = queryText(route.query.borrowStartFrom)
  const borrowTo = queryText(route.query.borrowStartTo)
  query.borrowRange = borrowFrom && borrowTo ? [borrowFrom, borrowTo] : null
  const dueFrom = queryText(route.query.dueFrom)
  const dueTo = queryText(route.query.dueTo)
  query.dueRange = dueFrom && dueTo ? [dueFrom, dueTo] : null
  query.page = Number(route.query.page || 1)
  query.pageSize = Number(route.query.pageSize || 20)
  moreFilters.value = Boolean(query.deptId || query.locationId || query.borrowRange || query.dueRange)
}

async function loadDicts() {
  const [c, d, l] = await Promise.all([listCategories(), listDepts(), listLocations()])
  categories.value = c.data.data
  depts.value = d.data.data
  locations.value = l.data.data
}

async function load() {
  loading.value = true
  try {
    const { data } = await listAssets({
      keyword: query.keyword || undefined,
      categoryIds: query.categoryIds.length ? query.categoryIds : undefined,
      statuses: query.statuses.length ? query.statuses : undefined,
      deptId: query.deptId,
      locationId: query.locationId,
      borrowStartFrom: query.borrowRange?.[0],
      borrowStartTo: query.borrowRange?.[1],
      dueFrom: query.dueRange?.[0],
      dueTo: query.dueRange?.[1],
      page: query.page,
      pageSize: query.pageSize,
    })
    list.value = data.data.list
    total.value = data.data.total
    await router.replace({
      query: {
        keyword: query.keyword || undefined,
        categoryIds: query.categoryIds.length ? query.categoryIds.join(',') : undefined,
        statuses: query.statuses.length ? query.statuses.join(',') : undefined,
        deptId: query.deptId ? String(query.deptId) : undefined,
        locationId: query.locationId ? String(query.locationId) : undefined,
        borrowStartFrom: query.borrowRange?.[0],
        borrowStartTo: query.borrowRange?.[1],
        dueFrom: query.dueRange?.[0],
        dueTo: query.dueRange?.[1],
        page: String(query.page),
        pageSize: String(query.pageSize),
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
  query.keyword = ''
  query.categoryIds = []
  query.statuses = []
  query.deptId = undefined
  query.locationId = undefined
  query.borrowRange = null
  query.dueRange = null
  query.page = 1
  query.pageSize = 20
  load()
}

async function remove(row: AssetItem) {
  await ElMessageBox.confirm(`确认删除设备 ${row.assetNo}？仅从未被领用的在库设备可删除。`, '删除确认', {
    type: 'warning',
  })
  await removeAsset(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  applyRouteQuery()
  await loadDicts()
  await load()
})

watch(() => [query.page, query.pageSize], () => load())
</script>

<template>
  <div class="page">
    <div class="page-panel">
      <el-form :inline="true" class="search-form" @submit.prevent="search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编号 / 名称 / 序列号" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.categoryIds" multiple collapse-tags collapse-tags-tooltip clearable placeholder="全部">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="状态">
          <el-select v-model="query.statuses" multiple collapse-tags collapse-tags-tooltip clearable placeholder="全部">
            <el-option label="在库" value="IN_STOCK" />
            <el-option label="审批中" value="PENDING" />
            <el-option label="已领用" value="BORROWED" />
            <el-option label="已逾期" value="OVERDUE" />
          </el-select>
        </el-form-item>
        <template v-if="moreFilters">
          <el-form-item label="部门">
            <el-select v-model="query.deptId" clearable placeholder="全部">
              <el-option v-for="item in depts" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="地点">
            <el-select v-model="query.locationId" clearable placeholder="全部">
              <el-option v-for="item in locations" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="领用时间">
            <el-date-picker
              v-model="query.borrowRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日"
              end-placeholder="结束日"
              clearable
            />
          </el-form-item>
          <el-form-item label="预计归还">
            <el-date-picker
              v-model="query.dueRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日"
              end-placeholder="结束日"
              clearable
            />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
          <el-button link type="primary" @click="moreFilters = !moreFilters">
            {{ moreFilters ? '收起' : '展开' }}
            <el-icon class="el-icon--right"><component :is="moreFilters ? ArrowUp : ArrowDown" /></el-icon>
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-toolbar">
      <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="router.push({ name: 'asset-create' })">
        新建设备
      </el-button>
      <span v-else class="toolbar-hint">仅展示在库设备与本人相关设备</span>
    </div>

    <div class="table-panel">
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        :row-class-name="({ row }: { row: AssetItem }) => (row.overdue ? 'overdue-row' : '')"
      >
        <el-table-column prop="assetNo" label="资产编号" min-width="110" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="类型" width="88" />
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <el-tag :type="assetTagType(row.status, row.overdue)">{{ row.overdue ? '已逾期' : row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="存放地点" min-width="100" show-overflow-tooltip />
        <el-table-column prop="holderName" label="领用人" width="88" show-overflow-tooltip />
        <el-table-column prop="borrowStartDate" label="领用时间" width="112" />
        <el-table-column prop="expectedReturnDate" label="预计归还日" width="112" />
        <el-table-column label="操作" width="168" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'asset-detail', params: { id: row.id } })">
              详情
            </el-button>
            <el-button v-if="isAdmin" link type="primary" @click="router.push({ name: 'asset-edit', params: { id: row.id } })">
              编辑
            </el-button>
            <el-button
              v-if="!isAdmin && row.status === 'IN_STOCK'"
              link
              type="primary"
              @click="router.push({ name: 'borrow-apply', query: { assetId: row.id } })"
            >
              申请领用
            </el-button>
            <el-button v-if="isAdmin && row.status === 'IN_STOCK'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.toolbar-hint {
  color: var(--ams-text-secondary);
  font-size: 13px;
}
</style>

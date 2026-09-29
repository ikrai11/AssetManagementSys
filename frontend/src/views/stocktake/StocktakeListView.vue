<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createStocktake, listStocktakes, type StocktakeTask } from '@/api/stocktake'
import { listDepts, listLocations } from '@/api/dict'
import type { DictItem } from '@/types/api'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const list = ref<StocktakeTask[]>([])
const depts = ref<DictItem[]>([])
const locations = ref<DictItem[]>([])
const open = ref(false)
const scopeType = ref<'DEPT' | 'LOCATION'>('LOCATION')
const deptId = ref<number>()
const locationId = ref<number>()

async function load() {
  loading.value = true
  try {
    const { data } = await listStocktakes()
    list.value = data.data
  } finally {
    loading.value = false
  }
}

function resetForm() {
  scopeType.value = 'LOCATION'
  deptId.value = undefined
  locationId.value = undefined
}

async function submit() {
  if (scopeType.value === 'DEPT' && !deptId.value) {
    ElMessage.warning('请选择部门')
    return
  }
  if (scopeType.value === 'LOCATION' && !locationId.value) {
    ElMessage.warning('请选择地点')
    return
  }
  saving.value = true
  try {
    const { data } = await createStocktake({
      scopeType: scopeType.value,
      deptId: scopeType.value === 'DEPT' ? deptId.value : undefined,
      locationId: scopeType.value === 'LOCATION' ? locationId.value : undefined,
    })
    open.value = false
    ElMessage.success('已生成应盘清单')
    await router.push({ name: 'stocktake-detail', params: { id: data.data.id } })
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const [deptRes, locationRes] = await Promise.all([listDepts(), listLocations()])
  depts.value = deptRes.data.data
  locations.value = locationRes.data.data
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-toolbar">
      <el-button type="primary" :icon="Plus" @click="resetForm(); open = true">新建盘点</el-button>
      <span class="toolbar-hint">按部门或地点生成应盘清单。缺失和位置不符不会自动报废，也不改变领用。</span>
    </div>
    <div class="table-panel">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="title" label="盘点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="scopeLabel" label="范围" min-width="140" show-overflow-tooltip />
        <el-table-column prop="statusLabel" label="状态" width="90" />
        <el-table-column prop="total" label="应盘" width="72" />
        <el-table-column prop="difference" label="差异" width="72" />
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="88" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'stocktake-detail', params: { id: row.id } })">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无盘点任务" />
    </div>

    <el-dialog v-model="open" title="新建盘点" width="480px">
      <p class="dialog-hint">只列入该部门或地点下未报废的设备。已报废不进入应盘清单。</p>
      <el-form label-width="90px">
        <el-form-item label="范围">
          <el-radio-group v-model="scopeType">
            <el-radio value="LOCATION">按地点</el-radio>
            <el-radio value="DEPT">按部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeType === 'DEPT'" label="部门" required>
          <el-select v-model="deptId" placeholder="选择部门" style="width: 100%">
            <el-option v-for="item in depts" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="地点" required>
          <el-select v-model="locationId" placeholder="选择地点" style="width: 100%">
            <el-option v-for="item in locations" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">生成清单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dialog-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
}
</style>

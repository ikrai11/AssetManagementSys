<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { finishStocktake, getStocktake, markStocktake, type StocktakeTask } from '@/api/stocktake'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const acting = ref(false)
const task = ref<StocktakeTask>()
const mismatchOpen = ref(false)
const mismatchItemId = ref<number>()
const mismatchComment = ref('')
const open = computed(() => task.value?.status === 'OPEN')
const differences = computed(() => (task.value?.items ?? []).filter((item) => item.result === 'MISSING' || item.result === 'MISMATCH'))

async function load() {
  loading.value = true
  try {
    const { data } = await getStocktake(Number(route.params.id))
    task.value = data.data
  } finally {
    loading.value = false
  }
}

async function mark(itemId: number, result: string, comment?: string) {
  if (!task.value) return
  acting.value = true
  try {
    const { data } = await markStocktake(task.value.id, itemId, { result, comment })
    task.value = data.data
  } finally {
    acting.value = false
  }
}

function openMismatch(itemId: number) {
  mismatchItemId.value = itemId
  mismatchComment.value = ''
  mismatchOpen.value = true
}

async function submitMismatch() {
  if (!mismatchComment.value.trim()) {
    ElMessage.warning('请填写发现地点')
    return
  }
  await mark(mismatchItemId.value!, 'MISMATCH', mismatchComment.value.trim())
  mismatchOpen.value = false
}

async function finish() {
  if (!task.value) return
  try {
    await ElMessageBox.confirm(
      '结束后不能再改标记。缺失和位置不符只进入差异清单，不会自动报废，也不改变领用。',
      '结束盘点',
      { type: 'warning', confirmButtonText: '确认结束' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const { data } = await finishStocktake(task.value.id)
    task.value = data.data
    ElMessage.success('盘点已结束')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel" v-if="task">
      <el-page-header :content="task.title" @back="router.push({ name: 'stocktakes' })" />
      <div class="detail-head">
        <div>
          <p>{{ task.scopeLabel }} · 应盘 {{ task.total }} · 未盘 {{ task.pending }} · 差异 {{ task.difference }}</p>
        </div>
        <el-tag :type="open ? 'primary' : 'info'">{{ task.statusLabel }}</el-tag>
      </div>
      <div class="detail-actions">
        <el-button v-if="open" type="primary" :disabled="acting || (task.pending ?? 0) > 0" @click="finish">
          结束盘点
        </el-button>
        <span v-if="open && (task.pending ?? 0) > 0" class="toolbar-hint">还有 {{ task.pending }} 台未盘点，全部标记后才能结束。</span>
      </div>

      <h3 class="section-title">应盘清单</h3>
      <el-table :data="task.items" stripe>
        <el-table-column prop="assetNo" label="资产编号" min-width="120" />
        <el-table-column prop="assetName" label="名称" min-width="120" />
        <el-table-column prop="assetStatusLabel" label="盘点时状态" width="110" />
        <el-table-column prop="locationName" label="存放地点" min-width="110" />
        <el-table-column prop="deptName" label="责任部门" min-width="110" />
        <el-table-column prop="resultLabel" label="结果" width="100" />
        <el-table-column prop="comment" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="open" label="操作" width="220" align="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="acting" @click="mark(row.id, 'NORMAL')">正常</el-button>
            <el-button link type="danger" :disabled="acting" @click="mark(row.id, 'MISSING')">缺失</el-button>
            <el-button link type="warning" :disabled="acting" @click="openMismatch(row.id)">位置不符</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!task.items?.length" description="该范围内没有未报废设备" />

      <h3 class="section-title">差异清单</h3>
      <el-table v-if="differences.length" :data="differences" stripe>
        <el-table-column prop="assetNo" label="资产编号" min-width="120" />
        <el-table-column prop="assetName" label="名称" min-width="120" />
        <el-table-column prop="resultLabel" label="差异" width="100" />
        <el-table-column prop="comment" label="说明" min-width="180" />
        <el-table-column prop="assetStatusLabel" label="盘点时状态" width="110" />
      </el-table>
      <el-empty v-else description="暂无差异" />
    </div>

    <el-dialog v-model="mismatchOpen" title="位置不符" width="420px">
      <p class="dialog-hint">填写实际发现地点。不会改设备的存放地点，也不会改变领用。</p>
      <el-input v-model="mismatchComment" type="textarea" maxlength="200" show-word-limit placeholder="例如：在会议室" />
      <template #footer>
        <el-button @click="mismatchOpen = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitMismatch">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
.detail-head p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
.section-title {
  margin: 24px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
.dialog-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
}
</style>

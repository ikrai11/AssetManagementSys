<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createOrgDept,
  createOrgLocation,
  listOrgDepts,
  listOrgLocations,
  removeOrgDept,
  removeOrgLocation,
  updateOrgDept,
  updateOrgLocation,
} from '@/api/org'
import type { OrgNode, OrgSavePayload } from '@/types/api'

type OrgKind = 'dept' | 'location'

interface OrgRow extends OrgNode {
  children?: OrgRow[]
}

const loading = ref(false)
const saving = ref(false)
const kind = ref<OrgKind>('dept')
const rows = ref<OrgNode[]>([])
const dialogOpen = ref(false)
const editing = ref<OrgNode | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  parentId: undefined as number | undefined,
  enabled: true,
})

const rules: FormRules<typeof form> = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
}

const tree = computed(() => toTree(rows.value))
const parentOptions = computed(() => {
  const banned = editing.value ? descendantIds(editing.value.id, rows.value) : new Set<number>()
  return rows.value
    .filter((item) => !banned.has(item.id))
    .map((item) => ({
      id: item.id,
      label: `${'　'.repeat(depthOf(item, rows.value))}${item.name}${item.enabled ? '' : '（已停用）'}`,
    }))
})

const kindLabel = computed(() => (kind.value === 'dept' ? '部门' : '地点'))

function toTree(list: OrgNode[]) {
  const nodes: OrgRow[] = list.map((item) => ({ ...item }))
  const byId = new Map(nodes.map((item) => [item.id, item]))
  const roots: OrgRow[] = []
  for (const node of nodes) {
    const parent = node.parentId ? byId.get(node.parentId) : undefined
    if (parent) {
      parent.children = parent.children ?? []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

function depthOf(item: OrgNode, list: OrgNode[]) {
  let depth = 0
  let parentId = item.parentId
  const seen = new Set<number>()
  while (parentId && !seen.has(parentId)) {
    seen.add(parentId)
    depth += 1
    parentId = list.find((row) => row.id === parentId)?.parentId
  }
  return depth
}

function descendantIds(id: number, list: OrgNode[]) {
  const children = new Map<number, number[]>()
  for (const item of list) {
    if (!item.parentId) continue
    const bucket = children.get(item.parentId) ?? []
    bucket.push(item.id)
    children.set(item.parentId, bucket)
  }
  const banned = new Set<number>([id])
  const stack = [id]
  while (stack.length) {
    const current = stack.pop()
    if (current == null) continue
    for (const child of children.get(current) ?? []) {
      if (!banned.has(child)) {
        banned.add(child)
        stack.push(child)
      }
    }
  }
  return banned
}

function canDelete(row: OrgNode) {
  return !row.referenced && !row.hasChildren
}

function deleteHint(row: OrgNode) {
  if (row.referenced) return '已被引用，只能停用'
  if (row.hasChildren) return '还有下级，不能删除'
  return ''
}

async function load() {
  loading.value = true
  try {
    const { data } = kind.value === 'dept' ? await listOrgDepts() : await listOrgLocations()
    rows.value = data.data
  } finally {
    loading.value = false
  }
}

function switchKind(next: string | number | boolean | undefined) {
  if (next !== 'dept' && next !== 'location') return
  kind.value = next
  load()
}

function openCreate() {
  editing.value = null
  form.name = ''
  form.parentId = undefined
  form.enabled = true
  dialogOpen.value = true
}

function openEdit(row: OrgNode) {
  editing.value = row
  form.name = row.name
  form.parentId = row.parentId
  form.enabled = row.enabled
  dialogOpen.value = true
}

function payload(): OrgSavePayload {
  return {
    name: form.name.trim(),
    parentId: form.parentId ?? null,
    enabled: form.enabled,
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (kind.value === 'dept') {
      if (editing.value) await updateOrgDept(editing.value.id, payload())
      else await createOrgDept(payload())
    } else if (editing.value) {
      await updateOrgLocation(editing.value.id, payload())
    } else {
      await createOrgLocation(payload())
    }
    ElMessage.success('已保存')
    dialogOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: OrgNode) {
  if (row.enabled) {
    try {
      await ElMessageBox.confirm(
        `停用「${row.name}」后，不能再被新设备或新用户选用。已有记录仍显示该名称。`,
        '停用确认',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  const body: OrgSavePayload = { name: row.name, parentId: row.parentId ?? null, enabled: !row.enabled }
  if (kind.value === 'dept') await updateOrgDept(row.id, body)
  else await updateOrgLocation(row.id, body)
  ElMessage.success(row.enabled ? '已停用' : '已启用')
  await load()
}

async function remove(row: OrgNode) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  if (kind.value === 'dept') await removeOrgDept(row.id)
  else await removeOrgLocation(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-toolbar">
      <el-radio-group :model-value="kind" @change="switchKind">
        <el-radio-button value="dept">部门</el-radio-button>
        <el-radio-button value="location">存放地点</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增{{ kindLabel }}</el-button>
    </div>
    <div class="table-panel">
      <p class="hint">可设置上级。被用户、设备或盘点引用的项只能停用。停用后不会出现在新数据的下拉中，历史记录仍显示原名称。</p>
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="上级" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parentName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="引用" width="100">
          <template #default="{ row }">{{ row.referenced ? '已被引用' : '未引用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled ? 'warning' : 'primary'" @click="toggle(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-tooltip :disabled="canDelete(row)" :content="deleteHint(row)" placement="top">
              <span class="action-wrap">
                <el-button link type="danger" :disabled="!canDelete(row)" @click="remove(row)">删除</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogOpen" :title="(editing ? '编辑' : '新增') + kindLabel" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="上级">
          <el-select v-model="form.parentId" clearable placeholder="无上级" style="width: 100%">
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  margin: 4px 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.action-wrap {
  display: inline-block;
  margin-left: 8px;
}
</style>

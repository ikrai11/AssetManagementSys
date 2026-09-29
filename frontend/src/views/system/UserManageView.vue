<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createUser, listUsers, resetUserPassword, updateUser } from '@/api/user'
import { listDepts } from '@/api/dict'
import { useAuthStore } from '@/stores/auth'
import type { DictItem, UserAccount, UserSavePayload } from '@/types/api'
import { withCurrentOption } from '@/utils/dict'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const resetting = ref(false)
const list = ref<UserAccount[]>([])
const total = ref(0)
const depts = ref<DictItem[]>([])
const drawerOpen = ref(false)
const editing = ref<UserAccount | null>(null)
const formRef = ref<FormInstance>()
const resetOpen = ref(false)
const resetTarget = ref<UserAccount | null>(null)
const resetPassword = ref('')

const query = reactive({
  keyword: '',
  role: '' as '' | 'ADMIN' | 'USER',
  deptId: undefined as number | undefined,
  enabled: undefined as boolean | undefined,
  page: 1,
  pageSize: 20,
})

const form = reactive({
  username: '',
  realName: '',
  email: '',
  mobile: '',
  deptId: undefined as number | undefined,
  role: 'USER' as 'ADMIN' | 'USER',
  password: '',
  enabled: true,
})

const formDepts = computed(() => withCurrentOption(depts.value, editing.value?.deptId, editing.value?.deptName))

const formRules = computed<FormRules<typeof form>>(() => ({
  username: editing.value ? [] : [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: editing.value ? [] : [{ required: true, min: 8, message: '初始密码至少 8 位', trigger: 'blur' }],
  email: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.role === 'USER' && !value?.trim()) {
          callback(new Error('普通用户邮箱不能为空'))
          return
        }
        if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
          callback(new Error('邮箱格式不正确'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}))

function queryText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function applyRouteQuery() {
  query.keyword = queryText(route.query.keyword)
  const role = queryText(route.query.role)
  query.role = role === 'ADMIN' || role === 'USER' ? role : ''
  query.deptId = queryText(route.query.deptId) ? Number(route.query.deptId) : undefined
  const enabled = queryText(route.query.enabled)
  query.enabled = enabled === 'true' ? true : enabled === 'false' ? false : undefined
  query.page = Number(route.query.page || 1)
  query.pageSize = Number(route.query.pageSize || 20)
}

async function load() {
  loading.value = true
  try {
    const { data } = await listUsers({
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      deptId: query.deptId,
      enabled: query.enabled,
      page: query.page,
      pageSize: query.pageSize,
    })
    list.value = data.data.list
    total.value = data.data.total
    await router.replace({
      query: {
        keyword: query.keyword || undefined,
        role: query.role || undefined,
        deptId: query.deptId ? String(query.deptId) : undefined,
        enabled: query.enabled === undefined ? undefined : String(query.enabled),
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
  query.role = ''
  query.deptId = undefined
  query.enabled = undefined
  query.page = 1
  query.pageSize = 20
  load()
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    username: '',
    realName: '',
    email: '',
    mobile: '',
    deptId: undefined,
    role: 'USER',
    password: '',
    enabled: true,
  })
  drawerOpen.value = true
}

function openEdit(row: UserAccount) {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    email: row.email || '',
    mobile: row.mobile || '',
    deptId: row.deptId,
    role: row.role,
    password: '',
    enabled: row.enabled,
  })
  drawerOpen.value = true
}

function payload(): UserSavePayload {
  return {
    username: editing.value ? undefined : form.username,
    realName: form.realName,
    email: form.email || undefined,
    mobile: form.mobile || undefined,
    deptId: form.deptId,
    role: form.role,
    password: editing.value ? undefined : form.password,
    enabled: form.enabled,
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editing.value) {
      await updateUser(editing.value.id, payload())
      ElMessage.success('已保存')
    } else {
      await createUser(payload())
      ElMessage.success('已创建，该用户下次登录须修改密码')
    }
    drawerOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: UserAccount) {
  if (row.enabled) {
    await ElMessageBox.confirm(
      `确认停用 ${row.realName}（${row.username}）？该用户将不能登录，未归还设备仍保留。`,
      '停用确认',
      { type: 'warning' },
    )
  }
  await updateUser(row.id, {
    realName: row.realName,
    email: row.email,
    mobile: row.mobile,
    deptId: row.deptId,
    role: row.role,
    enabled: !row.enabled,
  })
  ElMessage.success(row.enabled ? '已停用' : '已启用')
  await load()
}

function openReset(row: UserAccount) {
  resetTarget.value = row
  resetPassword.value = ''
  resetOpen.value = true
}

async function confirmReset() {
  if (!resetTarget.value) return
  if (resetPassword.value.trim().length < 8) {
    ElMessage.error('密码至少 8 位')
    return
  }
  resetting.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetPassword.value.trim())
    ElMessage.success('已重置，该用户下次登录须修改密码')
    resetOpen.value = false
  } finally {
    resetting.value = false
  }
}

onMounted(async () => {
  applyRouteQuery()
  const { data } = await listDepts()
  depts.value = data.data
  await load()
})

watch(() => [query.page, query.pageSize], () => load())
</script>

<template>
  <div class="page">
    <div class="page-panel">
      <el-form :inline="true" class="search-form" @submit.prevent="search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="姓名 / 账号" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" clearable placeholder="全部">
            <el-option label="系统管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.deptId" clearable placeholder="全部">
            <el-option v-for="item in depts" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.enabled" clearable placeholder="全部">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
    </div>

    <div class="table-panel">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="username" label="账号" min-width="110" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="100" show-overflow-tooltip />
        <el-table-column prop="roleLabel" label="角色" width="110" />
        <el-table-column prop="deptName" label="部门" min-width="110" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.id !== auth.userId"
              link
              :type="row.enabled ? 'warning' : 'primary'"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
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

    <el-drawer v-model="drawerOpen" :title="editing ? '编辑用户' : '新建用户'" size="420px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="88px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(editing)" autocomplete="off" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机" prop="mobile">
          <el-input v-model="form.mobile" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable placeholder="请选择">
            <el-option v-for="item in formDepts" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role">
            <el-option label="系统管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :disabled="editing?.id === auth.userId" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="resetOpen" title="重置密码" width="400px">
      <p class="reset-hint">重置后该用户下次登录必须修改密码，已签发的登录态立即失效。</p>
      <el-input v-model="resetPassword" type="password" show-password placeholder="新密码至少 8 位" />
      <template #footer>
        <el-button @click="resetOpen = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="confirmReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.reset-hint {
  margin: 0 0 12px;
  color: var(--ams-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}
</style>

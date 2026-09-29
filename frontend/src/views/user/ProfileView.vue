<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getMe } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const username = ref('')
const roleLabel = ref('')
const deptName = ref('')
const form = reactive({
  realName: '',
  email: '',
})

const rules: FormRules<typeof form> = {
  realName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  email: [
    {
      validator: (_rule, value, callback) => {
        const text = String(value ?? '').trim()
        if (auth.role === 'USER' && !text) {
          callback(new Error('请填写邮箱'))
          return
        }
        if (text && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) {
          callback(new Error('邮箱格式不正确'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await getMe()
    username.value = data.data.username
    roleLabel.value = data.data.roleLabel || (data.data.role === 'ADMIN' ? '系统管理员' : '普通用户')
    deptName.value = data.data.deptName || ''
    form.realName = data.data.realName
    form.email = data.data.email || ''
  } finally {
    loading.value = false
  }
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await auth.saveProfile(form.realName.trim(), form.email.trim())
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 480px">
        <el-form-item label="账号">
          <el-input :model-value="username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-input :model-value="roleLabel" disabled />
        </el-form-item>
        <el-form-item label="部门">
          <el-input :model-value="deptName || '未分配'" disabled />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
          <el-button @click="router.push({ name: 'password' })">修改密码</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

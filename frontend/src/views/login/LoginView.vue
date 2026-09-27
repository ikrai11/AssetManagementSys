<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorHint = ref('')
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorHint.value = ''
  try {
    await auth.login(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(auth.mustChangePassword ? { name: 'password' } : redirect)
  } catch (error) {
    if (axios.isAxiosError(error)) {
      errorHint.value = error.response?.data?.message || '登录失败'
    } else {
      errorHint.value = '登录失败'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <el-card class="card">
      <template #header>资产管理系统</template>
      <el-alert
        v-if="errorHint"
        :title="errorHint"
        type="error"
        show-icon
        :closable="false"
        class="hint"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="full" native-type="submit">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f5f7fa;
}
.card {
  width: 420px;
}
.hint {
  margin-bottom: 16px;
}
.full {
  width: 100%;
}
</style>

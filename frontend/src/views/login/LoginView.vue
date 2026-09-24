<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { UserRole } from '@/types/role'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

function enter(role: UserRole) {
  auth.enterPreview(role)
  router.push({ name: 'assets' })
}
</script>

<template>
  <div class="login">
    <el-card class="card">
      <template #header>资产管理系统</template>
      <p>登录接口尚未接入。可先进入对应角色的页面框架。</p>
      <div class="actions">
        <el-button type="primary" @click="enter('ADMIN')">管理员预览</el-button>
        <el-button @click="enter('USER')">普通用户预览</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
.card {
  width: 420px;
}
.actions {
  display: flex;
  gap: 12px;
}
</style>

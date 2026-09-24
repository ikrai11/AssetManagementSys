<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const menus = computed(() => {
  if (auth.role === 'ADMIN') {
    return [
      { path: '/assets', title: '设备台账' },
      { path: '/borrows/todos', title: '领用办理' },
      { path: '/password', title: '修改密码' },
    ]
  }
  return [
    { path: '/assets', title: '设备' },
    { path: '/borrows/mine', title: '我的领用' },
    { path: '/password', title: '修改密码' },
  ]
})

function logout() {
  auth.clear()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px">
      <div class="brand">资产管理系统</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          {{ item.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ auth.realName }}</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </el-header>
      <el-main>
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.brand {
  padding: 16px;
  font-weight: 600;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  border-bottom: 1px solid var(--el-border-color);
}
</style>

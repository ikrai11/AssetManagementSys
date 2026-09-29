<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Box, Document, DocumentChecked, House, Lock, Message, Notebook, OfficeBuilding, Setting, SwitchButton, Tickets, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useMessageStore } from '@/stores/message'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const messages = useMessageStore()

const menus = computed(() => {
  if (auth.role === 'ADMIN') {
    return [
      { path: '/home', title: '首页', icon: House },
      { path: '/assets', title: '设备台账', icon: Box },
      { path: '/stocktakes', title: '盘点', icon: DocumentChecked },
      { path: '/borrows/todos', title: '领用办理', icon: Tickets },
      { path: '/messages', title: '消息中心', icon: Bell },
      { path: '/borrows/mine', title: '我的领用', icon: Notebook },
      { path: '/users', title: '用户管理', icon: User },
      { path: '/org', title: '部门地点', icon: OfficeBuilding },
      { path: '/system-params', title: '系统参数', icon: Setting },
      { path: '/audit-logs', title: '审计日志', icon: Document },
      { path: '/mail-records', title: '邮件记录', icon: Message },
      { path: '/password', title: '修改密码', icon: Lock },
    ]
  }
  return [
    { path: '/home', title: '首页', icon: House },
    { path: '/assets', title: '设备', icon: Box },
    { path: '/borrows/mine', title: '我的领用', icon: Notebook },
    { path: '/messages', title: '消息中心', icon: Bell },
    { path: '/password', title: '修改密码', icon: Lock },
  ]
})

const roleLabel = computed(() => (auth.role === 'ADMIN' ? '系统管理员' : '普通用户'))

onMounted(() => {
  if (auth.loggedIn && !auth.userId) {
    auth.fetchMe()
  }
  messages.refresh()
})

watch(() => route.fullPath, () => {
  if (auth.loggedIn) {
    messages.refresh()
  }
})
const pageTitle = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : '资产管理系统'))
const activeMenu = computed(() => (route.path.startsWith('/stocktakes') ? '/stocktakes' : route.path))

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside class="aside" width="210px">
      <div class="brand">资产管理系统</div>
      <el-menu
        :default-active="activeMenu"
        router
        class="menu"
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="body">
      <el-header class="header" height="50px">
        <h1 class="header-title">{{ pageTitle }}</h1>
        <div class="header-user">
          <el-badge :value="messages.unread" :max="99" :hidden="messages.unread <= 0" class="msg-badge">
            <el-button link :icon="Bell" @click="router.push({ name: 'messages', query: { box: 'unread' } })">
              消息
            </el-button>
          </el-badge>
          <span>{{ auth.realName }}（{{ roleLabel }}）</span>
          <el-button link type="primary" :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
}

.aside {
  background: var(--ams-sidebar);
  color: #fff;
}

.brand {
  height: var(--ams-header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu {
  border-right: none;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--ams-sidebar-active) !important;
}

.body {
  min-width: 0;
  background: var(--ams-bg);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 1;
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ams-text-secondary);
  font-size: 13px;
}

.msg-badge {
  display: flex;
  align-items: center;
}

.main {
  padding: 16px;
  overflow: auto;
}
</style>

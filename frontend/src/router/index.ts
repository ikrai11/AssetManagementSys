import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { requiresAuth: false, title: '登录' },
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/common/ForbiddenView.vue'),
      meta: { requiresAuth: true, title: '无权限' },
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'home' } },
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/home/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'assets',
          name: 'assets',
          component: () => import('@/views/asset/AssetListView.vue'),
          meta: { title: '设备列表' },
        },
        {
          path: 'assets/new',
          name: 'asset-create',
          component: () => import('@/views/asset/AssetFormView.vue'),
          meta: { role: 'ADMIN', title: '新建设备' },
        },
        {
          path: 'assets/:id/edit',
          name: 'asset-edit',
          component: () => import('@/views/asset/AssetFormView.vue'),
          meta: { role: 'ADMIN', title: '编辑设备' },
        },
        {
          path: 'assets/:id',
          name: 'asset-detail',
          component: () => import('@/views/asset/AssetDetailView.vue'),
          meta: { title: '设备详情' },
        },
        {
          path: 'borrows/apply',
          name: 'borrow-apply',
          component: () => import('@/views/borrow/BorrowApplyView.vue'),
          meta: { title: '申请领用' },
        },
        {
          path: 'borrows/mine',
          name: 'borrow-mine',
          component: () => import('@/views/borrow/MyBorrowView.vue'),
          meta: { title: '我的领用' },
        },
        {
          path: 'borrows/todos',
          name: 'borrow-todos',
          component: () => import('@/views/borrow/BorrowTodoView.vue'),
          meta: { role: 'ADMIN', title: '领用办理' },
        },
        {
          path: 'borrows/:id',
          name: 'borrow-detail',
          component: () => import('@/views/borrow/BorrowDetailView.vue'),
          meta: { title: '领用详情' },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/message/MessageCenterView.vue'),
          meta: { title: '消息中心' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/system/UserManageView.vue'),
          meta: { role: 'ADMIN', title: '用户管理' },
        },
        {
          path: 'mail-records',
          name: 'mail-records',
          component: () => import('@/views/system/MailRecordView.vue'),
          meta: { role: 'ADMIN', title: '邮件记录' },
        },
        {
          path: 'password',
          name: 'password',
          component: () => import('@/views/user/PasswordView.vue'),
          meta: { title: '修改密码' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && to.matched.some((record) => record.meta.requiresAuth) && !auth.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (auth.loggedIn && to.name === 'login') {
    return { name: 'home' }
  }
  if (auth.loggedIn && auth.mustChangePassword && to.name !== 'password') {
    return { name: 'password' }
  }
  const role = to.matched.find((record) => record.meta.role)?.meta.role
  if (role && auth.role !== role) {
    return { name: 'forbidden' }
  }
})

export default router

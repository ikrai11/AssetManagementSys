<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemParams, updateSystemParams } from '@/api/system'
import type { SysParams } from '@/types/api'

const loading = ref(false)
const saving = ref(false)
const form = reactive<SysParams>({
  remindLeadDays: '7,3,1',
  borrowMaxDays: 90,
  renewMaxDaysFromIssue: 180,
  mailChannelEnabled: true,
  loginMaxFailures: 5,
  loginLockMinutes: 15,
})

async function load() {
  loading.value = true
  try {
    const { data } = await getSystemParams()
    Object.assign(form, data.data)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const { data } = await updateSystemParams({ ...form })
    Object.assign(form, data.data)
    ElMessage.success('已保存，只影响之后的新申请和新提醒')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel">
      <h2>系统参数</h2>
      <p class="hint">到期当天的提醒始终保留。邮件通道关闭后只发站内信。导入 2000 行、导出 10000 行仍为固定上限。</p>
      <el-form label-width="180px" @submit.prevent="save">
        <el-form-item label="提前提醒天数">
          <el-input v-model="form.remindLeadDays" placeholder="例如 7,3,1" style="width: 240px" />
        </el-form-item>
        <el-form-item label="单次最长领用天数">
          <el-input-number v-model="form.borrowMaxDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="续借后自发放日起最长天数">
          <el-input-number v-model="form.renewMaxDaysFromIssue" :min="1" :max="3650" />
        </el-form-item>
        <el-form-item label="邮件通道">
          <el-switch v-model="form.mailChannelEnabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="登录失败锁定次数">
          <el-input-number v-model="form.loginMaxFailures" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="锁定分钟数">
          <el-input-number v-model="form.loginLockMinutes" :min="1" :max="1440" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
h2 {
  margin: 0 0 8px;
  font-size: 18px;
}
.hint {
  margin: 0 0 20px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>

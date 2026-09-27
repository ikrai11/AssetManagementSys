<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="page">
    <div class="form-panel">
      <el-result icon="warning" title="无权限" sub-title="当前角色不能打开这个页面">
        <template #extra>
          <el-button type="primary" @click="router.push({ name: 'assets' })">返回首页</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

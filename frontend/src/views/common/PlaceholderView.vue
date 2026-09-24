<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <el-card>
    <template #header>{{ title }}</template>
    <p>页面框架已就绪，业务功能在阶段 1 后续接入。</p>
  </el-card>
</template>

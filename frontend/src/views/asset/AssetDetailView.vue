<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAsset } from '@/api/asset'
import { useAuthStore } from '@/stores/auth'
import { actionLabel, assetTagType } from '@/utils/status'
import type { AssetItem } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const asset = ref<AssetItem>()
const isAdmin = computed(() => auth.role === 'ADMIN')

async function load() {
  loading.value = true
  try {
    const { data } = await getAsset(Number(route.params.id))
    asset.value = data.data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel" v-if="asset">
      <el-page-header content="设备详情" @back="router.back()" />
      <div class="detail-head">
        <div>
          <h2>{{ asset.name }}</h2>
          <p>{{ asset.assetNo }}</p>
        </div>
        <el-tag :type="assetTagType(asset.status, asset.overdue)">
          {{ asset.overdue ? '已逾期' : asset.statusLabel }}
        </el-tag>
      </div>
      <div class="detail-actions">
        <el-button v-if="isAdmin" @click="router.push({ name: 'asset-edit', params: { id: asset.id } })">编辑</el-button>
        <el-button
          v-if="asset.status === 'IN_STOCK'"
          type="primary"
          @click="router.push({ name: 'borrow-apply', query: { assetId: asset.id } })"
        >
          申请领用
        </el-button>
        <el-button
          v-if="!isAdmin && asset.status === 'BORROWED' && asset.currentBorrowId"
          type="primary"
          @click="router.push({ name: 'borrow-detail', params: { id: asset.currentBorrowId } })"
        >
          申请归还
        </el-button>
        <el-button
          v-if="isAdmin && asset.currentBorrowId"
          type="primary"
          @click="router.push({ name: 'borrow-detail', params: { id: asset.currentBorrowId } })"
        >
          查看领用单
        </el-button>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="类型">{{ asset.categoryName }}</el-descriptions-item>
        <el-descriptions-item label="品牌 / 型号">{{ asset.brand }} {{ asset.model }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ asset.serialNo }}</el-descriptions-item>
        <el-descriptions-item label="存放地点">{{ asset.locationName }}</el-descriptions-item>
        <el-descriptions-item label="责任部门">{{ asset.deptName }}</el-descriptions-item>
        <el-descriptions-item label="领用人">{{ asset.holderName }}</el-descriptions-item>
        <el-descriptions-item label="领用时间">{{ asset.borrowStartDate }}</el-descriptions-item>
        <el-descriptions-item label="预计归还日">{{ asset.expectedReturnDate }}</el-descriptions-item>
        <el-descriptions-item v-if="isAdmin" label="购置价格">{{ asset.purchasePrice }}</el-descriptions-item>
        <el-descriptions-item v-if="isAdmin" label="供应商">{{ asset.supplier }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ asset.remark }}</el-descriptions-item>
      </el-descriptions>
      <h3 class="section-title">领用履历</h3>
      <el-timeline v-if="asset.logs?.length">
        <el-timeline-item v-for="item in asset.logs" :key="item.id" :timestamp="item.createdAt">
          {{ actionLabel[item.action] || item.action }} · {{ item.operatorName }}
          <span v-if="item.comment">：{{ item.comment }}</span>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无领用记录" />
    </div>
  </div>
</template>

<style scoped>
.detail-head {
  margin-top: 20px;
}
.section-title {
  margin: 24px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
</style>

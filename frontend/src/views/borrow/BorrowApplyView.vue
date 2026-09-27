<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getAsset } from '@/api/asset'
import { createBorrow } from '@/api/borrow'
import type { AssetItem } from '@/types/api'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const asset = ref<AssetItem>()
const maxDate = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() + 90)
  return date.toISOString().slice(0, 10)
})

const form = reactive({
  purpose: '',
  expectedReturnDate: '',
  remark: '',
})

const rules: FormRules<typeof form> = {
  purpose: [{ required: true, message: '请填写用途', trigger: 'blur' }],
  expectedReturnDate: [{ required: true, message: '请选择预计归还日', trigger: 'change' }],
}

async function load() {
  const assetId = Number(route.query.assetId)
  if (!assetId) {
    ElMessage.error('请从设备详情进入申请')
    await router.push({ name: 'assets' })
    return
  }
  loading.value = true
  try {
    const { data } = await getAsset(assetId)
    asset.value = data.data
  } finally {
    loading.value = false
  }
}

async function submit(asDraft: boolean) {
  if (!asDraft) {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid || !asset.value) return
    await ElMessageBox.confirm('提交后设备将被占用，在审批结束前其他人不能申请。确认提交？', '提交申请', {
      type: 'warning',
    })
  }
  saving.value = true
  try {
    const { data } = await createBorrow({
      assetId: asset.value!.id,
      purpose: form.purpose,
      expectedReturnDate: form.expectedReturnDate,
      remark: form.remark,
      submit: !asDraft,
    })
    ElMessage.success(asDraft ? '草稿已保存' : '申请已提交')
    await router.push({ name: 'borrow-detail', params: { id: data.data.id } })
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel">
      <el-page-header content="申请领用" @back="router.push({ name: 'assets' })" />
      <p v-if="asset" class="asset-line">设备：{{ asset.assetNo }} {{ asset.name }}（当前 {{ asset.statusLabel }}）</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" style="max-width: 560px">
        <el-form-item label="用途" prop="purpose">
          <el-input v-model="form.purpose" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="预计归还日" prop="expectedReturnDate">
          <el-date-picker
            v-model="form.expectedReturnDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="(date: Date) => date <= new Date() || date.toISOString().slice(0, 10) > maxDate"
          />
        </el-form-item>
        <el-form-item label="领用说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="saving" @click="submit(true)">保存草稿</el-button>
          <el-button type="primary" :loading="saving" @click="submit(false)">提交申请</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.asset-line {
  margin: 20px 0 16px;
  color: var(--ams-text-secondary);
}
</style>

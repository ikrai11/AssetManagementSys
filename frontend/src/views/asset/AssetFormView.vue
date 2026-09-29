<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createAsset, getAsset, updateAsset } from '@/api/asset'
import { listCategories, listDepts, listLocations } from '@/api/dict'
import type { DictItem } from '@/types/api'
import { withCurrentOption } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const categories = ref<DictItem[]>([])
const depts = ref<DictItem[]>([])
const locations = ref<DictItem[]>([])
const editing = computed(() => Boolean(route.params.id))

const form = reactive({
  assetNo: '',
  name: '',
  categoryId: undefined as number | undefined,
  brand: '',
  model: '',
  serialNo: '',
  purchaseDate: '',
  purchasePrice: undefined as number | undefined,
  supplier: '',
  warrantyUntil: '',
  deptId: undefined as number | undefined,
  locationId: undefined as number | undefined,
  remark: '',
})

const rules: FormRules<typeof form> = {
  assetNo: [{ required: true, message: '请填写资产编号', trigger: 'blur' }],
  name: [{ required: true, message: '请填写资产名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
}

async function load() {
  const [c, d, l] = await Promise.all([listCategories(), listDepts(), listLocations()])
  categories.value = c.data.data
  depts.value = d.data.data
  locations.value = l.data.data
  if (editing.value) {
    loading.value = true
    try {
      const { data } = await getAsset(Number(route.params.id))
      if (data.data.status === 'SCRAPPED') {
        ElMessage.warning('已报废设备只读，不能修改')
        await router.replace({ name: 'asset-detail', params: { id: route.params.id } })
        return
      }
      Object.assign(form, {
        assetNo: data.data.assetNo,
        name: data.data.name,
        categoryId: data.data.categoryId,
        brand: data.data.brand ?? '',
        model: data.data.model ?? '',
        serialNo: data.data.serialNo ?? '',
        purchaseDate: data.data.purchaseDate ?? '',
        purchasePrice: data.data.purchasePrice,
        supplier: data.data.supplier ?? '',
        warrantyUntil: data.data.warrantyUntil ?? '',
        deptId: data.data.deptId,
        locationId: data.data.locationId,
        remark: data.data.remark ?? '',
      })
      depts.value = withCurrentOption(depts.value, data.data.deptId, data.data.deptName)
      locations.value = withCurrentOption(locations.value, data.data.locationId, data.data.locationName)
    } finally {
      loading.value = false
    }
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form }
    if (editing.value) {
      await updateAsset(Number(route.params.id), payload)
      ElMessage.success('已保存')
    } else {
      await createAsset(payload)
      ElMessage.success('设备已入库')
    }
    await router.push({ name: 'assets' })
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="form-panel">
      <el-page-header :content="editing ? '编辑设备' : '新建设备'" @back="router.back()" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="asset-form">
        <h3>基本信息</h3>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="资产编号" prop="assetNo">
              <el-input v-model="form.assetNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择" class="full">
                <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="form.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="form.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序列号">
              <el-input v-model="form.serialNo" />
            </el-form-item>
          </el-col>
        </el-row>
        <h3>购置信息</h3>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="购置日期">
              <el-date-picker v-model="form.purchaseDate" value-format="YYYY-MM-DD" type="date" class="full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置价格">
              <el-input-number v-model="form.purchasePrice" :min="0" :precision="2" class="full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="form.supplier" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修截止">
              <el-date-picker v-model="form.warrantyUntil" value-format="YYYY-MM-DD" type="date" class="full" />
            </el-form-item>
          </el-col>
        </el-row>
        <h3>位置与归属</h3>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="责任部门">
              <el-select v-model="form.deptId" clearable class="full">
                <el-option v-for="item in depts" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放地点">
              <el-select v-model="form.locationId" clearable class="full">
                <el-option v-for="item in locations" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.asset-form {
  margin-top: 20px;
}
.asset-form h3 {
  margin: 8px 0 16px;
  font-size: 14px;
  font-weight: 600;
}
.full {
  width: 100%;
}
</style>

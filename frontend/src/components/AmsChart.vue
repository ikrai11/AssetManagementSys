<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{ option: echarts.EChartsCoreOption }>()
const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function render() {
  if (!el.value) return
  chart ??= echarts.init(el.value)
  chart.setOption(props.option, true)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = undefined
})

watch(() => props.option, () => nextTick(render), { deep: true })
</script>

<template>
  <div ref="el" class="ams-chart" />
</template>

<style scoped>
.ams-chart {
  width: 100%;
  height: 280px;
}
</style>

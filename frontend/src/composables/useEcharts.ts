import * as echarts from 'echarts/core'
import { BarChart, FunnelChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * echarts 按需注册（批次5：趋势折线/柱状 + 占比饼图；批次6：商机漏斗）
 * 模块级注册一次；实例随组件挂载创建、卸载销毁，并监听容器尺寸自适应
 */
echarts.use([
  LineChart, BarChart, PieChart, FunnelChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  CanvasRenderer,
])

export type EchartsOption = Parameters<echarts.ECharts['setOption']>[0]

export function useEcharts(el: Ref<HTMLElement | null>) {
  const chart = ref<echarts.ECharts | null>(null)
  let observer: ResizeObserver | null = null

  function ensureInit(): boolean {
    if (chart.value) return true
    if (!el.value) return false
    chart.value = echarts.init(el.value)
    observer = new ResizeObserver(() => chart.value?.resize())
    observer.observe(el.value)
    return true
  }

  onMounted(() => {
    if (el.value) ensureInit()
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    chart.value?.dispose()
    chart.value = null
  })

  /** 渲染图表；容器尚未挂载（如对话框内）时跳过，调用方需在 DOM 就绪后重试 */
  function render(option: EchartsOption) {
    if (ensureInit()) chart.value?.setOption(option, true)
  }

  return { chart, render }
}

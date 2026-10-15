<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { gsap, motionSkipped, EASE } from '@/composables/motion'

/* 柱状图：柱子从基线生长（结 Crystallize），highlight 柱用斜线填充表示「当前」。
   失败部分叠在柱顶，红色细段。悬停显示准星与读数。 */
const props = withDefaults(defineProps<{
  values: number[]
  labels: string[]
  failed?: number[]
  highlight?: number | null
  height?: number
  unit?: string
  /** columns：带数值刻度、柱顶读数和可选择列；compact 保留原紧凑图。 */
  variant?: 'compact' | 'columns'
  /** 完整标签用于无障碍读数，横轴仍显示 labels 简写。 */
  fullLabels?: string[]
}>(), {
  failed: () => [],
  highlight: null,
  height: 168,
  unit: '',
  variant: 'compact',
  fullLabels: () => [],
})
const emit = defineEmits<{ (event: 'select', index: number): void }>()

const bars = ref<HTMLElement | null>(null)
const hover = ref<number | null>(null)
const max = computed(() => Math.max(1, ...props.values))
const peakIndex = computed(() => props.values.indexOf(Math.max(...props.values)))
const columnScale = computed(() => {
  const raw = max.value * 1.15 / 3
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const ratio = raw / magnitude
  const step = Math.max(1, (ratio <= 1 ? 1 : ratio <= 2 ? 2 : ratio <= 5 ? 5 : 10) * magnitude)
  const limit = Math.ceil(max.value * 1.15 / step) * step
  return { max: limit, ticks: Array.from({ length: limit / step + 1 }, (_, i) => i * step) }
})

function grow() {
  const list = bars.value?.querySelectorAll<HTMLElement>('.bar-col')
  if (!list?.length || motionSkipped()) return
  gsap.fromTo(list, { scaleY: 0 }, { scaleY: 1, duration: 0.8, ease: EASE.settle, stagger: { each: 0.05, from: 'start' }, clearProps: 'transform' })
}

let grown = false
watch(() => props.values, (values) => {
  if (grown || !values.length) return
  grown = true
  nextTick(grow)
}, { immediate: true })
onBeforeUnmount(() => gsap.killTweensOf(bars.value?.querySelectorAll('.bar-col') ?? []))
</script>

<template>
  <div v-if="variant === 'columns'" class="column-chart" role="group" aria-label="可选择柱状图">
    <div class="column-chart-inner" :style="{ minWidth: `${values.length * 48 + 32}px` }">
      <div class="column-chart-plot" :style="{ height: `${height}px` }">
        <div class="column-chart-grid" aria-hidden="true">
          <div v-for="tick in columnScale.ticks" :key="tick" class="column-chart-tick" :class="{ 'is-baseline': tick === 0 }" :style="{ bottom: `${tick / columnScale.max * 100}%` }"><span>{{ tick }}</span></div>
        </div>
        <div class="column-chart-columns" :style="{ gridTemplateColumns: `repeat(${Math.max(1, values.length)}, minmax(0, 1fr))` }">
          <button v-for="(value, index) in values" :key="index" type="button" class="column-chart-slot" :aria-pressed="highlight === index" :aria-label="`${fullLabels[index] ?? labels[index]}，${value}${unit}`" @click="emit('select', index)">
            <span class="column-chart-bar" :style="{ height: `${Math.max(0, value) / columnScale.max * 100}%` }"><span class="column-chart-value tnum">{{ value }}</span></span>
            <span class="column-chart-label">{{ labels[index] }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="bars" :style="{ height: `${height}px` }" @pointerleave="hover = null">
    <div ref="bars" class="bars-plot" role="img" :aria-label="`柱状图，最高 ${Math.max(0, ...values)}${unit}`">
      <div
        v-for="(v, i) in values"
        :key="i"
        :class="['bar-slot', { 'is-hover': hover === i }]"
        @pointerenter="hover = i"
      >
        <span v-if="hover === i || (hover === null && i === peakIndex && v > 0)" class="bar-readout tnum" :style="{ bottom: `calc(${(v / max) * 100}% + 6px)` }">{{ v }}{{ unit }}</span>
        <div
          :class="['bar-col', { 'is-highlight': i === highlight }]"
          :style="{ height: `${Math.max(v ? 4 : 2, (v / max) * 100)}%` }"
        >
          <span v-if="failed[i]" class="bar-failed" :style="{ height: `${Math.min(100, (failed[i] / Math.max(v, 1)) * 100)}%` }" />
        </div>
      </div>
    </div>
    <div class="bars-labels">
      <span v-for="(l, i) in labels" :key="i" :class="{ 'is-highlight': i === highlight }">{{ l }}</span>
    </div>
  </div>
</template>

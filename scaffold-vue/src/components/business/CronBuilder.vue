<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button, Checkbox, Modal, Segmented, TextInput } from '@/components/ui'

/* Cron 表达式生成器：秒/分/时/日/月/周逐字段向导，输出 Quartz 6 段表达式。
   日与周互斥（Quartz 用 ? 占位），周字段 1=周日 … 7=周六。 */

type FieldKey = 'second' | 'minute' | 'hour' | 'day' | 'month' | 'week'
type Mode = 'every' | 'step' | 'values' | 'range'

interface FieldState {
  mode: Mode
  step: number
  from: number
  to: number
  values: number[]
}

const props = withDefaults(defineProps<{ open: boolean; value?: string }>(), { value: '' })
const emit = defineEmits<{ (e: 'close'): void; (e: 'confirm', value: string): void }>()

const FIELDS: Array<{ key: FieldKey; label: string; min: number; max: number }> = [
  { key: 'second', label: '秒', min: 0, max: 59 },
  { key: 'minute', label: '分', min: 0, max: 59 },
  { key: 'hour', label: '时', min: 0, max: 23 },
  { key: 'day', label: '日', min: 1, max: 31 },
  { key: 'month', label: '月', min: 1, max: 12 },
  { key: 'week', label: '周', min: 1, max: 7 },
]

const WEEK_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const MODES: Array<{ label: string; value: Mode }> = [
  { label: '每', value: 'every' },
  { label: '间隔', value: 'step' },
  { label: '指定', value: 'values' },
  { label: '区间', value: 'range' },
]

const current = ref<FieldKey>('second')
const fields = ref<Record<FieldKey, FieldState>>(emptyFields())

function emptyFields(): Record<FieldKey, FieldState> {
  return {
    second: { mode: 'values', step: 1, from: 0, to: 59, values: [0] },
    minute: { mode: 'every', step: 5, from: 0, to: 59, values: [] },
    hour: { mode: 'every', step: 1, from: 0, to: 23, values: [] },
    day: { mode: 'every', step: 1, from: 1, to: 28, values: [] },
    month: { mode: 'every', step: 1, from: 1, to: 12, values: [] },
    week: { mode: 'every', step: 1, from: 1, to: 7, values: [] },
  }
}

function parseField(expr: string, fallback: FieldState): FieldState {
  const text = String(expr ?? '').trim()
  if (!text || text === '*' || text === '?') return { ...fallback, mode: 'every', values: [] }
  if (text.startsWith('*/')) return { ...fallback, mode: 'step', step: Number(text.slice(2)) || fallback.step }
  if (text.includes('-')) {
    const [from, to] = text.split('-').map(Number)
    return { ...fallback, mode: 'range', from: Number.isFinite(from) ? from : fallback.from, to: Number.isFinite(to) ? to : fallback.to }
  }
  if (text.includes(',')) {
    return { ...fallback, mode: 'values', values: text.split(',').map(Number).filter(Number.isFinite) }
  }
  const single = Number(text)
  return Number.isFinite(single) ? { ...fallback, mode: 'values', values: [single] } : { ...fallback, mode: 'every', values: [] }
}

function parse(cron: string) {
  const parts = String(cron ?? '').trim().split(/\s+/)
  if (parts.length < 6 || parts.length > 7) return emptyFields()
  const base = emptyFields()
  const next = { ...base }
  ;(['second', 'minute', 'hour', 'day', 'month', 'week'] as FieldKey[]).forEach((key, index) => {
    next[key] = parseField(parts[index], base[key])
  })
  // 日与周互斥：Quartz 里同侧用 ? 占位
  if (parts[5] && parts[5] !== '?' && parts[3] === '?') next.day.mode = 'every'
  return next
}

watch(() => props.open, (open) => {
  if (!open) return
  fields.value = parse(props.value)
  current.value = 'second'
}, { immediate: true })

const fieldDef = computed(() => FIELDS.find(field => field.key === current.value) ?? FIELDS[0])
const state = computed(() => fields.value[current.value])

function setMode(mode: Mode) {
  fields.value[current.value] = { ...state.value, mode }
}

function setStep(delta: number) {
  const max = fieldDef.value.max
  const next = Math.min(max, Math.max(1, (state.value.step || 1) + delta))
  fields.value[current.value] = { ...state.value, step: next }
}

function setRange(which: 'from' | 'to', value: string | number | null) {
  const n = Number(value)
  if (!Number.isFinite(n)) return
  const max = fieldDef.value.max
  const min = fieldDef.value.min
  fields.value[current.value] = { ...state.value, [which]: Math.min(max, Math.max(min, Math.round(n))) }
}

function toggleValue(value: number, checked: boolean) {
  const values = new Set(state.value.values)
  if (checked) values.add(value)
  else values.delete(value)
  fields.value[current.value] = { ...state.value, values: [...values].sort((a, b) => a - b) }
}

function setValuesText(text: string) {
  const values = text.split(/[,，\s]+/).map(Number).filter(Number.isFinite)
  fields.value[current.value] = { ...state.value, values }
}

const valuesText = computed({
  get: () => state.value.values.join(','),
  set: setValuesText,
})

function exprOf(field: FieldState): string {
  switch (field.mode) {
    case 'every': return '*'
    case 'step': return `*/${Math.max(1, field.step)}`
    case 'range': return `${field.from}-${field.to}`
    case 'values': return field.values.length ? [...field.values].sort((a, b) => a - b).join(',') : '*'
  }
}

const weekActive = computed(() => fields.value.week.mode !== 'every' || fields.value.week.values.length > 0)

const cron = computed(() => {
  const f = fields.value
  const day = weekActive.value ? '?' : exprOf(f.day)
  const week = weekActive.value ? exprOf(f.week) : '?'
  return [exprOf(f.second), exprOf(f.minute), exprOf(f.hour), day, exprOf(f.month), week].join(' ')
})

const pad = (n: number) => String(n).padStart(2, '0')

const description = computed(() => {
  const f = fields.value
  const time = (() => {
    if (f.hour.mode === 'values' && f.hour.values.length === 1 && f.minute.mode === 'values' && f.minute.values.length === 1) {
      return `${pad(f.hour.values[0])}:${pad(f.minute.values[0])}`
    }
    return ''
  })()
  if (weekActive.value) {
    if (f.week.mode === 'values' && f.week.values.length) {
      return `每${f.week.values.map(v => WEEK_NAMES[v - 1]).join('、')}${time ? ` ${time}` : ''}`
    }
    return `每周（${exprOf(f.week)}）${time ? ` ${time}` : ''}`
  }
  if (f.day.mode === 'values' && f.day.values.length === 1) {
    return `每月 ${f.day.values[0]} 号${time ? ` ${time}` : ''}`
  }
  if (f.day.mode === 'step') return `每 ${f.day.step} 天${time ? ` ${time}` : ''}`
  if (f.minute.mode === 'step') return `每 ${f.minute.step} 分钟`
  if (time) return `每天 ${time}`
  if (f.minute.mode === 'every' && f.hour.mode === 'every') return '每分钟'
  return '自定义计划'
})

function confirm() {
  emit('confirm', cron.value)
}
</script>

<template>
  <Modal :open="open" title="Cron 表达式生成器" sub="逐字段选择，底部实时预览；应用后仍可手动微调" :width="620" @close="emit('close')">
    <div class="cron-builder">
      <Segmented v-model="current" label="字段" :options="FIELDS.map(field => ({ label: field.label, value: field.key }))" />

      <div class="cron-panel">
        <div class="cron-modes">
          <button
            v-for="mode in MODES"
            :key="mode.value"
            type="button"
            class="cron-mode"
            :class="{ 'is-active': state.mode === mode.value }"
            @click="setMode(mode.value)"
          >{{ mode.label }}</button>
          <span class="cron-hint">{{ fieldDef.min }}–{{ fieldDef.max }}，当前：{{ exprOf(state) }}</span>
        </div>

        <div v-if="state.mode === 'every'" class="cron-row soft">每{{ fieldDef.label }}（{{ current === 'week' ? '每天' : '*' }}）</div>

        <div v-else-if="state.mode === 'step'" class="cron-row">
          <span>每</span>
          <div class="cron-stepper">
            <button type="button" @click="setStep(-1)">−</button>
            <span>{{ state.step }}</span>
            <button type="button" @click="setStep(1)">＋</button>
          </div>
          <span>{{ fieldDef.label }}执行一次</span>
        </div>

        <div v-else-if="state.mode === 'range'" class="cron-row">
          <span>从</span>
          <TextInput :model-value="String(state.from)" type="number" style="width: 90px" @update:model-value="setRange('from', $event)" />
          <span>到</span>
          <TextInput :model-value="String(state.to)" type="number" style="width: 90px" @update:model-value="setRange('to', $event)" />
          <span>{{ fieldDef.label }}</span>
        </div>

        <div v-else class="cron-row is-top">
          <template v-if="current === 'week'">
            <Checkbox
              v-for="(name, index) in WEEK_NAMES"
              :key="name"
              :model-value="state.values.includes(index + 1)"
              :label="name"
              @update:model-value="toggleValue(index + 1, $event)"
            />
          </template>
          <template v-else>
            <TextInput v-model="valuesText" placeholder="用逗号分隔，例如 0,15,30,45" style="width: 100%" />
          </template>
        </div>
      </div>

      <div class="cron-preview">
        <code>{{ cron }}</code>
        <span class="soft">{{ description }}</span>
      </div>
    </div>

    <template #footer>
      <div class="cron-actions">
        <Button variant="ghost" @click="emit('close')">取消</Button>
        <Button variant="primary" @click="confirm">应用</Button>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.cron-builder { display: flex; flex-direction: column; gap: 16px; }
.cron-panel {
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  padding: 16px;
  min-height: 150px;
  background: var(--card-bg, #fff);
}
.cron-modes { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.cron-mode {
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--input-bg, #fff);
  color: var(--ink-muted);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
}
.cron-mode.is-active { border-color: var(--blue); color: var(--blue); background: color-mix(in srgb, var(--blue) 8%, transparent); }
.cron-hint { margin-left: auto; font-size: 12px; color: var(--ink-faint); }
.cron-row { display: flex; align-items: center; gap: 10px; min-height: 38px; font-size: 13px; }
.cron-row.is-top { align-items: flex-start; flex-wrap: wrap; }
.cron-stepper { display: inline-flex; align-items: center; border: 1px solid var(--line); border-radius: var(--r-sm); overflow: hidden; }
.cron-stepper button { width: 32px; height: 34px; border: 0; background: var(--hover-bg); color: var(--ink); cursor: pointer; font-size: 15px; }
.cron-stepper span { min-width: 46px; text-align: center; font-variant-numeric: tabular-nums; }
.cron-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--hover-bg);
  flex-wrap: wrap;
}
.cron-preview code { font-size: 13.5px; letter-spacing: .02em; }
.cron-actions { display: flex; justify-content: flex-end; gap: 10px; }
</style>

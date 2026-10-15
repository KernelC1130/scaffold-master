<script lang="ts">
export interface ArchiveSheet {
  title: string
  subtitle: string
  icon: string
  /** 分类色编号：沙金、天蓝、丁香、薄荷；不表示风险等级。 */
  tone: 1 | 2 | 3 | 5
  rows: string[]
}
</script>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from '@/components/Icon.vue'
import { turnStackCards } from '@/composables/motion'

const props = defineProps<{ sheets: ArchiveSheet[]; activeIndex: number }>()
const stage = ref<HTMLElement | null>(null)
let transition: ReturnType<typeof turnStackCards>
let previousIndex = props.activeIndex
const stackOrder = ref([0, 1, 2, 3])
let observer: ResizeObserver | undefined
let reducedQuery: MediaQueryList | undefined

function select(instant = false) {
  const root = stage.value
  if (!root) return
  transition?.kill()
  const cards = Array.from(root.querySelectorAll<HTMLElement>('.archive-sheet'))
  const height = root.clientHeight
  const targets = stackOrder.value.map((index, rank) => {
    const element = cards[index]!
    return {
      element,
      detail: element.querySelector<HTMLElement>('.archive-detail'),
      preview: element.querySelector<HTMLElement>('.archive-preview'),
      xPercent: [0, -6, 6, -3][rank] ?? 0,
      y: [0, height * -.16, height * -.28, height * -.38][rank] ?? 0,
      rotation: [0, -3, 3, 2][rank] ?? 0,
    }
  })
  transition = turnStackCards(targets, previousIndex, props.activeIndex, instant || height === 0)
  previousIndex = props.activeIndex
}

const settle = () => select(true)
watch(() => props.activeIndex, () => {
  const index = stackOrder.value.indexOf(props.activeIndex)
  if (index > 0) stackOrder.value = [props.activeIndex, ...stackOrder.value.filter(item => item !== props.activeIndex)]
  select()
}, { flush: 'post' })
onMounted(() => {
  settle()
  observer = new ResizeObserver(settle)
  if (stage.value) observer.observe(stage.value)
  reducedQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedQuery.addEventListener('change', settle)
})
onBeforeUnmount(() => {
  transition?.kill()
  observer?.disconnect()
  reducedQuery?.removeEventListener('change', settle)
})
</script>

<template>
  <div ref="stage" class="archive-stage">
    <div v-for="(sheet, index) in sheets" :key="sheet.title" class="archive-sheet" :aria-hidden="index !== activeIndex" :data-sheet-index="index" :style="{ '--sheet-tint': `var(--cat-${sheet.tone}-bg)`, '--sheet-accent': `var(--cat-${sheet.tone}-ink)` }">
      <div class="archive-preview" aria-hidden="true">
        <div class="archive-layer-title"><Icon :name="sheet.icon" :size="15" /><span>{{ sheet.title }}</span></div>
        <span class="archive-layer-rule" />
      </div>
      <div class="archive-detail">
        <div class="archive-header">
          <span class="archive-icon"><Icon :name="sheet.icon" :size="25" /></span>
          <div><h2>{{ sheet.title }}</h2><p>{{ sheet.subtitle }}</p></div>
          <span class="archive-point" aria-hidden="true" />
        </div>
        <div v-for="row in sheet.rows" :key="row" class="archive-row">
          <span>{{ row }}</span><span class="archive-field" aria-hidden="true" /><Icon name="check" :size="14" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.archive-stage { position: relative; isolation: isolate; container-type: size; width: 100%; max-width: 620px; height: clamp(230px, calc(100dvh - 618px), 340px); margin: 0 auto; }
.archive-sheet { position: absolute; visibility: hidden; top: 32%; left: 15%; width: 70%; height: 65%; max-height: 196px; padding: 18px 24px; border: 1px solid color-mix(in srgb, var(--sheet-accent) 18%, var(--line-strong)); border-radius: var(--r-lg); background: linear-gradient(145deg, color-mix(in srgb, var(--sheet-tint) 86%, transparent), color-mix(in srgb, var(--sheet-tint) 58%, transparent)), var(--surface-solid); box-shadow: var(--e2), var(--edge); transform-origin: 50% 0%; backface-visibility: hidden; }
.archive-sheet[data-stack-rank="0"] { box-shadow: var(--e3), var(--edge-strong); }
.archive-preview { position: absolute; inset: 18px 24px; pointer-events: none; }
.archive-layer-title { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--sheet-accent); }
.archive-layer-rule { display: block; height: 1px; margin-top: 15px; background: var(--line); }
.archive-header { display: flex; align-items: center; gap: 13px; padding-bottom: 12px; border-bottom: 1px solid var(--line); }
.archive-icon { display: grid; place-items: center; color: var(--sheet-accent); flex: none; }
.archive-header h2 { font-size: 16px; line-height: 1.4; font-weight: 650; }
.archive-header p { margin-top: 4px; font-size: 11px; line-height: 1.5; color: var(--ink-soft); }
.archive-point { flex: none; margin-left: auto; width: 5px; height: 5px; border-radius: 50%; background: var(--sheet-accent); }
.archive-row { display: flex; align-items: center; gap: 20px; margin-top: 12px; font-size: 12px; line-height: 1.5; color: var(--ink-muted); }
.archive-row > span:first-child { flex: none; }
.archive-field { flex: 1; height: 4px; border-radius: 4px; background: var(--line); }
.archive-row > .icon { color: var(--sheet-accent); }
@container (max-height: 275px) {
  .archive-sheet { padding: 12px 18px; }
  .archive-preview { inset: 12px 18px; }
  .archive-header { padding-bottom: 9px; gap: 10px; }
  .archive-header h2 { font-size: 14px; }
  .archive-header p { margin-top: 2px; }
  .archive-row { margin-top: 8px; font-size: 11px; }
  .archive-layer-title { font-size: 11px; }
}
</style>

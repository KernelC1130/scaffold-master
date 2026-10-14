<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from '@/components/Icon.vue'
import BrandMark from '@/components/business/BrandMark.vue'
import { motionSkipped } from '@/composables/motion'

const selected = ref(0)
const stages = [
  { name: '资产管理', icon: 'database', brief: '厘清资产全貌', tone: 2, mark: '✓', cmd: 'vulnctl assets --scan', out: 'inventory synced · 0 drift', spin: 'streaming', dots: 21 },
  { name: '漏洞分级', icon: 'alert', brief: '聚焦关键风险', tone: 1, mark: '▲', cmd: 'vulnctl vuln --triage', out: 'severity mapped · flagged for review', spin: 'scoring', dots: 23 },
  { name: '修复工单', icon: 'tool', brief: '让修复有序推进', tone: 3, mark: '→', cmd: 'vulnctl fix --track', out: 'patch in progress · owner assigned', spin: 'syncing', dots: 23 },
  { name: '复测闭环', icon: 'role', brief: '让结果可追溯', tone: 5, mark: '✓', cmd: 'vulnctl verify --close', out: 'loop closed · risk cleared', spin: 'watching', dots: 22 },
]

/* ── 中部终端：与底部四步联动，逐字打出当前阶段的品牌化命令；无交互时自动轮播，点击后交还控制权 ── */
const typedCmd = ref('')
const typedOut = ref('')
const showLine = ref(false)
const spinChar = ref('⠙')
let typeTimer = 0
let autoTimer = 0
let spinTimer = 0

const SPIN_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']

function renderStage(index: number) {
  const stage = stages[index]
  if (motionSkipped()) {
    typedCmd.value = stage.cmd
    typedOut.value = stage.out
    showLine.value = true
    spinChar.value = '⠙'
    return
  }
  typedCmd.value = ''
  typedOut.value = ''
  showLine.value = false
  window.clearInterval(typeTimer)
  let i = 0
  typeTimer = window.setInterval(() => {
    typedCmd.value = stage.cmd.slice(0, i += 1)
    if (i >= stage.cmd.length) {
      window.clearInterval(typeTimer)
      typedOut.value = stage.out
      window.setTimeout(() => { showLine.value = true }, 260)
    }
  }, 34)
}

watch(selected, renderStage)
onMounted(() => {
  renderStage(selected.value)
  let frame = 0
  spinTimer = window.setInterval(() => { spinChar.value = SPIN_FRAMES[(frame += 1) % SPIN_FRAMES.length] }, 90)
  autoTimer = window.setInterval(() => { selected.value = (selected.value + 1) % stages.length }, 3800)
})
onBeforeUnmount(() => {
  window.clearInterval(typeTimer)
  window.clearInterval(autoTimer)
  window.clearInterval(spinTimer)
})

function pickStage(index: number) {
  selected.value = index
  window.clearInterval(autoTimer)
}
</script>

<template>
        <section class="brand-panel" aria-labelledby="brand-title">
          <div class="brand-top">
            <div class="platform-brand">
              <BrandMark />
              <div><div class="brand-name">资产漏洞管理平台</div><div class="brand-sub">VULN MANAGER</div></div>
            </div>
            <span class="brand-edition"><Icon name="shield" :size="13" />风险治理 · 全程可循</span>
          </div>

          <div class="brand-copy">
            <div class="intro-label"><span />让风险治理，更进一步</div>
            <h1 id="brand-title">看清资产风险，<br><span>推动漏洞闭环。</span></h1>
            <p>从资产梳理、漏洞发现到修复复测，<br class="desktop-break">让每一项风险都有迹可循。</p>
          </div>

          <div class="stage-box" aria-hidden="true">
            <div class="term">
              <div class="term-chrome">
                <span class="term-dots"><i /><i /><i /></span>
                <span class="term-tag">vulnctl — live</span>
              </div>
              <code class="term-cmd"><span class="prompt">$</span> {{ typedCmd }}<span class="cursor" :style="{ background: `var(--cat-${stages[selected].tone}-ink)` }" /></code>
              <code class="term-out" :style="{ color: `var(--cat-${stages[selected].tone}-ink)` }"><span class="out-mark">{{ stages[selected].mark }}</span>{{ typedOut }}</code>
              <code class="term-line" :class="{ 'is-in': showLine }"><span class="spin-char" :style="{ color: `var(--cat-${stages[selected].tone}-ink)` }">{{ spinChar }}</span> {{ stages[selected].spin }} <span class="dots-run">{{ '·'.repeat(stages[selected].dots) }}</span> live</code>
            </div>
          </div>
          <div class="journey" role="group" aria-label="漏洞治理流程预览">
            <template v-for="(stage, index) in stages" :key="stage.name">
              <button type="button" :aria-pressed="selected === index" :class="['journey-step', { 'is-active': selected === index }]" :style="{ '--step-accent': `var(--cat-${stage.tone}-ink)` }" @click="pickStage(index)">
                <span class="step-icon"><Icon :name="stage.icon" :size="18" /></span>
                <strong>{{ stage.name }}</strong><small>{{ stage.brief }}</small>
              </button>
              <span v-if="index < stages.length - 1" class="step-arrow" aria-hidden="true"><Icon name="chevronRight" :size="13" /></span>
            </template>
          </div>
          <footer class="brand-footer"><span>© 2026 资产漏洞管理平台</span><span class="footer-sign"><Icon name="shield" :size="12" />资产有序 · 风险可控</span></footer>
        </section>
</template>

<style scoped>
.brand-panel { position: relative; isolation: isolate; display: flex; flex-direction: column; padding: 32px clamp(28px, 3vw, 50px) 22px; border: 1px solid var(--glass-line-soft); border-radius: 28px; background: radial-gradient(ellipse at 67% 55%, var(--ambient-b), transparent 65%), radial-gradient(ellipse at 4% 96%, var(--ambient-a), transparent 52%), var(--canvas); box-shadow: var(--edge), var(--e1); min-width: 0; }
.brand-panel::before { content: ''; position: absolute; z-index: -1; inset: 0; border-radius: inherit; background-image: radial-gradient(var(--line-strong) .8px, transparent .8px); background-size: 20px 20px; mask-image: radial-gradient(ellipse at 60% 56%, black, transparent 66%); pointer-events: none; }
.brand-top { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.platform-brand { display: flex; align-items: center; gap: 11px; }
.brand-edition { display: flex; align-items: center; gap: 5px; color: var(--ink-soft); font-size: 10px; white-space: nowrap; }
.brand-copy { padding-top: clamp(24px, 3vh, 48px); }
.intro-label { display: flex; align-items: center; gap: 8px; color: var(--ink-muted); font-size: 11px; letter-spacing: .04em; margin-bottom: 14px; }
.intro-label > span { width: 17px; height: 2px; border-radius: 2px; background: var(--blue); }
.brand-copy h1 { font-size: clamp(34px, 3.15vw, 54px); line-height: 1.2; font-weight: 730; letter-spacing: -.04em; }
.brand-copy h1 > span { color: var(--blue); }
.brand-copy p { font-size: 13px; line-height: 1.9; color: var(--ink-muted); margin-top: 18px; }
.stage-box { flex: 1; min-height: calc(clamp(230px, calc(100dvh - 618px), 340px) + 50px); display: flex; align-items: center; }
/* 纸面终端：与流程条同材质（浅底 + 细线 + 轻阴影），光标与结果行随阶段分类色；流内左对齐，并入标题内容轴 */
.term { width: min(620px, 100%);
        background: var(--surface); border: 1px solid var(--line); border-radius: 16px; padding: 15px 22px 16px;
        box-shadow: var(--e1); }
.term-chrome { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; }
.term-dots { display: flex; gap: 5px; }
.term-dots i { width: 7.5px; height: 7.5px; border-radius: 50%; background: rgba(16, 20, 24, .1); }
.term-dots i:first-child { background: var(--lime); }
.term-tag { font-family: Consolas, ui-monospace, monospace; font-size: 10px; letter-spacing: .06em; color: var(--ink-faint); }
.term code { display: block; font-family: Consolas, ui-monospace, monospace; font-size: 13.5px; line-height: 2.15; color: var(--ink); white-space: nowrap; overflow: hidden; }
.term .prompt { color: var(--ink-faint); }
.term .out-mark { margin-right: 7px; }
.term .cursor { display: inline-block; width: 6.5px; height: 13px; vertical-align: -2px; margin-left: 3px; animation: term-blink 1.1s steps(1) infinite; }
.term-line { color: var(--ink-faint); opacity: 0; transition: opacity .5s var(--ease-premium); }
.term-line.is-in { opacity: 1; }
.term-line .spin-char { display: inline-block; min-width: 14px; }
@keyframes term-blink { 50% { opacity: 0; } }
@media (prefers-reduced-motion: reduce) { .term .cursor { animation: none; } }
.journey { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr) 24px) minmax(0, 1fr); padding: 5px; border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--groove-bg); column-gap: 8px; }
.journey-step { min-width: 0; text-align: left; padding: 10px 12px; border: 1px solid transparent; border-radius: var(--r-md); color: var(--ink-muted); transition: background .2s, border-color .2s, color .2s; }
.journey-step:hover { background: var(--hover-bg); }
.journey-step.is-active { background: var(--surface); border-color: var(--glass-line); color: var(--step-accent); box-shadow: var(--e1), var(--edge); }
.step-icon { display: inline-flex; margin-bottom: 6px; color: var(--step-accent); }
.journey-step strong { display: block; font-size: 12px; font-weight: 650; }
.journey-step small { display: block; color: var(--ink-soft); font-size: 10px; margin-top: 4px; white-space: nowrap; }
.step-arrow { display: grid; place-items: center; color: var(--ink-faint); pointer-events: none; }
.brand-footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding-top: 16px; color: var(--ink-faint); font-size: 10px; }
.footer-sign { display: flex; align-items: center; gap: 5px; }

@container (min-width: 1600px) {
  .brand-panel { padding: 38px 54px 26px; }
  .brand-copy { padding-top: 48px; }
  .brand-copy p { font-size: 15px; }
  .journey-step strong { font-size: 13px; }
  .journey-step small { font-size: 12px; }
}
@container (max-width: 1160px) {
  .brand-edition, .journey-step small { display: none; }
  .brand-panel { padding: 26px 24px 20px; }
  .journey { grid-template-columns: repeat(3, minmax(0, 1fr) 16px) minmax(0, 1fr); column-gap: 4px; }
  .journey-step { padding: 12px 8px; }
  .brand-copy h1 { font-size: 36px; }
}
@container (max-width: 900px) {
  .brand-panel { border: 0; border-radius: 0; box-shadow: none; padding: 26px 26px 28px; }
  .brand-copy { padding-top: 28px; }
  .brand-copy h1 { font-size: 30px; line-height: 1.3; }
  .brand-copy p { font-size: 12px; margin-top: 10px; }
  .brand-copy p br { display: none; }
  .intro-label, .stage-box, .journey, .brand-footer { display: none; }
  .brand-name { font-size: 14px; }
}
@container (max-width: 350px) {
  .brand-panel { padding-inline: 22px; }
  .brand-copy h1 { font-size: 27px; }
}
@media (max-height: 860px) {
  @container (min-width: 901px) {
    .brand-panel { padding-top: 26px; }
    .brand-copy { padding-top: 24px; }
    .brand-copy h1 { font-size: 38px; }
    .intro-label, .journey-step small { display: none; }
    .brand-copy p { margin-top: 12px; }
  }
}
</style>

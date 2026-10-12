<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import Segmented from '@/components/ui/Segmented.vue'
import Avatar from '@/components/ui/Avatar.vue'
import Tag from '@/components/ui/Tag.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Metric from '@/components/ui/Metric.vue'
import DeltaPill from '@/components/ui/DeltaPill.vue'
import SectionLabel from '@/components/ui/SectionLabel.vue'
import TrendChart from '@/components/charts/TrendChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import TickRing from '@/components/charts/TickRing.vue'
import Swimlane from '@/components/charts/Swimlane.vue'
import { portalApi, systemApi, vulnApi, type DashboardSummary, type OperTimeline } from '@/api'
import { currentUser, navGroups, pathOf } from '@/composables/auth'
import { toastError } from '@/composables/toast'
import { EASE, firstTimeInSession, gsap, motionSkipped, trackGlow } from '@/composables/motion'
import { hasPermission } from '@/directives/permission'

/* ============================================================
   工作台 · 主次布局：8 列主卡（登录脉搏）+ 4 列侧块，
   下排柱状 / 刻度环 / 气泡，底部当日操作泳道与最近登录。
   ============================================================ */
type Range = '24h' | '7d' | '30d'

const router = useRouter()
const range = ref<Range>('24h')
const summary = ref<DashboardSummary | null>(null)
const timeline = ref<OperTimeline | null>(null)
const loading = ref(true)

const SCOPE_LABEL: Record<string, string> = {
  ALL: '全部数据', DEPT_AND_CHILD: '本部门及以下', DEPT: '本部门', SELF: '仅本人', CUSTOM: '自定义',
}
const RANGE_TEXT: Record<Range, string> = { '24h': '近 24 小时', '7d': '近 7 天', '30d': '近 30 天' }

const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 6 ? '夜深了' : h < 12 ? '上午好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})
const now = new Date()
const dateStamp = `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 · 星期${'日一二三四五六'[now.getDay()]}`

const statOf = (key: string) => {
  const s = summary.value?.stats.find(item => item.key === key)
  return s ? { value: Number(s.value ?? 0), delta: Number(s.delta ?? 0), up: Boolean(s.up), fresh: Boolean(s.fresh), label: String(s.label ?? ''), sub: String(s.sub ?? '') } : null
}
const online = computed(() => statOf('online'))
const api = computed(() => statOf('api'))

/* ── 主卡：登录脉搏 ── */
const trend = computed(() => summary.value?.trend ?? [])
const loginTotal = computed(() => trend.value.reduce((a, b) => a + b, 0))
const loginPrev = computed(() => Number(summary.value?.trendPrev ?? 0))
const loginFresh = computed(() => Boolean(summary.value) && !loginPrev.value && loginTotal.value > 0)
const loginDelta = computed(() => {
  if (!summary.value || !loginPrev.value) return summary.value ? 0 : null
  return Math.round(((loginTotal.value - loginPrev.value) / loginPrev.value) * 1000) / 10
})
const trendLabels = computed(() => {
  const n = trend.value.length
  const base = new Date()
  if (range.value === '24h') {
    return trend.value.map((_, i) => `${String((base.getHours() - (n - 1 - i) + 24) % 24).padStart(2, '0')}:00`)
  }
  return trend.value.map((_, i) => {
    const d = new Date(base)
    d.setDate(base.getDate() - (n - 1 - i))
    return `${d.getMonth() + 1}/${d.getDate()}`
  })
})
const peak = computed(() => Math.max(0, ...trend.value))
const avg = computed(() => trend.value.length ? Math.round((loginTotal.value / trend.value.length) * 10) / 10 : 0)

/* ── 下排 ── */
const WEEK = '日一二三四五六'
const daily = computed(() => summary.value?.dailyOps ?? [])
const dailyLabels = computed(() => daily.value.map((d, i) => i === daily.value.length - 1 ? '今天' : `周${WEEK[new Date(`${d.date}T00:00:00`).getDay()]}`))
const dailyTotal = computed(() => daily.value.reduce((a, b) => a + Number(b.total), 0))
/* 数据权限分布：按人数降序的横向占比条，比气泡更易读 */
const scopeItems = computed(() => {
  const list = (summary.value?.roleDist ?? []).map(r => ({ label: SCOPE_LABEL[r.label] ?? r.label, value: Number(r.value) })).sort((a, b) => b.value - a.value)
  const total = list.reduce((a, b) => a + b.value, 0) || 1
  const max = Math.max(1, ...list.map(i => i.value))
  return list.map((i, k) => ({ ...i, pct: Math.round((i.value / total) * 100), w: (i.value / max) * 100, cat: (k % 5) + 1 }))
})

/* ── 跨页入口：只在当前账号有对应菜单时出现 ── */
const loginLogPath = computed(() => pathOf('monitor/loginlog/index'))
const operLogPath = computed(() => pathOf('monitor/operlog/index'))
const canTimeline = computed(() => Boolean(operLogPath.value) && hasPermission('monitor:operlog:list'))
const canVuln = computed(() => hasPermission('vuln:vulnerability:list'))
const shortcuts = computed(() => navGroups.value
  .filter(g => !g.single)
  .flatMap(g => g.items.map(i => ({ ...i, group: g.label })))
  .slice(0, 6))

async function load() {
  loading.value = true
  try {
    summary.value = await portalApi.dashboard.summary({ range: range.value })
  } catch (error) {
    toastError(error, '工作台数据加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTimeline() {
  if (!canTimeline.value) return
  try {
    timeline.value = await systemApi.operLogs.timeline()
  } catch {
    timeline.value = null
  }
}

/* ── 安全态势（漏洞平台）：统计接口无权限或失败时静默隐藏整块 ── */
const vulnSummary = ref<{ totalAssets: number; openVulns: number; highRiskOpen: number; weekTasks: number } | null>(null)
const vulnMonthly = ref<Array<{ name: string; value: number }>>([])
const vulnRiskRaw = ref<Array<{ name: string; value: number }>>([])
const vulnTop = ref<Array<{ name: string; value: number }>>([])
const riskLabels = ['低', '中', '高']
const riskValues = computed(() => riskLabels.map(l => vulnRiskRaw.value.find(i => i.name === l)?.value ?? 0))
const topTotal = computed(() => vulnTop.value.reduce((a, b) => a + b.value, 0) || 1)
const topItems = computed(() => vulnTop.value.map((i, k) => ({ ...i, w: (i.value / Math.max(1, ...vulnTop.value.map(x => x.value))) * 100, pct: Math.round((i.value / topTotal.value) * 100), cat: (k % 5) + 1 })))
const pageDesc = computed(() => canVuln.value ? '安全态势、系统运行概况与今日操作轨迹。' : '系统运行概况、登录脉搏与今日操作轨迹。')

async function loadVuln() {
  if (!canVuln.value) return
  try {
    const [s, monthly, risk, top] = await Promise.all([
      vulnApi.statistics.summary(),
      vulnApi.statistics.vulnByMonth(),
      vulnApi.statistics.vulnByRiskLevel(),
      vulnApi.statistics.topRiskAssets(),
    ])
    vulnSummary.value = s
    vulnMonthly.value = monthly
    vulnRiskRaw.value = risk
    vulnTop.value = top
  } catch {
    /* 态势区块失败不影响工作台其余部分 */
  }
}

function refresh() {
  load()
  loadTimeline()
}

function openLane(module: string) {
  if (operLogPath.value) router.push({ path: operLogPath.value, query: { keyword: module } })
}

/* ── 开场编排：本会话首次进入，卡片从中心弹簧飞出落位 ── */
const grid = ref<HTMLElement | null>(null)
function playEntrance() {
  if (!firstTimeInSession('dashboard') || motionSkipped()) return
  nextTick(() => {
    const box = grid.value?.getBoundingClientRect()
    const cards = grid.value ? [...grid.value.querySelectorAll<HTMLElement>('.bento')] : []
    if (!box || !cards.length) return
    const cx = box.left + box.width / 2
    const cy = box.top + Math.min(box.height / 2, 300)
    cards.forEach((el, i) => {
      const r = el.getBoundingClientRect()
      gsap.from(el, {
        x: cx - (r.left + r.width / 2),
        y: cy - (r.top + r.height / 2),
        scale: 0.82,
        opacity: 0,
        duration: 0.72,
        ease: EASE.settle,
        delay: 0.05 + i * 0.05,
        clearProps: 'transform,opacity',
      })
    })
  })
}

watch(range, load)

onMounted(() => {
  load()
  loadTimeline()
  loadVuln()
  playEntrance()
})
</script>

<template>
  <div class="page dashboard">
    <header class="page-head reveal">
      <div class="page-head-main">
        <span class="page-date">{{ dateStamp }}</span>
        <h1 class="page-title">{{ greeting }}，{{ currentUser?.nickname }}</h1>
        <p class="page-desc">{{ pageDesc }}</p>
      </div>
    </header>

    <!-- 安全态势（漏洞平台）：置顶主位，无漏洞菜单权限时整块隐藏 -->
    <div v-if="canVuln" ref="grid" class="bento-grid">
      <section class="bento bento-side">
        <div class="side-top">
          <span class="side-label">资产总数</span>
          <span class="side-ico m-groove"><Icon name="database" :size="16" /></span>
        </div>
        <Metric :value="vulnSummary?.totalAssets" unit="台" size="l" />
        <div class="side-foot"><span class="muted">纳管资产</span></div>
      </section>
      <section class="bento bento-side">
        <div class="side-top">
          <span class="side-label">未闭合漏洞</span>
          <span class="side-ico m-groove"><Icon name="alert" :size="16" /></span>
        </div>
        <Metric :value="vulnSummary?.openVulns" unit="个" size="l" />
        <div class="side-foot"><span class="muted">新发现 + 修复中</span></div>
      </section>
      <section class="bento bento-side">
        <div class="side-top">
          <span class="side-label">高风险未闭合</span>
          <span class="side-ico m-groove"><Icon name="ban" :size="16" /></span>
        </div>
        <Metric :value="vulnSummary?.highRiskOpen" unit="个" size="l" />
        <div class="side-foot"><span class="muted">风险敞口</span></div>
      </section>
      <section class="bento bento-side">
        <div class="side-top">
          <span class="side-label">本周新工单</span>
          <span class="side-ico m-groove"><Icon name="board" :size="16" /></span>
        </div>
        <Metric :value="vulnSummary?.weekTasks" unit="单" size="l" />
        <div class="side-foot"><span class="muted">近 7 天</span></div>
      </section>

      <section class="bento bento-third">
        <div class="bento-head">
          <SectionLabel title="漏洞发现趋势" />
          <span class="bento-meta">按月</span>
        </div>
        <TrendChart v-if="vulnMonthly.length" :values="vulnMonthly.map(i => i.value)" :labels="vulnMonthly.map(i => i.name)" :height="132" unit=" 个" tone="blue" />
        <EmptyState v-else title="暂无漏洞数据" icon="chart" />
      </section>

      <section class="bento bento-third">
        <div class="bento-head">
          <SectionLabel title="风险等级分布" />
          <span class="bento-meta">全部漏洞</span>
        </div>
        <BarChart :values="riskValues" :labels="riskLabels" :highlight="riskValues.length ? riskValues.length - 1 : null" unit=" 个" />
      </section>

      <section class="bento bento-third">
        <div class="bento-head">
          <SectionLabel title="高危资产 TOP5" />
          <span class="bento-meta">按高危漏洞数</span>
        </div>
        <ul v-if="topItems.length" class="share-list">
          <li v-for="item in topItems" :key="item.name" :class="`cat-${item.cat}`">
            <span class="share-label">{{ item.name }}</span>
            <span class="share-track"><i :style="{ width: `${item.w}%` }" /></span>
            <span class="share-value tnum">{{ item.value }}<small> 个 · {{ item.pct }}%</small></span>
          </li>
        </ul>
        <EmptyState v-else title="暂无高风险漏洞" icon="check" />
      </section>
    </div>

    <div ref="grid" v-else class="bento-grid">
      <!-- 01 主卡：登录脉搏 -->
      <section class="bento bento-hero m-glow" @pointermove="trackGlow">
        <span class="glow-layer" aria-hidden="true" />
        <div class="hero-head">
          <SectionLabel title="登录脉搏" />
          <Segmented v-model="range" label="时间范围" :options="[{ label: '24 小时', value: '24h' }, { label: '7 天', value: '7d' }, { label: '30 天', value: '30d' }]" />
        </div>
        <div class="hero-body">
          <div class="hero-figure">
            <Metric :value="summary ? loginTotal : null" unit="次" size="xl" />
            <div class="hero-delta">
              <DeltaPill :value="loginDelta" :fresh="loginFresh" label="较上一等长周期" />
              <span class="faint">较上一周期</span>
            </div>
            <dl class="hero-readouts">
              <div><dt>峰值</dt><dd class="tnum">{{ peak }}</dd></div>
              <div><dt>均值</dt><dd class="tnum">{{ avg }}</dd></div>
              <div><dt>范围</dt><dd>{{ RANGE_TEXT[range] }}</dd></div>
            </dl>
          </div>
          <div class="hero-chart">
            <TrendChart :values="trend" :labels="trendLabels" :height="212" unit=" 次" primary-label="登录" tone="ink" />
          </div>
        </div>
        <div class="hero-actions">
          <button v-if="loginLogPath" type="button" class="pill-btn is-dark" @click="router.push(loginLogPath)">
            查看登录日志 <Icon name="arrowRight" :size="14" />
          </button>
          <button type="button" class="pill-btn" :disabled="loading" @click="refresh">
            <Icon name="refresh" :size="14" /> 刷新
          </button>
        </div>
      </section>

      <!-- 侧块：在线用户 / 接口调用 -->
      <section class="bento bento-side">
        <div class="side-top">
          <span class="side-label">在线用户 · 近 1 小时</span>
          <span class="side-ico m-groove"><Icon name="activity" :size="16" /></span>
        </div>
        <Metric :value="online?.value" unit="人" size="l" />
        <div class="side-foot">
          <span class="muted">{{ online?.sub || '最近 1 小时登录' }}</span>
        </div>
      </section>
      <section class="bento bento-side">
        <div class="side-top">
          <span class="side-label">接口调用 · 近 24 小时</span>
          <span class="side-ico m-groove"><Icon name="log" :size="16" /></span>
        </div>
        <Metric :value="api?.value" unit="次" size="l" />
        <div class="side-foot">
          <span class="muted">{{ api?.sub || '最近 24 小时操作' }}</span>
          <DeltaPill v-if="api" :value="api.delta" :up="api.up" :fresh="api.fresh" label="较前 24 小时" />
        </div>
      </section>

      <!-- 02 近 7 天操作量 -->
      <section class="bento bento-third">
        <div class="bento-head">
          <SectionLabel title="近 7 天操作量" />
          <span class="bento-meta tnum">{{ dailyTotal }} 次</span>
        </div>
        <BarChart :values="daily.map(d => Number(d.total))" :failed="daily.map(d => Number(d.failed))" :labels="dailyLabels" :highlight="daily.length ? daily.length - 1 : null" unit=" 次" />
      </section>

      <!-- 03 操作成功率 -->
      <section class="bento bento-third bento-ring">
        <div class="bento-head">
          <SectionLabel title="操作成功率" />
          <span class="bento-meta">{{ RANGE_TEXT[range] }}</span>
        </div>
        <div class="ring-wrap">
          <TickRing :value="summary?.successRate" label="成功" :size="164" />
          <dl class="ring-readouts">
            <div><dt>操作</dt><dd class="tnum">{{ summary?.trendTotal ?? '—' }}</dd></div>
            <div><dt>成功率</dt><dd class="tnum">{{ summary?.successRate ?? '—' }}%</dd></div>
          </dl>
        </div>
      </section>

      <!-- 04 数据权限分布 -->
      <section class="bento bento-third">
        <div class="bento-head">
          <SectionLabel title="数据权限分布" />
          <span class="bento-meta">按角色范围</span>
        </div>
        <ul v-if="scopeItems.length" class="share-list">
          <li v-for="item in scopeItems" :key="item.label" :class="`cat-${item.cat}`">
            <span class="share-label">{{ item.label }}</span>
            <span class="share-track"><i :style="{ width: `${item.w}%` }" /></span>
            <span class="share-value tnum">{{ item.value }}<small> 人 · {{ item.pct }}%</small></span>
          </li>
        </ul>
        <EmptyState v-else-if="!loading" title="暂无角色数据" icon="role" />
      </section>

      <!-- 05 今日操作泳道 -->
      <section v-if="canTimeline" class="bento bento-wide">
        <div class="bento-head">
          <SectionLabel title="今日操作泳道" />
          <button v-if="operLogPath" type="button" class="pill-btn" @click="router.push(operLogPath)">操作日志 <Icon name="arrowRight" :size="14" /></button>
        </div>
        <Swimlane v-if="timeline?.lanes.length" :lanes="timeline.lanes" :now-sec="timeline.nowSec" compact :max-lanes="5" :fixed-lanes="5" :show-hidden-hint="false" @select="openLane($event.lane.module)" />
        <EmptyState v-else title="今天还没有操作记录" icon="clock" />
      </section>

      <!-- 最近登录 -->
      <section :class="['bento', canTimeline ? 'bento-narrow' : 'bento-half']">
        <div class="bento-head">
          <SectionLabel title="最近登录" />
        </div>
        <div class="activity-list">
          <div v-for="(a, i) in summary?.activities ?? []" :key="i" class="activity">
            <Avatar :name="String(a.who)" :size="30" />
            <div class="activity-main">
              <div class="ellipsis"><b>{{ a.who }}</b> <span class="muted">{{ a.target }}</span></div>
              <div class="faint mono-time">{{ a.time }}</div>
            </div>
            <Tag :tone="a.tint === 'ok' ? 'ok' : 'danger'" dot>{{ a.tint === 'ok' ? '成功' : '失败' }}</Tag>
          </div>
          <EmptyState v-if="!loading && !(summary?.activities ?? []).length" title="暂无登录记录" icon="clock" />
        </div>
      </section>

      <!-- 常用入口 -->
      <section v-if="shortcuts.length" :class="['bento', canTimeline ? 'bento-full' : 'bento-half']">
        <div class="bento-head">
          <SectionLabel title="常用入口" />
        </div>
        <div class="shortcut-grid">
          <button v-for="s in shortcuts" :key="s.key" type="button" class="quick-link lift m-surface" @click="router.push(s.path)">
            <span class="quick-ico m-groove"><Icon :name="s.icon" :size="17" /></span>
            <span class="quick-text">
              <span class="quick-title">{{ s.label }}</span>
              <span class="quick-sub">{{ s.group }}</span>
            </span>
            <Icon name="arrowRight" :size="15" class="quick-go" />
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

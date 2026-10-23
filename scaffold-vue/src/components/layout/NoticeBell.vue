<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import Button from '@/components/ui/Button.vue'
import IconButton from '@/components/ui/IconButton.vue'
import { systemApi, type NoticeRow } from '@/api'
import { overlayEnter, overlayLeave } from '@/composables/motion'

/* 顶栏铃铛：全员公告与点对点消息共用同一个入口。
   未读数 60 秒轮询一次；点开时拉最近 5 条，已读状态按人记录。 */
const router = useRouter()
const btn = ref<HTMLElement | null>(null)
const pop = ref<HTMLElement | null>(null)
const open = ref(false)
const count = ref(0)
const items = ref<NoticeRow[]>([])
const loading = ref(false)
const pos = ref({ top: 0, right: 0 })
let timer = 0

const CATEGORY_LABEL: Record<string, string> = { notice: '公告', alert: '告警', task: '待办' }

async function refreshCount() {
  try {
    count.value = (await systemApi.notices.unreadCount()).count ?? 0
  } catch { /* 未读数失败不影响主流程 */ }
}

async function loadLatest() {
  loading.value = true
  try {
    const page = await systemApi.notices.mine({ pageNum: 1, pageSize: 5 })
    items.value = page.rows
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function openPop() {
  const rect = btn.value?.getBoundingClientRect()
  if (!rect) return
  pos.value = { top: rect.bottom + 8, right: window.innerWidth - rect.right }
  open.value = true
  await Promise.all([loadLatest(), refreshCount()])
  await nextTick()
  document.addEventListener('pointerdown', onOutside, true)
  document.addEventListener('keydown', onKey)
}

function close() {
  open.value = false
  document.removeEventListener('pointerdown', onOutside, true)
  document.removeEventListener('keydown', onKey)
}

function onOutside(event: PointerEvent) {
  const target = event.target as Node
  if (btn.value?.contains(target) || pop.value?.contains(target)) return
  close()
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') close()
}

async function openItem(item: NoticeRow) {
  if (!item.readFlag) {
    try {
      await systemApi.notices.read(item.id)
      item.readFlag = 1
      count.value = Math.max(0, count.value - 1)
    } catch { /* 标记失败下次重试 */ }
  }
  close()
  router.push('/system/notice')
}

async function readAll() {
  try {
    await systemApi.notices.readAll()
    items.value = items.value.map(item => ({ ...item, readFlag: 1 }))
    count.value = 0
  } catch { /* 忽略 */ }
}

function goAll() {
  close()
  router.push('/system/notice')
}

onMounted(() => {
  refreshCount()
  timer = window.setInterval(refreshCount, 60_000)
})
onBeforeUnmount(() => {
  window.clearInterval(timer)
  close()
})
</script>

<template>
  <span ref="btn" class="bell-wrap">
    <IconButton name="bell" label="通知" @click="open ? close() : openPop()" />
    <span v-if="count > 0" class="bell-badge" aria-hidden="true">{{ count > 99 ? '99+' : count }}</span>
  </span>

  <Teleport to="body">
    <Transition :css="false" @enter="(el, done) => overlayEnter(el, done, 'pop')" @leave="(el, done) => overlayLeave(el, done, 'pop')">
      <div v-if="open" ref="pop" class="menu-pop m-float bell-pop" :style="{ top: `${pos.top}px`, right: `${pos.right}px` }" role="dialog" aria-label="通知">
        <div class="bell-head">
          <b>通知</b>
          <span class="soft">{{ count > 0 ? `${count} 条未读` : '都看完啦' }}</span>
        </div>
        <div v-if="loading" class="bell-empty soft">正在加载…</div>
        <div v-else-if="!items.length" class="bell-empty soft">暂无通知</div>
        <template v-else>
          <button
            v-for="item in items"
            :key="item.id"
            type="button"
            class="bell-item"
            :class="[`is-${item.level}`, !item.readFlag && 'is-unread']"
            @click="openItem(item)"
          >
            <span class="bell-dot" aria-hidden="true" />
            <span class="bell-item-main">
              <span class="bell-title">{{ item.title }}</span>
              <span class="bell-meta soft">{{ CATEGORY_LABEL[item.category] ?? '通知' }} · {{ item.createTime }}</span>
            </span>
            <Icon v-if="!item.readFlag" name="mail" :size="13" class="bell-unread-ico" />
          </button>
        </template>
        <div class="bell-foot">
          <Button variant="text" size="sm" @click="readAll">全部已读</Button>
          <Button variant="text" size="sm" @click="goAll">查看全部</Button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.bell-wrap { position: relative; display: inline-flex; }
.bell-badge {
  position: absolute;
  top: 1px;
  right: 1px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--red);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  pointer-events: none;
  box-shadow: 0 0 0 2px var(--canvas);
}
.bell-pop { width: 320px; padding: 6px; }
.bell-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 10px 8px;
  font-size: 13px;
}
.bell-head .soft { font-size: 11.5px; }
.bell-empty { padding: 22px 10px; text-align: center; font-size: 12.5px; }
.bell-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  width: 100%;
  padding: 9px 10px;
  border-radius: var(--r-xs);
  text-align: left;
  transition: background .15s ease;
}
.bell-item:hover { background: var(--hover-bg); }
.bell-item.is-unread .bell-title { font-weight: 650; }
.bell-dot { width: 6px; height: 6px; margin-top: 6px; border-radius: 50%; background: var(--ink-faint); flex: none; }
.bell-item.is-info .bell-dot { background: var(--blue); }
.bell-item.is-warn .bell-dot { background: #e6a23c; }
.bell-item.is-danger .bell-dot { background: var(--red); }
.bell-item-main { min-width: 0; display: flex; flex-direction: column; gap: 2px; flex: 1; }
.bell-title { font-size: 12.5px; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bell-meta { font-size: 11px; }
.bell-unread-ico { color: var(--blue); margin-top: 2px; }
.bell-foot {
  display: flex;
  justify-content: space-between;
  padding: 6px 4px 2px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
}
</style>

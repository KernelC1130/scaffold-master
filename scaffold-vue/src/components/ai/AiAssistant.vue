<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import yaml from 'highlight.js/lib/languages/yaml'
import plaintext from 'highlight.js/lib/languages/plaintext'
import DOMPurify from 'dompurify'
import Icon from '@/components/Icon.vue'
import IconButton from '@/components/ui/IconButton.vue'
import { authSession } from '@/api/session.ts'
import { vulnApi } from '@/api'
import { pushToast, toastError } from '@/composables/toast'
import 'highlight.js/styles/github.css'

/* ============================================================
   AI 助手：可拖动悬浮球 + 聊天弹窗（迁移自旧平台 AiAssistant，
   逻辑照搬：SSE 手动解析 / 伪打字机平滑输出 / Markdown 消毒 /
   代码块工具栏；UI 换 scaffold 珍珠玻璃语言）
   ============================================================ */

// 代码高亮：按需注册常用语言（全量包体积过大）
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('yaml', yaml)
// 无语言标注的代码块会 fallback 到 plaintext，不注册会导致渲染抛错炸掉整个会话
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)
marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
      return hljs.highlight(code, { language }).value
    },
  })
)

const API_BASE = (import.meta as ImportMeta & { env?: { VITE_API_BASE_URL?: string } }).env?.VITE_API_BASE_URL || '/api'

interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  html?: string
  streaming?: boolean
}

const open = ref(false)
const loading = ref(false)
const input = ref('')
const messages = ref<ChatMsg[]>([])
const bodyRef = ref<HTMLElement | null>(null)
const abortRef = ref<AbortController | null>(null)
const currentSessionId = ref<number | null>(null)
const currentTitle = ref('AI 助手')
const sessions = ref<Array<{ sessionId: number; title: string }>>([])
const sessionMenuOpen = ref(false)

/* ── 悬浮球拖动：拖走换位置，原地松手视为点击 ── */
const fabRef = ref<HTMLElement | null>(null)
const fabPos = ref<{ x: number; y: number } | null>(null)
const fabDragging = ref(false)
const FAB_SIZE = 48
let fabStart = { x: 0, y: 0, left: 0, top: 0 }
let fabMoved = false

function onFabDown(e: PointerEvent) {
  // 不能 preventDefault —— Chrome 下会抑制后续 click 合成，导致单击打不开弹窗
  const rect = fabRef.value!.getBoundingClientRect()
  fabStart = { x: e.clientX, y: e.clientY, left: rect.left, top: rect.top }
  fabMoved = false
  window.addEventListener('pointermove', onFabMove)
  window.addEventListener('pointerup', onFabUp)
}

function onFabMove(e: PointerEvent) {
  const dx = e.clientX - fabStart.x
  const dy = e.clientY - fabStart.y
  if (!fabMoved) {
    if (Math.abs(dx) + Math.abs(dy) < 4) return
    fabMoved = true
    fabDragging.value = true
    // 确认进入拖动：阻止文字框选（此处 preventDefault 不影响 click 语义）
    e.preventDefault()
    document.body.classList.add('fab-dragging')
  }
  const x = Math.min(Math.max(fabStart.left + dx, 0), window.innerWidth - FAB_SIZE)
  const y = Math.min(Math.max(fabStart.top + dy, 0), window.innerHeight - FAB_SIZE)
  fabPos.value = { x, y }
}

function onFabUp() {
  window.removeEventListener('pointermove', onFabMove)
  window.removeEventListener('pointerup', onFabUp)
  document.body.classList.remove('fab-dragging')
  fabDragging.value = false
}

function onFabClick() {
  if (fabMoved) {
    fabMoved = false
    return
  }
  open.value = true
  void loadSessions()
}

/* ── Markdown 渲染（代码高亮）→ XSS 消毒 → 代码块包工具栏容器 ── */
function renderMarkdown(text: string) {
  const html = DOMPurify.sanitize(marked.parse(text) as string)
  // 容器在 sanitize 之后注入（静态字符串，不经消毒也不需要）
  const copySvg = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
  const downloadSvg = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="3" x2="12" y2="15"/></svg>'
  return html
    .split('<pre>')
    .join('<div class="code-block"><div class="code-header"><span class="code-lang"></span><span class="code-actions"><button type="button" class="code-copy-btn">' + copySvg + '复制</button><button type="button" class="code-download-btn">' + downloadSvg + '下载</button></span></div><pre>')
    .split('</pre>')
    .join('</pre></div>')
}

/** 补齐代码块语言标签（marked 的 language-xxx 在内层 code class 上，字符串阶段拿不到） */
function decorateCodeBlocks() {
  if (!bodyRef.value) return
  bodyRef.value.querySelectorAll<HTMLElement>('.code-block').forEach(block => {
    if (block.dataset.langDone) return
    block.dataset.langDone = '1'
    const code = block.querySelector('code')
    const m = code ? code.className.match(/language-([\w-]+)/) : null
    const langEl = block.querySelector('.code-lang')
    if (langEl) langEl.textContent = m ? m[1] : 'text'
  })
}

/** 代码语言 -> 文件后缀 */
const LANG_EXT: Record<string, string> = {
  python: 'py', java: 'java', sql: 'sql', json: 'json', bash: 'sh',
  shell: 'sh', javascript: 'js', xml: 'xml', yaml: 'yml', html: 'html',
}

/** 智能命名：java 取 class X，python 取 def X，否则 代码片段_yyyyMMddHHmm */
function buildFileName(pre: HTMLElement, lang: string) {
  const ext = LANG_EXT[lang] || 'txt'
  const text = (pre.querySelector('code') || pre).innerText
  if (lang === 'java') {
    const m = text.match(/class\s+(\w+)/)
    if (m) return m[1] + '.' + ext
  }
  if (lang === 'python') {
    const m = text.match(/def\s+(\w+)/)
    if (m) return m[1] + '.' + ext
  }
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return '代码片段_' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate()) + pad(d.getHours()) + pad(d.getMinutes()) + '.' + ext
}

function downloadCode(pre: HTMLElement, lang: string) {
  const code = pre.querySelector('code')
  const blob = new Blob([code ? code.innerText : pre.innerText], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = buildFileName(pre, lang)
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 消息容器上的事件委托：复制/下载按钮（流式重渲 DOM 不需要重新绑定） */
function onBodyClick(e: MouseEvent) {
  const target = e.target instanceof Element ? e.target : null
  const copyBtn = target?.closest('.code-copy-btn')
  if (copyBtn) {
    const pre = copyBtn.closest('.code-block')?.querySelector('pre')
    if (!pre) return
    const code = pre.querySelector('code')
    navigator.clipboard
      .writeText(code ? code.innerText : pre.innerText)
      .then(() => pushToast('代码已复制'))
      .catch(() => pushToast('复制失败，请手动选择复制', 'danger'))
    return
  }
  const downloadBtn = target?.closest('.code-download-btn')
  if (downloadBtn) {
    const block = downloadBtn.closest('.code-block')
    const pre = block?.querySelector('pre')
    if (pre) downloadCode(pre as HTMLElement, detectLang(pre as HTMLElement))
  }
}

function detectLang(pre: HTMLElement) {
  const code = pre.querySelector('code')
  const m = code ? code.className.match(/language-([\w-]+)/) : null
  return m ? m[1] : ''
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void send()
  }
}

/** 关闭弹窗：中断进行中的流式请求 */
function handleClose() {
  abortRef.value?.abort()
  sessionMenuOpen.value = false
}

/** 停止生成 */
function handleStop() {
  abortRef.value?.abort()
}

function scrollToBottom() {
  nextTick(() => bodyRef.value?.scrollTo({ top: bodyRef.value.scrollHeight }))
}

/** 更新最后一条 assistant 消息（流式增量/收尾渲染） */
function renderAssistant(text: string) {
  const msg = messages.value[messages.value.length - 1]
  if (msg && msg.role === 'assistant') {
    msg.content = text
    msg.html = renderMarkdown(text)
    nextTick(decorateCodeBlocks)
    scrollToBottom()
  }
}

/** 流式对话：fetch + ReadableStream 手动解析 SSE（EventSource 带不了 Authorization 头） */
async function send() {
  const question = input.value.trim()
  if (!question || loading.value) return
  messages.value.push({ role: 'user', content: question })
  // 先占位一个空的 assistant 消息：无内容时气泡内显示三点动画
  messages.value.push({ role: 'assistant', content: '', html: '', streaming: true })
  input.value = ''
  loading.value = true
  scrollToBottom()

  abortRef.value = new AbortController()
  let raw = ''
  let shown = 0
  let streamDone = false
  // 伪打字机：delta 到达只攒进 raw，由定时器匀速推进显示（模型吐字忽快忽慢也不影响观感）
  const typer = setInterval(() => {
    if (shown >= raw.length) return
    shown = Math.min(raw.length, shown + Math.max(2, Math.ceil((raw.length - shown) / 8)))
    renderAssistant(raw.slice(0, shown))
  }, 30)

  try {
    // 无会话时先创建（title 由后端取首问前 20 字）
    if (!currentSessionId.value) {
      const created = await vulnApi.ai.newSession(question)
      currentSessionId.value = created.sessionId
      currentTitle.value = created.title
      sessions.value.unshift({ sessionId: created.sessionId, title: created.title })
    }
    const token = authSession.token()
    const resp = await fetch(`${API_BASE}/vuln/ai/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + (token ?? '') },
      body: JSON.stringify({ sessionId: currentSessionId.value, question }),
      signal: abortRef.value.signal,
    })
    if (!resp.ok || !resp.body) {
      let msg = 'HTTP ' + resp.status
      try {
        const j = await resp.json()
        msg = j.message || msg
      } catch { /* 保留状态码提示 */ }
      throw new Error(msg)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // SSE 帧以空行（\n\n）分隔
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        for (const line of frame.split('\n')) {
          if (!line.startsWith('data:')) continue
          const payload = line.slice(5).trim()
          if (!payload) continue
          if (payload === '[DONE]') {
            // 后端已宣告结束：主动断开读取。各层代理对「连接关闭」的传播不可靠，以 [DONE] 为准收尾
            streamDone = true
            abortRef.value.abort()
            continue
          }
          try {
            const obj = JSON.parse(payload)
            if (typeof obj.delta === 'string' && obj.delta) {
              raw += obj.delta
            } else if (obj.error) {
              raw += (raw ? '\n\n' : '') + '> ⚠️ ' + obj.error
            }
          } catch { /* 非法帧忽略 */ }
        }
      }
    }
    renderAssistant(raw || '（本次无回答）')
  } catch (e) {
    clearInterval(typer)
    if (streamDone) {
      // [DONE] 已收到：无论连接以何种方式收尾，都按正常完成处理
      renderAssistant(raw || '（本次无回答）')
    } else if (e instanceof DOMException && e.name === 'AbortError') {
      // 用户主动停止：显示已收到的全部内容并标记
      renderAssistant(raw + (raw ? '\n\n' : '') + '*（已停止生成）*')
    } else if (raw) {
      // 已有部分内容：追加中断提示
      renderAssistant(raw + '\n\n⚠️ 生成中断：' + ((e as Error).message || '网络异常'))
    } else {
      const msg = messages.value[messages.value.length - 1]
      if (msg && msg.role === 'assistant') {
        msg.streaming = false
        msg.html = '<p class="chat-err">抱歉，本次回答失败：' + ((e as Error).message || '请稍后重试') + '</p>'
      }
    }
  } finally {
    clearInterval(typer)
    loading.value = false
    abortRef.value = null
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') last.streaming = false
    scrollToBottom()
  }
}

/** 加载会话列表 */
async function loadSessions() {
  try {
    const res = await vulnApi.ai.sessions()
    sessions.value = res.list || []
  } catch {
    sessions.value = []
  }
}

/** 新建会话：清空当前对话，sessionId 待首条消息发送时创建 */
function handleNewSession() {
  currentSessionId.value = null
  currentTitle.value = 'AI 助手'
  messages.value = []
  sessionMenuOpen.value = false
}

/** 切换会话：从服务端拉取历史消息渲染 */
async function switchSession(sessionId: number) {
  if (sessionId === currentSessionId.value || loading.value) return
  sessionMenuOpen.value = false
  try {
    const res = await vulnApi.ai.history(sessionId)
    currentSessionId.value = sessionId
    messages.value = (res.list || []).map(m => ({
      role: m.role as 'user' | 'assistant',
      content: m.content,
      html: m.role === 'assistant' ? renderMarkdown(m.content) : '',
    }))
    const s = sessions.value.find(x => x.sessionId === sessionId)
    currentTitle.value = s ? s.title : 'AI 助手'
    nextTick(decorateCodeBlocks)
    scrollToBottom()
  } catch (e) {
    toastError(e, '加载会话失败')
  }
}

/** 点击页面其他区域关闭会话下拉 */
function onDocClick(e: MouseEvent) {
  if (!sessionMenuOpen.value) return
  const target = e.target instanceof Element ? e.target : null
  if (!target?.closest('.session-switch')) sessionMenuOpen.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  abortRef.value?.abort()
})
</script>

<template>
  <!-- 悬浮球（可拖动；原地点击打开弹窗） -->
  <div
    ref="fabRef"
    :class="['ai-fab', { dragging: fabDragging }]"
    :style="fabPos ? { left: fabPos.x + 'px', top: fabPos.y + 'px', right: 'auto', bottom: 'auto' } : undefined"
    title="AI 助手"
    @pointerdown="onFabDown"
    @click="onFabClick"
  >
    <Icon name="sparkles" :size="22" />
  </div>

  <!-- 聊天弹窗 -->
  <Teleport to="body">
    <div v-if="open" class="ai-overlay" @click.self="open = false">
      <section class="ai-dialog m-float" role="dialog" aria-label="AI 助手">
        <!-- 头部：会话切换下拉 + 新建/关闭 -->
        <header class="ai-head">
          <span class="ai-mark"><Icon name="sparkles" :size="16" /></span>
          <div class="session-switch">
            <button type="button" class="session-title" @click.stop="sessionMenuOpen = !sessionMenuOpen">
              {{ currentTitle }}
              <Icon name="chevronDown" :size="12" />
            </button>
            <div v-if="sessionMenuOpen" class="session-menu m-float">
              <p v-if="!sessions.length" class="session-empty">暂无历史会话</p>
              <button
                v-for="s in sessions"
                :key="s.sessionId"
                type="button"
                :class="['session-item', { on: s.sessionId === currentSessionId }]"
                @click.stop="switchSession(s.sessionId)"
              >
                {{ s.title }}
              </button>
            </div>
          </div>
          <div class="ai-actions">
            <IconButton name="plus" label="新建会话" :disabled="loading" @click="handleNewSession" />
            <IconButton name="x" label="关闭" @click="open = false" />
          </div>
        </header>

        <div ref="bodyRef" class="ai-body" @click="onBodyClick">
          <div v-if="messages.length === 0" class="ai-empty">
            <span class="ai-empty-mark"><Icon name="sparkles" :size="26" /></span>
            <p>你好，我是漏洞管理平台 AI 助手</p>
            <p class="sub">可以问我漏洞情况、风险统计等问题</p>
          </div>
          <div v-for="(msg, index) in messages" :key="index" :class="['ai-msg', msg.role]">
            <div v-if="msg.role === 'user'" class="bubble">{{ msg.content }}</div>
            <!-- AI 回复为 Markdown，消毒后再渲染，防 XSS；流式期间无内容先显示三点动画 -->
            <div v-else class="bubble md-body">
              <div v-if="msg.streaming && !msg.html" class="typing"><span></span><span></span><span></span></div>
              <!-- eslint-disable-next-line vue/no-v-html -- DOMPurify 已消毒 -->
              <div v-else v-html="msg.html"></div>
            </div>
          </div>
        </div>

        <footer class="ai-foot">
          <textarea
            v-model="input"
            class="ai-input"
            rows="3"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            :disabled="loading"
            @keydown="onKeydown"
          ></textarea>
          <div class="ai-foot-actions">
            <button v-if="loading" type="button" class="ai-send is-stop" @click="handleStop">
              <Icon name="minus" :size="15" /> 停止
            </button>
            <button v-else type="button" class="ai-send" :disabled="!input.trim()" @click="send()">
              <Icon name="arrowRight" :size="15" /> 发送
            </button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
/* ── 悬浮球 ── */
.ai-fab {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 1200;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  color: #fff;
  background: var(--dark-surface);
  box-shadow: 0 4px 14px rgba(14, 18, 20, .3), 0 10px 30px rgba(14, 18, 20, .22);
  cursor: pointer;
  transition: transform .18s var(--ease-premium), box-shadow .18s ease;
  user-select: none;
  touch-action: none;
}
.ai-fab:hover { transform: translateY(-2px) scale(1.05); box-shadow: 0 6px 18px rgba(14, 18, 20, .34), 0 14px 36px rgba(14, 18, 20, .26); }
.ai-fab:active { transform: scale(.97); }
.ai-fab.dragging { cursor: grabbing; transition: none; }
.ai-fab .icon { color: var(--lime); filter: drop-shadow(0 0 6px rgba(130, 221, 45, .45)); }
body.fab-dragging { user-select: none; }

/* ── 遮罩与弹窗 ── */
.ai-overlay {
  position: fixed;
  inset: 0;
  z-index: 1300;
  background: var(--overlay);
  display: grid;
  place-items: center;
  padding: 24px;
}
.ai-dialog {
  width: min(760px, 100%);
  height: min(660px, 100%);
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  border: 1px solid var(--glass-line, rgba(23, 25, 29, .08));
  background: var(--canvas, #f6f8f7);
  overflow: hidden;
}

/* ── 头部 ── */
.ai-head { display: flex; align-items: center; gap: 10px; padding: 13px 16px; border-bottom: 1px solid var(--line, rgba(23,25,29,.08)); }
.ai-mark { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 9px; background: var(--dark-surface); color: var(--lime); flex: none; }
.session-switch { position: relative; min-width: 0; }
.session-title { display: inline-flex; align-items: center; gap: 5px; max-width: 380px; border: 0; background: none; padding: 4px 8px;
                 border-radius: 8px; font: inherit; font-size: 14px; font-weight: 700; color: var(--ink, #101418); cursor: pointer; }
.session-title:hover { background: var(--hover-bg, rgba(23,25,29,.05)); }
.session-title .icon { color: var(--ink-faint, #9aa2ab); }
.session-menu { position: absolute; top: calc(100% + 6px); left: 0; z-index: 20; min-width: 240px; max-width: 380px; max-height: 300px;
                overflow-y: auto; padding: 5px; border-radius: 12px; background: var(--surface, #fff); border: 1px solid var(--line, rgba(23,25,29,.08)); }
.session-empty { padding: 10px 12px; font-size: 12px; color: var(--ink-faint, #9aa2ab); }
.session-item { display: block; width: 100%; text-align: left; border: 0; background: none; padding: 8px 10px; border-radius: 8px;
                font: inherit; font-size: 12.5px; color: var(--ink-soft, #4b5563); cursor: pointer; white-space: nowrap;
                overflow: hidden; text-overflow: ellipsis; }
.session-item:hover { background: var(--hover-bg, rgba(23,25,29,.05)); color: var(--ink, #101418); }
.session-item.on { background: var(--hover-bg, rgba(23,25,29,.05)); color: var(--blue, #236de9); font-weight: 650; }
.ai-actions { margin-left: auto; display: flex; gap: 4px; }

/* ── 消息区 ── */
.ai-body { flex: 1; overflow-y: auto; padding: 18px 18px 8px; }
.ai-empty { text-align: center; padding: 60px 0 40px; color: var(--ink-muted, #6e7480); }
.ai-empty p { margin-top: 10px; font-size: 14px; }
.ai-empty .sub { font-size: 12px; color: var(--ink-faint, #9aa2ab); margin-top: 4px; }
.ai-empty-mark { display: inline-grid; place-items: center; width: 54px; height: 54px; border-radius: 16px;
                 background: var(--dark-surface); color: var(--lime); }
.ai-msg { display: flex; margin-bottom: 14px; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }
.bubble { max-width: 82%; padding: 10px 14px; border-radius: 14px; font-size: 13.5px; line-height: 1.75; overflow-wrap: break-word; }
.ai-msg.user .bubble { background: var(--blue, #236de9); color: #fff; border-bottom-right-radius: 5px; }
.ai-msg.assistant .bubble { background: var(--surface, #fff); border: 1px solid var(--line, rgba(23,25,29,.08));
                            color: var(--ink, #101418); border-bottom-left-radius: 5px; box-shadow: 0 1px 2px rgba(17,23,27,.04); }

/* Markdown 正文 */
.md-body :deep(p) { margin: 0 0 8px; }
.md-body :deep(p:last-child) { margin-bottom: 0; }
.md-body :deep(ul), .md-body :deep(ol) { margin: 4px 0 8px; padding-left: 20px; }
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3), .md-body :deep(h4) { font-size: 13.5px; margin: 10px 0 6px; }
.md-body :deep(code:not(pre code)) { padding: 1px 6px; border-radius: 5px; background: var(--hover-bg, rgba(23,25,29,.06));
                                    font-family: Consolas, ui-monospace, monospace; font-size: 12px; }
.md-body :deep(blockquote) { margin: 6px 0; padding: 4px 12px; border-left: 3px solid var(--line-strong, rgba(23,25,29,.14)); color: var(--ink-soft, #4b5563); }
.md-body :deep(a) { color: var(--blue, #236de9); }
.md-body :deep(.chat-err) { color: #d64545; margin: 0; }

/* 代码块 + 工具栏 */
/* 代码块浅色：header 与代码同底融合为一个容器（参考组内平台样式） */
.md-body :deep(.code-block) { margin: 6px 0; border-radius: 10px; overflow: hidden; background: #f6f8fa; }
.md-body :deep(.code-header) { display: flex; justify-content: space-between; align-items: center; padding: 9px 14px 0; }
.md-body :deep(.code-lang) { font-family: Consolas, ui-monospace, monospace; font-size: 11.5px; color: var(--ink-muted, #6e7480); }
.md-body :deep(.code-actions) { display: flex; gap: 2px; }
.md-body :deep(.code-copy-btn), .md-body :deep(.code-download-btn) { display: inline-flex; align-items: center; gap: 5px; border: 0;
                                     background: none; padding: 3px 8px; border-radius: 7px;
                                     font-family: inherit; font-size: 13.5px; color: var(--ink-soft, #4b5563);
                                     cursor: pointer; transition: background .15s ease, color .15s ease; }
.md-body :deep(.code-copy-btn:hover), .md-body :deep(.code-download-btn:hover) { color: var(--blue, #236de9); background: rgba(35,109,233,.08); }
.md-body :deep(.code-block pre) { margin: 0; padding: 0 14px 12px; overflow-x: auto; }
.md-body :deep(.code-block code) { font-family: Consolas, ui-monospace, monospace; font-size: 12.5px; line-height: 1.65;
                                   background: none; padding: 0; color: #24292f; }

/* 三点动画 */
.typing { display: inline-flex; gap: 4px; padding: 4px 0; }
.typing span { width: 6px; height: 6px; border-radius: 50%; background: var(--ink-faint, #9aa2ab); opacity: .5; animation: ai-typing 1.2s infinite; }
.typing span:nth-child(2) { animation-delay: .18s; }
.typing span:nth-child(3) { animation-delay: .36s; }
@keyframes ai-typing { 0%, 60%, 100% { transform: translateY(0); opacity: .5; } 30% { transform: translateY(-4px); opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .typing span { animation: none; } }

/* ── 输入区 ── */
.ai-foot { border-top: 1px solid var(--line, rgba(23,25,29,.08)); padding: 12px 16px 14px; }
.ai-input { width: 100%; resize: none; border: 1px solid var(--line, rgba(23,25,29,.1)); border-radius: 12px; padding: 10px 12px;
            font: inherit; font-size: 13px; line-height: 1.6; color: var(--ink, #101418); background: var(--surface, #fff);
            transition: border-color .18s ease, box-shadow .18s ease; }
.ai-input:focus { outline: none; border-color: var(--blue, #236de9); box-shadow: 0 0 0 3px rgba(35, 109, 233, .12); }
.ai-input:disabled { opacity: .6; }
.ai-foot-actions { display: flex; justify-content: flex-end; margin-top: 10px; }
.ai-send { display: inline-flex; align-items: center; gap: 6px; border: 0; border-radius: 10px; padding: 8px 18px;
           background: var(--blue, #236de9); color: #fff; font: inherit; font-size: 13px; font-weight: 650; cursor: pointer;
           transition: transform .15s ease, box-shadow .15s ease, background .15s ease; }
.ai-send:hover:not(:disabled) { background: #1d5fce; transform: translateY(-1px); }
.ai-send:disabled { opacity: .45; cursor: not-allowed; }
.ai-send.is-stop { background: var(--dark-surface, #131619); }
</style>

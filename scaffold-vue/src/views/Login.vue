<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Field from '@/components/ui/Field.vue'
import TextInput from '@/components/ui/TextInput.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import { ApiError, authApi } from '@/api'
import LoginBrandPanel from '@/components/business/LoginBrandPanel.vue'
import { login } from '@/composables/auth'
import { pushToast } from '@/composables/toast'
import { EASE, afterTwoFrames, gsap, motionSkipped, prefersReducedMotion, trackGlow } from '@/composables/motion'
import { required, validate } from '@/utils/validate'

const router = useRouter()
const route = useRoute()

/* 登录框记忆：勾选记住登录后存下本次账号密码，下次打开/退出后回填；无记忆才预填 test 演示账号 */
const LOGIN_HINT_KEY = 'scaffold.login-hint'
function loadLoginHint(): { username: string; password: string; remember: boolean } {
  try {
    const raw = localStorage.getItem(LOGIN_HINT_KEY)
    if (raw) {
      const h = JSON.parse(raw) as { username?: string; password?: string }
      if (h.username) return { username: h.username, password: h.password || '', remember: true }
    }
  } catch { /* 存储不可用或数据损坏，走预填 */ }
  return { username: 'test', password: 'test123', remember: true }
}
const loginHint = loadLoginHint()
const form = reactive({ username: loginHint.username, password: loginHint.password, captcha: '' })
const errors = ref<Partial<Record<keyof typeof form, string>>>({})
const remember = ref(loginHint.remember)
const submitting = ref(false)
const captchaCode = ref('')
const captchaUuid = ref('')
const captchaLoading = ref(false)
const root = ref<HTMLElement | null>(null)
const card = ref<HTMLElement | null>(null)
const succeeded = ref(false)

/* ── 登录卡随指针轻倾（≤ 4°），离开回正；只在可悬停设备上启用 ── */
let tiltX: ((v: number) => void) | null = null
let tiltY: ((v: number) => void) | null = null
function onTilt(event: PointerEvent) {
  if (!card.value || !tiltX || !tiltY || motionSkipped()) return
  const r = card.value.getBoundingClientRect()
  const px = (event.clientX - r.left) / r.width - 0.5
  const py = (event.clientY - r.top) / r.height - 0.5
  tiltY(Math.max(-1, Math.min(1, px * 2)) * 4)
  tiltX(Math.max(-1, Math.min(1, py * 2)) * -3)
}
function resetTilt() {
  tiltX?.(0)
  tiltY?.(0)
}

/* ── 登录成功三拍：按钮收拢成圆（放）→ 对勾圆环描出（结）→ 进入外壳 ── */
async function playSuccess() {
  succeeded.value = true
  await nextTick()
  const orb = card.value?.querySelector<HTMLElement>('.success-orb')
  if (!orb || motionSkipped()) return
  const ring = orb.querySelector<SVGCircleElement>('circle')
  const check = orb.querySelector<SVGPathElement>('path')
  await new Promise<void>((resolve) => {
    const tl = gsap.timeline({ onComplete: resolve })
    tl.fromTo(orb, { width: orb.parentElement!.clientWidth }, { width: 46, duration: 0.42, ease: EASE.swap })
    if (ring) tl.fromTo(ring, { strokeDashoffset: 126 }, { strokeDashoffset: 0, duration: 0.5, ease: EASE.reveal }, '-=0.08')
    if (check) tl.fromTo(check, { strokeDashoffset: 24 }, { strokeDashoffset: 0, duration: 0.32, ease: EASE.settle }, '-=0.22')
    tl.to(orb, { scale: 1.06, duration: 0.16, ease: 'power1.out' }).to(orb, { scale: 1, duration: 0.3, ease: EASE.settle })
  })
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const res = await authApi.captcha()
    captchaUuid.value = res.uuid
    captchaCode.value = res.code
  } catch {
    captchaUuid.value = ''
    captchaCode.value = ''
    pushToast('无法连接服务，请确认后端已启动', 'danger')
  } finally {
    captchaLoading.value = false
  }
  form.captcha = ''
}

async function submit() {
  errors.value = validate(form, {
    username: [required('请输入账号')],
    password: [required('请输入密码')],
    captcha: [required('请输入验证码')],
  })
  if (Object.keys(errors.value).length) return
  submitting.value = true
  try {
    await login({ username: form.username.trim(), password: form.password, captcha: form.captcha.trim(), captchaUuid: captchaUuid.value }, remember.value)
    // 记住登录：把本次账号密码存入登录框记忆（下次打开/退出后回填）；不勾选则清除记忆
    try {
      if (remember.value) {
        localStorage.setItem(LOGIN_HINT_KEY, JSON.stringify({ username: form.username.trim(), password: form.password }))
      } else {
        localStorage.removeItem(LOGIN_HINT_KEY)
      }
    } catch { /* 存储不可用，静默 */ }
    await playSuccess()
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    const failure = await router.replace(redirect)
    // 进入外壳的导航被取消（如菜单加载期间会话过期）时仍停在登录页：恢复登录按钮
    if (failure || router.currentRoute.value.name === 'login') succeeded.value = false
  } catch (error) {
    succeeded.value = false
    pushToast(error instanceof ApiError ? error.message : '登录失败，请稍后重试', 'danger')
    await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

/* ── 一次性开场：本次会话只播一次；任意点击或按键快进而不是硬切 ── */
const INTRO_KEY = 'scaffold.login-intro'
let intro: gsap.core.Timeline | null = null
const fastForward = () => intro?.timeScale(6)

function playIntro() {
  const el = root.value
  if (!el) return
  let seen = false
  try { seen = sessionStorage.getItem(INTRO_KEY) === '1' } catch { /* 忽略 */ }
  if (prefersReducedMotion() || document.hidden) {
    el.classList.add('is-entrance-ready')
    return
  }
  const q = (s: string) => el.querySelectorAll(s)
  intro = gsap.timeline({ paused: true, onComplete: () => { intro = null } })
  intro.set(['.brand-top', '.brand-copy', '.journey', '.brand-footer', '.login-card'].flatMap(s => [...q(s)]), { opacity: 0, y: 16 })
  intro.add(() => el.classList.add('is-entrance-ready'))
  // 先对焦品牌 → 标题逐行落定 → 能力逐条接通 → 登录卡交付
  intro.to(q('.brand-top'), { opacity: 1, y: 0, duration: 0.7, ease: EASE.reveal }, 0)
  intro.to(q('.brand-copy'), { opacity: 1, y: 0, duration: 0.9, ease: EASE.reveal, stagger: 0.08 }, 0.12)
  intro.to(q('.journey'), { opacity: 1, y: 0, duration: 0.7, ease: EASE.reveal, stagger: { each: 0.06, from: 'start' } }, 0.36)
  intro.to(q('.brand-footer'), { opacity: 1, y: 0, duration: 0.7, ease: EASE.reveal, stagger: 0.08 }, 0.5)
  intro.to(q('.login-card'), { opacity: 1, y: 0, duration: 0.8, ease: EASE.glide }, 0.22)
  intro.add(() => gsap.set(el.querySelectorAll('.brand-top, .brand-copy, .journey, .brand-footer'), { clearProps: 'transform' }))
  if (seen) intro.timeScale(2.4)
  try { sessionStorage.setItem(INTRO_KEY, '1') } catch { /* 忽略 */ }
  afterTwoFrames(() => intro?.play(0))
}

// 页面被切到后台时直接走完编排，避免停在隐藏帧
function onVisibility() {
  if (document.hidden && intro) intro.progress(1, false)
}

onMounted(() => {
  refreshCaptcha()
  if (card.value && window.matchMedia('(hover: hover)').matches) {
    tiltX = gsap.quickTo(card.value, 'rotationX', { duration: 0.6, ease: 'power3.out' })
    tiltY = gsap.quickTo(card.value, 'rotationY', { duration: 0.6, ease: 'power3.out' })
  }
  playIntro()
  window.addEventListener('pointerdown', fastForward, { once: true })
  window.addEventListener('keydown', fastForward, { once: true })
  document.addEventListener('visibilitychange', onVisibility)
})
onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', fastForward)
  window.removeEventListener('keydown', fastForward)
  document.removeEventListener('visibilitychange', onVisibility)
  intro?.kill()
})
</script>

<template>
  <div class="login-stage">
  <div ref="root" class="login login-pearl">
    <LoginBrandPanel />

    <!-- 右：登录卡 -->
    <section class="login-side" @pointermove="onTilt" @pointerleave="resetTilt">
      <div ref="card" class="login-card m-float m-glow" role="region" aria-labelledby="login-heading" @pointermove="trackGlow">
        <span class="glow-layer" aria-hidden="true" />
        <div class="login-card-inner">
          <span class="eyebrow">账号登录</span>
          <h2 id="login-heading" class="login-card-title">登录</h2>
          <p class="login-card-sub">使用管理员分配的账号登录系统</p>

          <form class="login-form" novalidate @submit.prevent="submit">
            <Field label="账号" :error="errors.username">
              <TextInput v-model="form.username" icon="user" placeholder="请输入账号" autocomplete="username" :invalid="!!errors.username" />
            </Field>
            <Field label="密码" :error="errors.password">
              <TextInput v-model="form.password" type="password" icon="lock" placeholder="请输入密码" autocomplete="current-password" :invalid="!!errors.password" />
            </Field>
            <Field label="验证码" :error="errors.captcha">
              <div class="captcha-row">
                <TextInput v-model="form.captcha" icon="shield" placeholder="请输入右侧字符" :maxlength="6" autocomplete="off" :invalid="!!errors.captcha" />
                <button type="button" class="captcha-box m-groove" :disabled="captchaLoading" aria-label="看不清，换一张" title="看不清，换一张" @click="refreshCaptcha">
                  <span v-if="captchaCode" class="captcha-code" aria-hidden="true">
                    <span v-for="(ch, i) in captchaCode.split('')" :key="i" :style="{ rotate: `${(i % 2 ? 1 : -1) * (6 + i * 3)}deg` }">{{ ch }}</span>
                  </span>
                  <Icon v-else name="refresh" :size="16" />
                </button>
              </div>
            </Field>
            <div class="login-options">
              <Checkbox v-model="remember" label="记住登录状态" />
            </div>
            <div class="login-submit-slot">
              <Button v-if="!succeeded" type="submit" variant="primary" block :loading="submitting" class="login-submit">登录</Button>
              <div v-else class="success-orb" role="status" aria-label="登录成功，正在进入">
                <svg width="30" height="30" viewBox="0 0 46 46" aria-hidden="true">
                  <circle cx="23" cy="23" r="20" stroke-dasharray="126" />
                  <path d="m15 23.5 5.5 5.5L31.5 18" stroke-dasharray="24" />
                </svg>
              </div>
            </div>
          </form>
        </div>
      </div>
    </section>
  </div>
  </div>
</template>

<style scoped>
/* 登录卡的校验红字浮层化：不占布局流，卡片高度恒定（红字悬在 16px 字段间隙中） */
.login-card .field { position: relative; }
.login-card :deep(.field-error) { position: absolute; left: 2px; top: calc(100% - 3px); }
.login-stage { container-type: inline-size; background: var(--stage); }
.login-pearl { grid-template-columns: minmax(0, 1.3fr) minmax(400px, 1fr); padding: 16px; }
.login-pearl:not(.is-entrance-ready) :deep(.brand-top),
.login-pearl:not(.is-entrance-ready) :deep(.brand-copy),
.login-pearl:not(.is-entrance-ready) :deep(.journey),
.login-pearl:not(.is-entrance-ready) :deep(.brand-footer) { opacity: 0; }
@container (max-width: 1160px) {
  .login-pearl { grid-template-columns: minmax(0, 1.15fr) minmax(360px, 1fr); }
  .login-side { padding-inline: 22px; min-height: auto; }
}
@container (max-width: 900px) {
  .login-pearl { display: flex; flex-direction: column; padding: 0; }
  .login-side { padding: 0 18px 26px; min-height: auto; flex: 1; background: var(--canvas); }
}
</style>

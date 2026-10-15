<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import Icon from '@/components/Icon.vue'
import LoginBrandPanel from '@/components/business/LoginBrandPanel.vue'
import Button from '@/components/ui/Button.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Field from '@/components/ui/Field.vue'
import Segmented from '@/components/ui/Segmented.vue'
import TextInput from '@/components/ui/TextInput.vue'
import { installPressFeedback, revealCascade } from '@/composables/motion'

const theme = ref('light')
const size = ref('desktop')
const brand = ref<HTMLElement | null>(null)
const username = ref('')
const password = ref('')
const captcha = ref('')
const remember = ref(true)
const notice = ref('')
const code = ref('7GDF')
const themes = [{ label: '浅色', value: 'light' }, { label: '深色', value: 'dark' }]
const sizes = [{ label: '桌面', value: 'desktop' }, { label: '手机', value: 'mobile' }]
watch(theme, value => { document.documentElement.dataset.theme = value })
onMounted(() => {
  installPressFeedback()
  revealCascade(brand.value, '.brand-top, .brand-copy, .archive-figure, .journey, .brand-footer')
})
</script>

<template>
  <div class="sample-shell">
    <header class="sample-toolbar">
      <div class="sample-heading"><span class="sample-dot" />登录页 · 品牌采样<span class="sample-hint">珍珠玻璃 / 视觉区域待设计</span></div>
      <div class="sample-controls">
        <Segmented v-model="theme" :options="themes" label="配色预览" />
        <Segmented v-model="size" :options="sizes" label="尺寸预览" />
      </div>
    </header>
    <div class="sample-stage" :class="{ 'is-mobile': size === 'mobile' }">
      <main class="sample-layout">
<div ref="brand" class="sample-brand"><LoginBrandPanel /></div>

        <section class="sample-login-side" aria-labelledby="login-title">
          <div class="login-card m-float">
            <div class="login-card-inner">
              <div class="login-eyebrow">账号登录</div>
              <h2 class="login-card-title" id="login-title">登录</h2>
              <p class="login-card-sub">使用管理员分配的账号登录系统</p>
              <form class="login-form" autocomplete="off" @submit.prevent="notice = '这是设计采样，尚未连接登录服务。'">
                <Field label="账号"><TextInput v-model="username" icon="user" placeholder="请输入账号" autocomplete="off" /></Field>
                <Field label="密码"><TextInput v-model="password" icon="lock" type="password" placeholder="请输入密码" autocomplete="off" /></Field>
                <Field label="验证码"><div class="captcha-row"><TextInput v-model="captcha" icon="shield" placeholder="请输入右侧字符" :maxlength="4" /><button type="button" class="captcha-box m-groove" aria-label="更换示例验证码" @click="code = code === '7GDF' ? 'K8M2' : '7GDF'"><span class="captcha-code">{{ code }}</span></button></div></Field>
                <div class="login-options"><Checkbox v-model="remember" label="记住登录状态" /></div>
                <div class="login-submit-slot"><Button type="submit" variant="primary" block class="login-submit">登录</Button></div>
                <p v-if="notice" class="sample-notice" role="status">{{ notice }}</p>
              </form>
            </div>
          </div>
          <p class="login-trust"><Icon name="lock" :size="12" />统一身份认证 · 按授权访问</p>
        </section>
      </main>
    </div>
    <div class="sample-footnote">设计采样 · 主视觉区域留白 · 登录表单仅作样式预览</div>
  </div>
</template>

<style scoped>
.sample-brand { display: grid; min-width: 0; }
.sample-shell { background: var(--stage); min-height: 100dvh; }
.sample-toolbar { min-height: 58px; padding: 10px 26px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid var(--line); background: var(--rail-bg); }
.sample-heading { display: flex; align-items: center; gap: 10px; font-size: 12px; font-weight: 650; }
.sample-dot { width: 7px; height: 7px; background: var(--blue); border-radius: 50%; }
.sample-hint { padding-left: 12px; margin-left: 4px; border-left: 1px solid var(--line-strong); color: var(--ink-soft); font-weight: 400; }
.sample-controls { display: flex; gap: 14px; }
.sample-stage { width: 100%; max-width: 1920px; margin: auto; container-type: inline-size; }
.sample-stage.is-mobile { max-width: 390px; margin-top: 22px; border: 1px solid var(--line-strong); border-radius: 26px; overflow: hidden; box-shadow: var(--e3); }
.sample-layout { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(400px, 1fr); padding: 16px; min-height: calc(100dvh - 83px); }
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
.archive-figure { margin: 0; flex: 1; min-height: calc(clamp(230px, calc(100dvh - 618px), 340px) + 50px); }
figcaption { display: flex; align-items: center; justify-content: center; gap: 12px; font-size: 11px; line-height: 1.7; color: var(--ink-muted); min-height: 24px; text-align: center; }
.caption-line { height: 1px; width: 20px; background: var(--line-strong); flex: none; }
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
.sample-login-side { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 50px clamp(24px, 4.2vw, 70px); position: relative; background: radial-gradient(ellipse at 60% 40%, var(--canvas), transparent 70%); min-width: 0; }
.sample-login-side .login-card { max-width: 420px; }
.login-eyebrow { font-size: 11px; font-weight: 650; color: var(--ink-soft); }
.login-trust { display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--ink-soft); font-size: 11px; margin-top: 24px; }
.sample-notice { font-size: 12px; color: var(--ink-muted); }
.sample-footnote { text-align: center; padding: 0 18px 10px; font-size: 10px; color: var(--ink-soft); }
.captcha-code { letter-spacing: .17em; font-style: italic; }
@container (min-width: 1600px) {
  .brand-panel { padding: 38px 54px 26px; }
  .brand-copy { padding-top: 48px; }
  .brand-copy p { font-size: 15px; }
  .journey-step strong { font-size: 13px; }
  .journey-step small, figcaption { font-size: 12px; }
}
@container (max-width: 1160px) {
  .brand-edition, .journey-step small { display: none; }
  .sample-layout { grid-template-columns: minmax(0, 1.15fr) minmax(360px, 1fr); }
  .brand-panel { padding: 26px 24px 20px; }
  .sample-login-side { padding-inline: 22px; }
  .journey { grid-template-columns: repeat(3, minmax(0, 1fr) 16px) minmax(0, 1fr); column-gap: 4px; }
  .journey-step { padding: 12px 8px; }
  .brand-copy h1 { font-size: 36px; }
}
@container (max-width: 900px) {
  .sample-layout { display: flex; flex-direction: column; padding: 0; min-height: auto; }
  .brand-panel { border: 0; border-radius: 0; box-shadow: none; padding: 26px 26px 28px; }
  .brand-copy { padding-top: 28px; }
  .brand-copy h1 { font-size: 30px; line-height: 1.3; }
  .brand-copy p { font-size: 12px; margin-top: 10px; }
  .brand-copy p br { display: none; }
  .intro-label, .archive-figure, .journey, .brand-footer { display: none; }
  .sample-login-side { padding: 0 18px 26px; margin-top: -2px; background: var(--canvas); }
  .sample-login-side .login-card { max-width: 460px; }
  .login-card-inner { padding: 26px 24px; }
  .login-trust { margin-top: 20px; }
  .brand-name { font-size: 14px; }
}
@media (max-width: 600px) {
  .sample-toolbar { padding: 10px 14px; flex-wrap: wrap; gap: 10px; }
  .sample-heading { font-size: 11px; }
  .sample-hint { display: none; }
  .sample-controls { gap: 8px; }
  .sample-footnote { padding-top: 12px; }
  .sample-stage.is-mobile { margin-top: 0; border: 0; border-radius: 0; }
}
@container (max-width: 350px) {
  .brand-panel { padding-inline: 22px; }
  .brand-copy h1 { font-size: 27px; }
  .login-card-inner { padding: 24px 18px; }
  .captcha-box { width: 84px; }
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

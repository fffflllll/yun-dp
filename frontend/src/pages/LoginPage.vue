<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ArrowRight, KeyRound, LoaderCircle, LockKeyhole, Phone } from '@lucide/vue'
import { discoveryApi } from '../api/discovery'
import { useToast } from '../composables/useToast'

const passwordMode = computed(() => location.pathname.endsWith('/login2.html'))
const phone = ref('')
const code = ref('')
const password = ref('')
const agreed = ref(false)
const sending = ref(false)
const submitting = ref(false)
const seconds = ref(0)
const { show } = useToast()
let timer: number | undefined

onBeforeUnmount(() => window.clearInterval(timer))
async function sendCode() {
  if (!/^1\d{10}$/.test(phone.value)) { show('请输入 11 位手机号码', 'info'); return }
  sending.value = true
  try {
    await discoveryApi.sendCode(phone.value)
    seconds.value = 60
    timer = window.setInterval(() => { if (--seconds.value <= 0) window.clearInterval(timer) }, 1000)
    show('验证码已发送', 'success')
  } catch (error) { show(error instanceof Error ? error.message : '发送失败', 'error') }
  finally { sending.value = false }
}
async function login() {
  if (!agreed.value) { show('请先阅读并同意服务协议', 'info'); return }
  if (!phone.value || (!passwordMode.value && !code.value) || (passwordMode.value && !password.value)) { show('请填写完整登录信息', 'info'); return }
  submitting.value = true
  try {
    const token = await discoveryApi.login(passwordMode.value ? { phone: phone.value, password: password.value } : { phone: phone.value, code: code.value })
    sessionStorage.setItem('token', token)
    window.location.assign('/index.html')
  } catch (error) { show(error instanceof Error ? error.message : '登录失败', 'error') }
  finally { submitting.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <a class="product-brand" href="/index.html"><span class="product-brand__spark">✦</span><span>MeetMate</span></a>
      <p class="eyebrow">WELCOME BACK</p><h1>{{ passwordMode ? '密码登录' : '手机号快捷登录' }}</h1><p class="login-card__intro">开始记录好味道，也为下一次相聚做准备。</p>
      <form @submit.prevent="login"><label>手机号码<div class="input-wrap"><Phone :size="18" /><input v-model.trim="phone" inputmode="tel" maxlength="11" placeholder="请输入手机号" autocomplete="tel" /></div></label>
        <label v-if="!passwordMode">验证码<div class="input-wrap"><KeyRound :size="18" /><input v-model.trim="code" inputmode="numeric" placeholder="请输入验证码" autocomplete="one-time-code" /><button class="input-action" type="button" :disabled="sending || seconds > 0" @click="sendCode">{{ seconds ? `${seconds}s` : '发送验证码' }}</button></div></label>
        <label v-else>密码<div class="input-wrap"><LockKeyhole :size="18" /><input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" /></div></label>
        <label class="agreement"><input v-model="agreed" type="checkbox" /> <span>我已阅读并同意服务协议与隐私政策</span></label>
        <button class="primary-button primary-button--full" :disabled="submitting" type="submit"><LoaderCircle v-if="submitting" class="spin" :size="18" /><ArrowRight v-else :size="18" />{{ submitting ? '正在登录' : '进入 MeetMate' }}</button>
      </form>
      <a class="login-switch" :href="passwordMode ? '/login.html' : '/login2.html'">{{ passwordMode ? '使用验证码登录' : '使用密码登录' }}</a>
    </section>
  </main>
</template>

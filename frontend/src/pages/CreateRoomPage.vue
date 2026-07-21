<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Clipboard, LoaderCircle, MapPin, Minus, Plus, Users } from '@lucide/vue'
import AppHeader from '../components/AppHeader.vue'
import BaseModal from '../components/BaseModal.vue'
import { meetmateApi } from '../api/meetmate'
import { requireLogin } from '../api/http'
import { useToast } from '../composables/useToast'

const form = reactive({
  title: '',
  centerX: 120.149993,
  centerY: 30.334229,
  searchRadiusMeter: 5000,
  maxMembers: 6,
})
const creating = ref(false)
const created = ref<{ roomId: number; inviteCode: string } | null>(null)
const { show } = useToast()
const radiusLabel = computed(() => form.searchRadiusMeter >= 1000 ? `${form.searchRadiusMeter / 1000} 公里` : `${form.searchRadiusMeter} 米`)

onMounted(requireLogin)

function stepMembers(delta: number) {
  form.maxMembers = Math.min(20, Math.max(2, form.maxMembers + delta))
}

async function createRoom() {
  if (!form.title.trim()) {
    show('请填写聚会名称', 'info')
    return
  }
  creating.value = true
  try {
    const result = await meetmateApi.createRoom({ ...form, title: form.title.trim() })
    created.value = { roomId: result.roomId, inviteCode: result.inviteCode }
  } catch (error) {
    show(error instanceof Error ? error.message : '创建失败', 'error')
  } finally {
    creating.value = false
  }
}

async function copyCode() {
  if (!created.value) return
  try {
    await navigator.clipboard.writeText(created.value.inviteCode)
    show('邀请码已复制', 'success')
  } catch {
    show(`邀请码：${created.value.inviteCode}`, 'info')
  }
}
</script>

<template>
  <div class="app-shell">
    <AppHeader title="创建聚会" back />
    <main class="page page--form">
      <header class="form-intro">
        <p class="eyebrow">新房间</p>
        <h1>把人聚齐，剩下的交给 MeetMate</h1>
        <p>先设定聚会范围，成员加入后再分别填写自己的偏好。</p>
      </header>

      <form class="creation-form" @submit.prevent="createRoom">
        <section class="form-section">
          <div class="section-heading"><div><p class="eyebrow">基本信息</p><h2>这次聚会叫什么？</h2></div></div>
          <div class="field">
            <label for="room-title">聚会名称</label>
            <input id="room-title" v-model="form.title" maxlength="100" autocomplete="off" placeholder="例如：周六晚餐小聚" />
            <span class="field-counter">{{ form.title.length }}/100</span>
          </div>
        </section>

        <section class="form-section">
          <div class="section-heading"><div><p class="eyebrow">活动范围</p><h2>从哪里开始找？</h2></div><MapPin :size="21" /></div>
          <div class="coordinate-grid">
            <div class="field"><label for="longitude">经度</label><input id="longitude" v-model.number="form.centerX" type="number" min="-180" max="180" step="0.000001" required /></div>
            <div class="field"><label for="latitude">纬度</label><input id="latitude" v-model.number="form.centerY" type="number" min="-90" max="90" step="0.000001" required /></div>
          </div>
          <div class="field field--range">
            <div class="field-label-row"><label for="radius">搜索半径</label><strong>{{ radiusLabel }}</strong></div>
            <input id="radius" v-model.number="form.searchRadiusMeter" type="range" min="500" max="20000" step="500" />
            <div class="range-scale"><span>500 米</span><span>20 公里</span></div>
          </div>
        </section>

        <section class="form-section">
          <div class="section-heading"><div><p class="eyebrow">参与人数</p><h2>最多邀请多少人？</h2></div><Users :size="21" /></div>
          <div class="stepper" aria-label="最大成员数">
            <button type="button" class="icon-button" aria-label="减少人数" :disabled="form.maxMembers <= 2" @click="stepMembers(-1)"><Minus :size="20" /></button>
            <output><strong>{{ form.maxMembers }}</strong><span>人</span></output>
            <button type="button" class="icon-button" aria-label="增加人数" :disabled="form.maxMembers >= 20" @click="stepMembers(1)"><Plus :size="20" /></button>
          </div>
        </section>

        <button class="primary-button primary-button--large" type="submit" :disabled="creating">
          <LoaderCircle v-if="creating" class="spin" :size="20" /><Plus v-else :size="20" />{{ creating ? '正在创建' : '创建房间' }}
        </button>
      </form>
    </main>

    <BaseModal :open="Boolean(created)" title="房间已创建" @close="created = null">
      <div v-if="created" class="success-content">
        <span class="success-content__icon"><Check :size="28" /></span>
        <p>把邀请码发给朋友，他们就能加入这次聚会。</p>
        <button class="invite-code" type="button" @click="copyCode"><code>{{ created.inviteCode }}</code><Clipboard :size="18" /></button>
        <a class="primary-button primary-button--full" :href="`/meetmate-room.html?roomId=${created.roomId}`">进入房间</a>
      </div>
    </BaseModal>
  </div>
</template>

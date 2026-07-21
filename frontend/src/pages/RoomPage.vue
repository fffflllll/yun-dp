<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Check, Clipboard, Info, KeyRound, LoaderCircle, LockKeyhole, Sparkles, Users } from '@lucide/vue'
import AppHeader from '../components/AppHeader.vue'
import ChatPanel from '../components/ChatPanel.vue'
import MembersPanel from '../components/MembersPanel.vue'
import PlanningPanel from '../components/PlanningPanel.vue'
import PreferencePanel from '../components/PreferencePanel.vue'
import StateBadge from '../components/StateBadge.vue'
import { meetmateApi } from '../api/meetmate'
import { requireLogin } from '../api/http'
import { useToast } from '../composables/useToast'
import { formatDate } from '../utils/format'
import type { CurrentUser, RoomDetail } from '../types'

const roomId = Number(new URLSearchParams(location.search).get('roomId'))
const room = ref<RoomDetail | null>(null)
const user = ref<CurrentUser | null>(null)
const loading = ref(true)
const error = ref('')
const copied = ref(false)
const { show } = useToast()

const preferenceEditable = computed(() => ['COLLECTING_PREFERENCES', 'MEMBERS_LOCKED'].includes(room.value?.status || ''))
const stages = computed(() => [
  { label: '邀请成员', icon: Users, done: room.value?.status !== 'COLLECTING_PREFERENCES' },
  { label: '确认偏好', icon: LockKeyhole, done: ['READY_TO_PLAN', 'PLANNING', 'WAITING_INPUT', 'PLANS_READY', 'FINALIZED'].includes(room.value?.status || '') },
  { label: '生成方案', icon: Sparkles, done: ['PLANS_READY', 'FINALIZED'].includes(room.value?.status || '') },
  { label: '聚会确定', icon: Check, done: room.value?.status === 'FINALIZED' },
])

onMounted(async () => {
  if (!requireLogin()) return
  if (!Number.isInteger(roomId) || roomId <= 0) {
    error.value = '房间链接无效'
    loading.value = false
    return
  }
  await loadPage()
})

async function loadPage() {
  try {
    const [roomResult, userResult] = await Promise.all([meetmateApi.room(roomId), meetmateApi.currentUser()])
    room.value = roomResult
    user.value = userResult
    document.title = `${roomResult.title} - MeetMate`
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshRoom() {
  try { room.value = await meetmateApi.room(roomId) }
  catch (reason) { show(reason instanceof Error ? reason.message : '房间更新失败', 'error') }
}

async function copyInviteCode() {
  if (!room.value) return
  try {
    await navigator.clipboard.writeText(room.value.inviteCode)
    copied.value = true
    show('邀请码已复制', 'success')
    window.setTimeout(() => { copied.value = false }, 1600)
  } catch { show(`邀请码：${room.value.inviteCode}`, 'info') }
}
</script>

<template>
  <div class="app-shell">
    <AppHeader :title="room?.title || '房间详情'" back>
      <button v-if="room" class="header-code" type="button" @click="copyInviteCode"><KeyRound :size="15" /><code>{{ room.inviteCode }}</code><Check v-if="copied" :size="15" /><Clipboard v-else :size="15" /></button>
    </AppHeader>

    <main v-if="loading" class="page center-state center-state--page"><LoaderCircle class="spin" :size="28" /><span>正在打开房间</span></main>
    <main v-else-if="error || !room" class="page error-state"><Info :size="28" /><h1>无法打开房间</h1><p>{{ error }}</p><a class="primary-button" href="/meetmate.html">返回聚会大厅</a></main>
    <main v-else class="page page--room">
      <section class="room-overview">
        <div><div class="room-overview__status"><StateBadge :status="room.status" /><span>房间 #{{ room.roomId }}</span></div><h1>{{ room.title }}</h1><p>{{ room.members.length }} 位成员 · {{ room.searchRadiusMeter / 1000 }} 公里范围 · 创建于 {{ formatDate(room.createTime, true) }}</p></div>
        <button class="invite-button" type="button" @click="copyInviteCode"><span>邀请码</span><code>{{ room.inviteCode }}</code><Clipboard :size="18" /></button>
      </section>

      <ol class="stage-strip" aria-label="聚会进度">
        <li v-for="(stage, index) in stages" :key="stage.label" :class="{ 'is-done': stage.done }"><span><component :is="stage.icon" :size="16" /></span><div><small>0{{ index + 1 }}</small><strong>{{ stage.label }}</strong></div></li>
      </ol>

      <div class="room-workspace">
        <div class="room-main-column">
          <PreferencePanel :room-id="room.roomId" :editable="preferenceEditable" @saved="refreshRoom" />
          <PlanningPanel :room="room" :current-user-id="user?.id" @changed="refreshRoom" />
        </div>
        <aside class="room-side-column">
          <MembersPanel :members="room.members" :max-members="room.maxMembers" :current-user-id="user?.id" />
          <ChatPanel :room-id="room.roomId" :current-user-id="user?.id" />
        </aside>
      </div>
    </main>
  </div>
</template>

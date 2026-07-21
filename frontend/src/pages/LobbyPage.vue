<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, CalendarPlus, DoorOpen, KeyRound, LoaderCircle, RefreshCw, TriangleAlert, Users } from '@lucide/vue'
import AppHeader from '../components/AppHeader.vue'
import BaseModal from '../components/BaseModal.vue'
import StateBadge from '../components/StateBadge.vue'
import { meetmateApi } from '../api/meetmate'
import { requireLogin } from '../api/http'
import { useToast } from '../composables/useToast'
import { avatarText, formatDate } from '../utils/format'
import type { RoomSummary } from '../types'

const rooms = ref<RoomSummary[]>([])
const loading = ref(true)
const joinOpen = ref(new URLSearchParams(location.search).get('openJoin') === '1')
const joinCode = ref('')
const joining = ref(false)
const loadError = ref('')
const { show } = useToast()

onMounted(() => {
  if (!requireLogin()) return
  void loadRooms()
})

async function loadRooms() {
  loading.value = true
  loadError.value = ''
  try {
    rooms.value = await meetmateApi.listRooms()
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '房间列表加载失败'
    show(loadError.value, 'error')
  } finally {
    loading.value = false
  }
}

async function joinRoom() {
  const code = joinCode.value.trim().toUpperCase()
  if (!/^[A-Z0-9]{6}$/.test(code)) {
    show('请输入完整的 6 位邀请码', 'info')
    return
  }
  joining.value = true
  try {
    const roomId = await meetmateApi.joinRoom(code)
    show('已加入聚会', 'success')
    window.location.assign(`/meetmate-room.html?roomId=${roomId}`)
  } catch (error) {
    show(error instanceof Error ? error.message : '加入失败', 'error')
  } finally {
    joining.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <AppHeader title="MeetMate" />
    <main class="page page--lobby">
      <section class="lobby-intro">
        <div>
          <p class="eyebrow">一起定下这顿饭</p>
          <h1>我的聚会</h1>
          <p>收集每个人的偏好，让规划变得简单、透明。</p>
        </div>
        <div class="lobby-actions">
          <a class="primary-button" href="/meetmate-create.html"><CalendarPlus :size="19" />创建聚会</a>
          <button class="secondary-button" type="button" @click="joinOpen = true"><KeyRound :size="19" />加入聚会</button>
        </div>
      </section>

      <section class="room-list-section" aria-labelledby="rooms-title">
        <div class="section-heading">
          <div><p class="eyebrow">最近更新</p><h2 id="rooms-title">进行中的房间</h2></div>
          <span v-if="rooms.length" class="section-meta">{{ rooms.length }} 个房间</span>
        </div>

        <div v-if="loading" class="center-state"><LoaderCircle class="spin" :size="24" /><span>正在同步房间</span></div>
        <div v-else-if="loadError" class="empty-state empty-state--error">
          <span class="empty-state__icon"><TriangleAlert :size="28" /></span>
          <h3>房间列表暂时无法加载</h3>
          <p>{{ loadError }}</p>
          <button class="secondary-button" type="button" @click="loadRooms"><RefreshCw :size="18" />重新加载</button>
        </div>
        <div v-else-if="rooms.length" class="room-grid">
          <a v-for="room in rooms" :key="room.roomId" class="room-card" :href="`/meetmate-room.html?roomId=${room.roomId}`">
            <div class="room-card__top"><StateBadge :status="room.status" /><ArrowRight :size="19" /></div>
            <h3>{{ room.title }}</h3>
            <div class="room-card__meta">
              <span><Users :size="16" />{{ room.memberCount }}/{{ room.maxMembers }}</span>
              <span>{{ formatDate(room.createTime, true) }}</span>
            </div>
            <div class="room-card__footer">
              <div class="avatar-stack">
                <span v-for="member in (room.members || []).slice(0, 4)" :key="member.userId" class="avatar avatar--small">
                  {{ avatarText(member.nickName, member.userId) }}
                </span>
              </div>
              <code>{{ room.inviteCode }}</code>
            </div>
          </a>
        </div>
        <div v-else class="empty-state">
          <span class="empty-state__icon"><DoorOpen :size="28" /></span>
          <h3>还没有聚会房间</h3>
          <p>创建一个房间，或用朋友发来的邀请码加入。</p>
          <a class="primary-button" href="/meetmate-create.html"><CalendarPlus :size="18" />创建第一个聚会</a>
        </div>
      </section>
    </main>

    <BaseModal :open="joinOpen" title="加入聚会" @close="joinOpen = false">
      <form class="join-form" @submit.prevent="joinRoom">
        <label for="join-code">邀请码</label>
        <input id="join-code" v-model="joinCode" class="code-input" inputmode="text" autocomplete="off" maxlength="6" autofocus placeholder="6 位字母或数字" @input="joinCode = joinCode.toUpperCase()" />
        <p>向房主获取邀请码即可加入，字母不区分大小写。</p>
        <button class="primary-button primary-button--full" type="submit" :disabled="joining">
          <LoaderCircle v-if="joining" class="spin" :size="18" /><KeyRound v-else :size="18" />{{ joining ? '正在加入' : '加入房间' }}
        </button>
      </form>
    </BaseModal>
  </div>
</template>

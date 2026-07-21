<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'
import { LoaderCircle, MessageCircle, RotateCcw, Send } from '@lucide/vue'
import { meetmateApi } from '../api/meetmate'
import { useToast } from '../composables/useToast'
import { avatarText, formatDate } from '../utils/format'
import type { ChatMessage } from '../types'

const props = defineProps<{ roomId: number; currentUserId?: number }>()
const messages = ref<ChatMessage[]>([])
const newMessage = ref('')
const loading = ref(true)
const sending = ref(false)
const list = useTemplateRef<HTMLDivElement>('list')
const { show } = useToast()
let pollTimer: number | undefined
let requestInFlight = false

const lastMessageId = computed(() => messages.value.reduce((max, item) => typeof item.id === 'number' ? Math.max(max, item.id) : max, 0))

onMounted(async () => {
  await loadMessages(true)
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') void loadMessages(false)
  }, 4000)
})
onBeforeUnmount(() => window.clearInterval(pollTimer))

async function loadMessages(replace: boolean) {
  if (requestInFlight) return
  requestInFlight = true
  try {
    const incoming = await meetmateApi.messages(props.roomId, replace ? 0 : lastMessageId.value)
    mergeMessages(incoming, replace)
    await scrollToBottom()
  } catch (error) {
    if (replace) show(error instanceof Error ? error.message : '聊天记录加载失败', 'error')
  } finally {
    loading.value = false
    requestInFlight = false
  }
}

function mergeMessages(incoming: ChatMessage[], replace = false) {
  const optimistic = messages.value.filter((item) => typeof item.id === 'string')
  const serverMessages = replace ? [] : messages.value.filter((item) => typeof item.id === 'number')
  const byId = new Map(serverMessages.map((item) => [String(item.id), item]))
  incoming.forEach((item) => byId.set(String(item.id), item))
  messages.value = [...byId.values()].sort((a, b) => Number(a.id) - Number(b.id)).concat(optimistic)
}

async function sendMessage() {
  const content = newMessage.value.trim()
  if (!content || sending.value || !props.currentUserId) return
  const optimisticId = `pending-${Date.now()}`
  const optimistic: ChatMessage = {
    id: optimisticId,
    userId: props.currentUserId,
    userName: '我',
    content,
    createTime: new Date().toISOString(),
    pending: true,
  }
  messages.value.push(optimistic)
  newMessage.value = ''
  sending.value = true
  await scrollToBottom()
  try {
    const saved = await meetmateApi.sendMessage(props.roomId, content)
    const index = messages.value.findIndex((item) => item.id === optimisticId)
    if (index >= 0) messages.value.splice(index, 1, saved)
    await loadMessages(false)
  } catch (error) {
    optimistic.pending = false
    optimistic.failed = true
    show(error instanceof Error ? error.message : '消息发送失败', 'error')
  } finally {
    sending.value = false
  }
}

function retryMessage(message: ChatMessage) {
  messages.value = messages.value.filter((item) => item.id !== message.id)
  newMessage.value = message.content
  void sendMessage()
}

async function scrollToBottom() {
  await nextTick()
  if (list.value) list.value.scrollTop = list.value.scrollHeight
}
</script>

<template>
  <section class="panel chat-panel">
    <div class="panel-heading"><div><span class="panel-kicker"><MessageCircle :size="15" />房间聊天</span><h2>成员可见</h2></div><span class="live-indicator"><i></i>自动同步</span></div>
    <div ref="list" class="chat-list" aria-live="polite">
      <div v-if="loading" class="center-state center-state--compact"><LoaderCircle class="spin" :size="21" /></div>
      <div v-else-if="!messages.length" class="chat-empty"><MessageCircle :size="24" /><strong>还没有消息</strong><span>先和大家打个招呼吧</span></div>
      <article v-for="message in messages" :key="message.id" class="chat-message" :class="{ 'is-me': message.userId === currentUserId, 'is-pending': message.pending, 'is-failed': message.failed }">
        <img v-if="message.icon" class="avatar avatar--small" :src="message.icon" alt="" /><span v-else class="avatar avatar--small">{{ avatarText(message.userName, message.userId) }}</span>
        <div class="chat-message__body">
          <div class="chat-message__meta"><strong>{{ message.userId === currentUserId ? '我' : message.userName || '成员' }}</strong><span>{{ formatDate(message.createTime) }}</span></div>
          <p>{{ message.content }}</p>
          <button v-if="message.failed" class="retry-button" type="button" @click="retryMessage(message)"><RotateCcw :size="14" />发送失败，点击重试</button>
        </div>
      </article>
    </div>
    <form class="chat-composer" @submit.prevent="sendMessage">
      <label class="sr-only" for="chat-message">发送消息</label>
      <textarea id="chat-message" v-model="newMessage" rows="1" maxlength="500" placeholder="发消息给房间成员" @keydown.enter.exact.prevent="sendMessage"></textarea>
      <button class="send-button" type="submit" :disabled="!newMessage.trim() || sending" aria-label="发送消息"><LoaderCircle v-if="sending" class="spin" :size="19" /><Send v-else :size="19" /></button>
    </form>
  </section>
</template>

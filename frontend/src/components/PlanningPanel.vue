<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bot, Check, ChevronRight, LoaderCircle, LockKeyhole, MapPin, Sparkles } from '@lucide/vue'
import { meetmateApi } from '../api/meetmate'
import { useToast } from '../composables/useToast'
import type { Clarification, PlanEvent, Proposal, RoomDetail } from '../types'

const props = defineProps<{ room: RoomDetail; currentUserId?: number }>()
const emit = defineEmits<{ changed: [] }>()
const events = ref<PlanEvent[]>([])
const proposals = ref<Proposal[]>([])
const clarification = ref<Clarification | null>(null)
const runId = ref<number | null>(props.room.latestPlanRunId || null)
const runStatus = ref(props.room.latestPlanRunStatus || '')
const busy = ref(false)
const { show } = useToast()
let timer: number | undefined

const isOwner = computed(() => props.currentUserId === props.room.creatorId)
const clarificationId = computed(() => clarification.value?.clarificationId || clarification.value?.id)
const activeRun = computed(() => ['QUEUED', 'RUNNING', 'WAITING_INPUT'].includes(runStatus.value))
const stageCopy = computed(() => {
  const copy: Record<string, string> = {
    COLLECTING_PREFERENCES: '成员填写偏好后，房主可以锁定名单。',
    MEMBERS_LOCKED: '名单已锁定，等待所有成员确认偏好。',
    READY_TO_PLAN: '偏好已收齐，可以开始生成聚餐方案。',
    PLANNING: '正在分析偏好并筛选合适的餐厅。',
    WAITING_INPUT: '需要一位成员补充信息后才能继续。',
    PLANS_READY: '候选方案已经生成，等待房主确认。',
    FINALIZED: '聚会方案已确定。',
    FAILED: '本次规划未完成，可以查看执行记录。',
  }
  return copy[props.room.status] || '聚餐规划会综合所有成员的确认偏好。'
})

onMounted(async () => {
  if (runId.value) await loadRun()
  timer = window.setInterval(() => { if (activeRun.value && document.visibilityState === 'visible') void loadRun() }, 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))

async function loadRun() {
  if (!runId.value) return
  try {
    const data = await meetmateApi.planRun(runId.value)
    events.value = data.events || []
    proposals.value = data.proposals || []
    clarification.value = data.clarification || null
    runStatus.value = data.run.status
    if (!activeRun.value) emit('changed')
  } catch (error) {
    console.warn('[MeetMate] planning refresh failed', error)
  }
}

async function lockMembers() {
  busy.value = true
  try {
    await meetmateApi.lockMembers(props.room.roomId)
    show('成员名单已锁定', 'success')
    emit('changed')
  } catch (error) {
    show(error instanceof Error ? error.message : '锁定失败', 'error')
  } finally { busy.value = false }
}

async function startPlan() {
  busy.value = true
  try {
    const result = await meetmateApi.startPlan(props.room.roomId)
    runId.value = result.runId
    runStatus.value = 'QUEUED'
    show('AI 规划已开始', 'success')
    await loadRun()
    emit('changed')
  } catch (error) {
    show(error instanceof Error ? error.message : '规划启动失败', 'error')
  } finally { busy.value = false }
}

async function answer(answer: string) {
  if (!runId.value || !clarificationId.value) return
  busy.value = true
  try {
    await meetmateApi.answerClarification(runId.value, clarificationId.value, answer)
    show('回答已提交', 'success')
    await loadRun()
  } catch (error) {
    show(error instanceof Error ? error.message : '提交失败', 'error')
  } finally { busy.value = false }
}

async function confirmProposal(proposalId?: number) {
  if (!runId.value || !proposalId) return
  busy.value = true
  try {
    await meetmateApi.confirmPlan(runId.value, proposalId)
    show('聚会方案已确认', 'success')
    emit('changed')
  } catch (error) {
    show(error instanceof Error ? error.message : '确认失败', 'error')
  } finally { busy.value = false }
}

function eventLabel(type: string) {
  const labels: Record<string, string> = {
    RUN_QUEUED: '进入规划队列', RUN_STARTED: '开始规划', PREFERENCES_READ: '读取成员偏好',
    RESTAURANTS_RECALLED: '搜索候选餐厅', RESTAURANTS_FILTERED: '筛选候选',
    CONSTRAINTS_CHECKED: '检查限制', PLANS_DRAFTED: '生成方案', PLANS_VALIDATED: '校验方案',
    WAITING_INPUT: '等待成员回答', RUN_COMPLETED: '规划完成', RUN_CANCELLED: '规划已取消', RUN_FAILED: '规划结束', PLAN_CONFIRMED: '方案已确认',
  }
  return labels[type] || type
}
</script>

<template>
  <section class="panel planning-panel">
    <div class="panel-heading">
      <div><span class="panel-kicker"><Bot :size="15" />AI 规划</span><h2>从偏好到聚餐方案</h2></div>
      <LoaderCircle v-if="activeRun" class="spin planning-spinner" :size="21" />
    </div>
    <div class="planning-summary"><Sparkles :size="19" /><p>{{ stageCopy }}</p></div>

    <div v-if="isOwner && room.status === 'COLLECTING_PREFERENCES'" class="planning-action">
      <button class="secondary-button" type="button" :disabled="busy" @click="lockMembers"><LockKeyhole :size="18" />锁定成员名单</button>
    </div>
    <div v-if="isOwner && room.status === 'READY_TO_PLAN'" class="planning-action">
      <button class="primary-button" type="button" :disabled="busy" @click="startPlan"><LoaderCircle v-if="busy" class="spin" :size="18" /><Sparkles v-else :size="18" />开始规划</button>
    </div>

    <div v-if="clarification && clarification.status === 'PENDING' && clarification.targetUserId === currentUserId" class="clarification-box">
      <strong>需要你的确认</strong><p>{{ clarification.question }}</p>
      <div><button class="primary-button" type="button" :disabled="busy" @click="answer('RELAX_TO_SOFT')">可协商</button><button class="text-button" type="button" :disabled="busy" @click="answer('CANCEL_PLAN')">取消规划</button></div>
    </div>

    <div v-if="proposals.length" class="proposal-list">
      <article v-for="proposal in proposals" :key="proposal.proposalId || proposal.id" class="proposal-card">
        <div class="proposal-card__top"><span>{{ proposal.recommended ? '推荐方案' : `备选 ${proposal.rank}` }}</span><MapPin :size="17" /></div>
        <h3>{{ proposal.shopName || '候选餐厅' }}</h3>
        <p class="proposal-card__meta">{{ proposal.suggestedTime }}<template v-if="proposal.estimatedPerCapita"> · 人均约 {{ proposal.estimatedPerCapita }} 元</template></p>
        <p>{{ proposal.reasoning }}</p>
        <div class="tag-row"><span v-for="item in proposal.satisfiedPreferences" :key="item">{{ item }}</span></div>
        <button v-if="isOwner && room.status === 'PLANS_READY'" class="primary-button" type="button" :disabled="busy" @click="confirmProposal(proposal.proposalId || proposal.id)"><Check :size="17" />确认方案</button>
      </article>
    </div>

    <details v-if="events.length" class="trace-details">
      <summary>查看执行记录 <span>{{ events.length }}</span><ChevronRight :size="17" /></summary>
      <ol><li v-for="event in events" :key="event.sequence"><i></i><div><strong>{{ eventLabel(event.eventType) }}</strong><p>{{ event.summary }}</p></div></li></ol>
    </details>
  </section>
</template>

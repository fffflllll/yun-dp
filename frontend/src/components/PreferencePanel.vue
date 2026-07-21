<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, LoaderCircle, Sparkles } from '@lucide/vue'
import { meetmateApi } from '../api/meetmate'
import { useToast } from '../composables/useToast'
import { splitTags } from '../utils/format'
import type { PreferenceData } from '../types'

const props = defineProps<{ roomId: number; editable: boolean }>()
const emit = defineEmits<{ saved: [] }>()
const rawText = ref('')
const draft = ref<PreferenceData | null>(null)
const parsing = ref(false)
const saving = ref(false)
const warning = ref('')
const status = ref('')
const cuisineText = ref('')
const allergenText = ref('')
const avoidText = ref('')
const { show } = useToast()

const canParse = computed(() => props.editable && rawText.value.trim().length > 0 && !parsing.value)
const hardConstraints = [
  ['BUDGET_MAX', '预算'],
  ['MAX_DISTANCE', '距离'],
  ['ACCEPTS_SPICY', '辣度'],
  ['ALLERGENS', '过敏原'],
]

onMounted(loadPreference)

async function loadPreference() {
  try {
    const value = await meetmateApi.preference(props.roomId)
    if (!value) return
    rawText.value = value.rawText || ''
    status.value = value.status
    applyDraft(value.confirmed || value.draft || null)
  } catch (error) {
    show(error instanceof Error ? error.message : '偏好加载失败', 'error')
  }
}

function applyDraft(value: PreferenceData | null) {
  draft.value = value ? reactive({
    ...value,
    preferredCuisines: value.preferredCuisines || [],
    allergens: value.allergens || [],
    avoidFoods: value.avoidFoods || [],
    hardConstraintKeys: value.hardConstraintKeys || [],
    notes: value.notes || [],
  }) : null
  cuisineText.value = value?.preferredCuisines?.join('，') || ''
  allergenText.value = value?.allergens?.join('，') || ''
  avoidText.value = value?.avoidFoods?.join('，') || ''
}

async function parsePreference() {
  if (!canParse.value) return
  parsing.value = true
  try {
    const result = await meetmateApi.parsePreference(props.roomId, rawText.value.trim())
    applyDraft(result.preference)
    warning.value = result.warning || ''
    show(result.aiParsed ? '已提取偏好，请核对后确认' : '已生成可编辑草稿', 'success')
  } catch (error) {
    show(error instanceof Error ? error.message : '偏好提取失败', 'error')
  } finally {
    parsing.value = false
  }
}

async function confirmPreference() {
  if (!draft.value || !props.editable) return
  saving.value = true
  try {
    draft.value.preferredCuisines = splitTags(cuisineText.value)
    draft.value.allergens = splitTags(allergenText.value)
    draft.value.avoidFoods = splitTags(avoidText.value)
    await meetmateApi.confirmPreference(props.roomId, draft.value)
    status.value = 'CONFIRMED'
    show('你的偏好已确认，房间成员可以看到完成状态', 'success')
    emit('saved')
  } catch (error) {
    show(error instanceof Error ? error.message : '偏好保存失败', 'error')
  } finally {
    saving.value = false
  }
}

function toggleConstraint(key: string) {
  if (!draft.value) return
  const index = draft.value.hardConstraintKeys.indexOf(key)
  if (index >= 0) draft.value.hardConstraintKeys.splice(index, 1)
  else draft.value.hardConstraintKeys.push(key)
}
</script>

<template>
  <section class="panel preference-panel">
    <div class="panel-heading panel-heading--preference">
      <div><span class="panel-kicker"><Sparkles :size="15" />我的偏好</span><h2>告诉大家你想吃什么</h2></div>
      <span v-if="status === 'CONFIRMED'" class="completion-label"><Check :size="15" />已确认</span>
    </div>

    <div class="field">
      <label for="preference-text">用一句话描述</label>
      <div class="textarea-wrap">
        <textarea id="preference-text" v-model="rawText" :disabled="!editable" maxlength="1000" rows="4" placeholder="例如：周六晚上，人均 100 元以内，不吃辣，最好离地铁近"></textarea>
        <span class="field-counter">{{ rawText.length }}/1000</span>
      </div>
      <p v-if="!editable" class="inline-note">房间已经进入规划阶段，偏好暂时不能修改。</p>
    </div>
    <div class="preference-actions">
      <button class="primary-button" type="button" :disabled="!canParse" @click="parsePreference">
        <LoaderCircle v-if="parsing" class="spin" :size="18" /><Sparkles v-else :size="18" />{{ parsing ? '正在提取' : '提取偏好' }}
      </button>
      <span v-if="warning" class="inline-note">{{ warning }}</span>
    </div>

    <Transition name="expand">
      <div v-if="draft" class="preference-editor">
        <div class="preference-fields">
          <div class="field"><label for="budget">人均预算上限</label><div class="input-with-unit"><input id="budget" v-model.number="draft.budgetMax" type="number" min="1" max="10000" :disabled="!editable" /><span>元</span></div></div>
          <div class="field"><label for="distance">最大距离</label><div class="input-with-unit"><input id="distance" v-model.number="draft.maxDistanceMeters" type="number" min="100" max="50000" step="100" :disabled="!editable" /><span>米</span></div></div>
          <div class="field"><label for="preferred-time">偏好时间</label><input id="preferred-time" v-model="draft.preferredTime" :disabled="!editable" placeholder="周六 18:30" /></div>
          <fieldset class="field"><legend>接受辣味</legend><div class="segmented-control"><label><input v-model="draft.acceptsSpicy" type="radio" :value="true" :disabled="!editable" /><span>可以</span></label><label><input v-model="draft.acceptsSpicy" type="radio" :value="false" :disabled="!editable" /><span>不吃辣</span></label></div></fieldset>
          <div class="field field--wide"><label for="cuisines">偏好菜系</label><input id="cuisines" v-model="cuisineText" :disabled="!editable" placeholder="川菜，日料，火锅" /><span class="field-help">用逗号分隔多个选项</span></div>
          <div class="field"><label for="allergens">过敏原</label><input id="allergens" v-model="allergenText" :disabled="!editable" placeholder="花生，乳制品" /></div>
          <div class="field"><label for="avoid-foods">不想吃</label><input id="avoid-foods" v-model="avoidText" :disabled="!editable" placeholder="香菜，动物内脏" /></div>
        </div>
        <fieldset class="constraint-field"><legend>不可违反的限制</legend><div class="chip-options"><label v-for="option in hardConstraints" :key="option[0]"><input type="checkbox" :checked="draft.hardConstraintKeys.includes(option[0])" :disabled="!editable" @change="toggleConstraint(option[0])" /><span>{{ option[1] }}</span></label></div></fieldset>
        <button class="primary-button" type="button" :disabled="saving || !editable" @click="confirmPreference">
          <LoaderCircle v-if="saving" class="spin" :size="18" /><Check v-else :size="18" />{{ saving ? '正在保存' : '确认我的偏好' }}
        </button>
      </div>
    </Transition>
  </section>
</template>

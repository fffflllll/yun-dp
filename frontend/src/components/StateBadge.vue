<script setup lang="ts">
import { computed } from 'vue'
import { statusLabel } from '../utils/format'

const props = defineProps<{ status?: string }>()
const tone = computed(() => {
  if (props.status === 'FINALIZED' || props.status === 'READY_TO_PLAN' || props.status === 'PLANS_READY') return 'positive'
  if (props.status === 'FAILED') return 'negative'
  if (props.status === 'PLANNING' || props.status === 'WAITING_INPUT') return 'active'
  return 'neutral'
})
</script>

<template>
  <span class="state-badge" :class="`state-badge--${tone}`">
    <span class="state-badge__dot" aria-hidden="true"></span>{{ statusLabel(status) }}
  </span>
</template>

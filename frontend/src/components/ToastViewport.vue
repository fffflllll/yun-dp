<script setup lang="ts">
import { CircleCheck, CircleX, Info, X } from '@lucide/vue'
import { useToast } from '../composables/useToast'

const { items, dismiss } = useToast()
</script>

<template>
  <div class="toast-viewport" aria-live="polite" aria-atomic="true">
    <TransitionGroup name="toast">
      <div v-for="item in items" :key="item.id" class="toast" :class="`toast--${item.tone}`">
        <CircleCheck v-if="item.tone === 'success'" :size="19" />
        <CircleX v-else-if="item.tone === 'error'" :size="19" />
        <Info v-else :size="19" />
        <span>{{ item.message }}</span>
        <button class="icon-button icon-button--small" type="button" aria-label="关闭提示" @click="dismiss(item.id)">
          <X :size="16" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

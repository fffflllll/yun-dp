<script setup lang="ts">
import { ArrowLeft, Utensils } from '@lucide/vue'

withDefaults(defineProps<{ title?: string; back?: boolean }>(), { title: 'MeetMate', back: false })

function goBack() {
  if (history.length > 1) history.back()
  else window.location.assign('/meetmate.html')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <button v-if="back" type="button" class="icon-button" aria-label="返回" @click="goBack">
        <ArrowLeft :size="21" />
      </button>
      <a v-else class="brand-mark" href="/meetmate.html" aria-label="MeetMate 聚会大厅">
        <Utensils :size="20" />
      </a>
      <div class="app-header__title">{{ title }}</div>
      <div class="app-header__actions"><slot /></div>
    </div>
  </header>
</template>

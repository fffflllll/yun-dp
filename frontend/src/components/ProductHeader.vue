<script setup lang="ts">
import { ArrowLeft, Search, Sparkles } from '@lucide/vue'

withDefaults(defineProps<{ title?: string; back?: boolean; compact?: boolean }>(), {
  title: '发现好去处', back: false, compact: false,
})

function goBack() {
  if (history.length > 1) history.back()
  else window.location.assign('/index.html')
}
</script>

<template>
  <header class="product-header" :class="{ 'product-header--compact': compact }">
    <div class="product-header__inner">
      <button v-if="back" class="circle-action" type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="20" /></button>
      <a v-else class="product-brand" href="/index.html"><Sparkles :size="18" /><span>MeetMate</span></a>
      <h1 v-if="title" class="product-header__title">{{ title }}</h1>
      <slot name="action"><a class="circle-action" href="/shop-list.html?type=1&name=美食" aria-label="搜索"><Search :size="19" /></a></slot>
    </div>
  </header>
</template>

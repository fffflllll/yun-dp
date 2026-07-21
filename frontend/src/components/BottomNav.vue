<script setup lang="ts">
import { Compass, Plus, UserRound, UsersRound } from '@lucide/vue'

defineProps<{ active?: 'home' | 'meet' | 'profile' }>()
</script>

<template>
  <nav class="bottom-nav" aria-label="主导航">
    <a :class="{ active: active === 'home' }" href="/index.html"><Compass :size="20" /><span>发现</span></a>
    <a class="bottom-nav__create" href="/blog-edit.html" aria-label="发布笔记"><Plus :size="21" /></a>
    <a :class="{ active: active === 'meet' }" href="/meetmate.html"><UsersRound :size="20" /><span>聚会</span></a>
    <a :class="{ active: active === 'profile' }" href="/info.html"><UserRound :size="20" /><span>我的</span></a>
  </nav>
</template>

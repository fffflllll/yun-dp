<script setup lang="ts">
import { Heart } from '@lucide/vue'
import type { Blog } from '../api/discovery'

const props = defineProps<{ blog: Blog }>()
const emit = defineEmits<{ like: [blog: Blog] }>()

function cover(blog: Blog) { return blog.images?.split(',')[0] || '/imgs/blogs/blog1.jpg' }
</script>

<template>
  <article class="discovery-card">
    <a :href="`/blog-detail.html?id=${blog.id}`" class="discovery-card__cover"><img :src="cover(blog)" :alt="blog.title || '探店笔记'" /></a>
    <div class="discovery-card__body">
      <a :href="`/blog-detail.html?id=${blog.id}`" class="discovery-card__title">{{ blog.title || '发现一家好店' }}</a>
      <footer>
        <a :href="`/other-info.html?id=${blog.userId}`" class="author"><img :src="blog.icon || '/imgs/icons/default-icon.png'" alt="" /><span>{{ blog.name || 'MeetMate 用户' }}</span></a>
        <button class="like-button" :class="{ liked: blog.isLike }" type="button" @click.prevent="emit('like', blog)"><Heart :size="15" :fill="blog.isLike ? 'currentColor' : 'none'" />{{ blog.liked || 0 }}</button>
      </footer>
    </div>
  </article>
</template>

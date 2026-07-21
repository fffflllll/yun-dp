<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Heart, MapPin, MessageCircle, Star } from '@lucide/vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Blog, type Shop, type User } from '../api/discovery'
import { useToast } from '../composables/useToast'

const id = Number(new URLSearchParams(location.search).get('id'))
const blog = ref<Blog | null>(null)
const shop = ref<Shop | null>(null)
const likes = ref<User[]>([])
const followed = ref(false)
const me = ref<User | null>(null)
const { show } = useToast()
const images = computed(() => blog.value?.images?.split(',').filter(Boolean) || [])

onMounted(async () => {
  try {
    blog.value = await discoveryApi.blog(id)
    const tasks: Promise<unknown>[] = [discoveryApi.likes(id).then(value => { likes.value = value })]
    if (blog.value.shopId) tasks.push(discoveryApi.shop(blog.value.shopId).then(value => { shop.value = value }))
    if (sessionStorage.getItem('token')) tasks.push(discoveryApi.me().then(value => { me.value = value }).catch(() => null))
    await Promise.all(tasks)
    if (blog.value?.userId && me.value?.id !== blog.value.userId) followed.value = await discoveryApi.isFollowing(blog.value.userId)
  } catch (error) { show(error instanceof Error ? error.message : '笔记加载失败', 'error') }
})
async function like() {
  if (!blog.value) return
  try { await discoveryApi.likeBlog(blog.value.id); blog.value = await discoveryApi.blog(blog.value.id); likes.value = await discoveryApi.likes(blog.value.id) }
  catch (error) { show(error instanceof Error ? error.message : '点赞失败', 'error') }
}
async function follow() {
  if (!blog.value) return
  if (!sessionStorage.getItem('token')) { window.location.assign('/login.html'); return }
  try { await discoveryApi.follow(blog.value.userId, !followed.value); followed.value = !followed.value }
  catch (error) { show(error instanceof Error ? error.message : '操作失败', 'error') }
}
</script>

<template>
  <div class="product-shell">
    <ProductHeader title="探店笔记" back />
    <main v-if="blog" class="product-page article-page">
      <section class="article-author"><a :href="me?.id === blog.userId ? '/info.html' : `/other-info.html?id=${blog.userId}`"><img :src="blog.icon || '/imgs/icons/default-icon.png'" alt="" /><div><strong>{{ blog.name || 'MeetMate 用户' }}</strong><span>{{ blog.createTime ? new Date(blog.createTime).toLocaleDateString('zh-CN') : '刚刚' }}</span></div></a><button v-if="me?.id !== blog.userId" class="follow-button" :class="{ following: followed }" type="button" @click="follow">{{ followed ? '已关注' : '关注' }}</button></section>
      <h1>{{ blog.title || '值得专程前往的一家店' }}</h1>
      <section v-if="images.length" class="article-images"><img v-for="image in images" :key="image" :src="image" :alt="blog.title" /></section>
      <p class="article-content">{{ blog.content || '这位用户还没有留下更多文字。' }}</p>
      <a v-if="shop" class="article-shop" :href="`/shop-detail.html?id=${shop.id}`"><img :src="shop.images?.split(',')[0] || '/imgs/blogs/blog1.jpg'" alt="" /><div><p class="eyebrow">THIS NOTE IS ABOUT</p><h2>{{ shop.name }}</h2><p><Star :size="13" fill="currentColor" />{{ ((shop.score || 0) / 10).toFixed(1) }} · ¥{{ shop.avgPrice || '—' }}/人</p></div><MapPin :size="19" /></a>
      <section class="article-likes"><button class="like-button" :class="{ liked: blog.isLike }" type="button" @click="like"><Heart :size="19" :fill="blog.isLike ? 'currentColor' : 'none'" />{{ blog.liked || 0 }} 人觉得不错</button><div class="avatar-row"><img v-for="user in likes.slice(0, 7)" :key="user.id" :src="user.icon || '/imgs/icons/default-icon.png'" :alt="user.nickName || ''" /></div></section>
      <section class="comment-placeholder"><MessageCircle :size="20" /><div><h2>评论功能即将上线</h2><p>先把这家店收藏到你的下一次聚会里吧。</p></div></section>
    </main>
  </div>
</template>

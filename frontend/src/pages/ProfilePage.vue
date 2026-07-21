<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { FilePenLine, Heart, LogOut, Plus, Users } from '@lucide/vue'
import BlogCard from '../components/BlogCard.vue'
import BottomNav from '../components/BottomNav.vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Blog, type User, type UserInfo } from '../api/discovery'
import { useToast } from '../composables/useToast'

const user = ref<User | null>(null)
const info = ref<UserInfo | null>(null)
const mine = ref<Blog[]>([])
const following = ref<Blog[]>([])
const tab = ref<'mine' | 'following'>('mine')
const { show } = useToast()
const shown = computed(() => tab.value === 'mine' ? mine.value : following.value)

onMounted(async () => {
  if (!sessionStorage.getItem('token')) { window.location.assign('/login.html'); return }
  try {
    user.value = await discoveryApi.me()
    const [details, blogs] = await Promise.all([discoveryApi.userInfo(user.value.id), discoveryApi.myBlogs()])
    info.value = details; mine.value = blogs
  } catch (error) { show(error instanceof Error ? error.message : '资料加载失败', 'error') }
})
async function changeTab(next: 'mine' | 'following') {
  tab.value = next
  if (next === 'following' && !following.value.length) {
    try { following.value = (await discoveryApi.followBlogs()).list || [] }
    catch (error) { show(error instanceof Error ? error.message : '关注动态加载失败', 'error') }
  }
}
async function like(blog: Blog) {
  try { await discoveryApi.likeBlog(blog.id); Object.assign(blog, await discoveryApi.blog(blog.id)) }
  catch (error) { show(error instanceof Error ? error.message : '点赞失败', 'error') }
}
async function logout() {
  try { await discoveryApi.logout() } catch { /* Session cleanup must still proceed. */ }
  sessionStorage.removeItem('token'); window.location.assign('/index.html')
}
</script>

<template>
  <div class="product-shell">
    <ProductHeader title="我的主页"><template #action><button class="circle-action" type="button" aria-label="退出登录" @click="logout"><LogOut :size="18" /></button></template></ProductHeader>
    <main v-if="user" class="product-page product-page--with-nav profile-page">
      <section class="profile-hero"><img :src="user.icon || '/imgs/icons/default-icon.png'" alt="" /><div><p class="eyebrow">PERSONAL SPACE</p><h1>{{ user.nickName || 'MeetMate 用户' }}</h1><p>{{ info?.introduce || '把喜欢的地方，慢慢收集起来。' }}</p></div><a class="circle-action" href="/info-edit.html" aria-label="编辑资料"><FilePenLine :size="18" /></a></section>
      <div class="profile-stats"><span><strong>{{ mine.length }}</strong>笔记</span><span><strong>—</strong>关注</span><span><strong>—</strong>粉丝</span></div>
      <div class="profile-tabs"><button :class="{ active: tab === 'mine' }" type="button" @click="changeTab('mine')"><Heart :size="16" />我的笔记</button><button :class="{ active: tab === 'following' }" type="button" @click="changeTab('following')"><Users :size="16" />关注动态</button></div>
      <div v-if="shown.length" class="discovery-grid"><BlogCard v-for="blog in shown" :key="blog.id" :blog="blog" @like="like" /></div>
      <div v-else class="empty-profile"><p>这里还没有内容。</p><a class="primary-button" href="/blog-edit.html"><Plus :size="17" />发布第一篇笔记</a></div>
    </main>
    <BottomNav active="profile" />
  </div>
</template>

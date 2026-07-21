<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Heart, UserPlus, Users } from '@lucide/vue'
import BlogCard from '../components/BlogCard.vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Blog, type User, type UserInfo } from '../api/discovery'
import { useToast } from '../composables/useToast'

const id = Number(new URLSearchParams(location.search).get('id'))
const user = ref<User | null>(null)
const info = ref<UserInfo | null>(null)
const blogs = ref<Blog[]>([])
const common = ref<User[]>([])
const followed = ref(false)
const tab = ref<'notes' | 'common'>('notes')
const { show } = useToast()
const display = computed(() => tab.value === 'notes')

onMounted(async () => {
  try {
    user.value = await discoveryApi.user(id)
    if (!user.value) throw new Error('该用户不存在')
    ;[info.value, blogs.value] = await Promise.all([discoveryApi.userInfo(id), discoveryApi.userBlogs(id)])
    if (sessionStorage.getItem('token')) followed.value = await discoveryApi.isFollowing(id)
  } catch (error) { show(error instanceof Error ? error.message : '主页加载失败', 'error') }
})
async function toggleFollow() {
  if (!sessionStorage.getItem('token')) { window.location.assign('/login.html'); return }
  try { await discoveryApi.follow(id, !followed.value); followed.value = !followed.value }
  catch (error) { show(error instanceof Error ? error.message : '操作失败', 'error') }
}
async function selectTab(value: 'notes' | 'common') {
  tab.value = value
  if (value === 'common' && !common.value.length) {
    try { common.value = await discoveryApi.commonFollows(id) }
    catch (error) { show(error instanceof Error ? error.message : '共同关注加载失败', 'error') }
  }
}
async function like(blog: Blog) {
  try { await discoveryApi.likeBlog(blog.id); Object.assign(blog, await discoveryApi.blog(blog.id)) }
  catch (error) { show(error instanceof Error ? error.message : '点赞失败', 'error') }
}
</script>

<template>
  <div class="product-shell">
    <ProductHeader title="用户主页" back />
    <main v-if="user" class="product-page profile-page">
      <section class="profile-hero"><img :src="user.icon || '/imgs/icons/default-icon.png'" alt="" /><div><p class="eyebrow">MEETMATE MEMBER</p><h1>{{ user.nickName || 'MeetMate 用户' }}</h1><p>{{ info?.introduce || '这个人还没有留下个人介绍。' }}</p></div><button class="follow-button" :class="{ following: followed }" type="button" @click="toggleFollow"><UserPlus :size="16" />{{ followed ? '已关注' : '关注' }}</button></section>
      <div class="profile-tabs"><button :class="{ active: tab === 'notes' }" type="button" @click="selectTab('notes')"><Heart :size="16" />笔记</button><button :class="{ active: tab === 'common' }" type="button" @click="selectTab('common')"><Users :size="16" />共同关注</button></div>
      <div v-if="display && blogs.length" class="discovery-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" @like="like" /></div>
      <div v-else-if="display" class="empty-profile">还没有公开笔记。</div>
      <section v-else class="common-list"><a v-for="member in common" :key="member.id" :href="`/other-info.html?id=${member.id}`"><img :src="member.icon || '/imgs/icons/default-icon.png'" alt="" /><span>{{ member.nickName || 'MeetMate 用户' }}</span><small>查看主页</small></a><p v-if="!common.length" class="empty-profile">暂未发现共同关注。</p></section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, MapPin, Search } from '@lucide/vue'
import BottomNav from '../components/BottomNav.vue'
import BlogCard from '../components/BlogCard.vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Blog, type ShopType } from '../api/discovery'
import { useToast } from '../composables/useToast'

const types = ref<ShopType[]>([])
const blogs = ref<Blog[]>([])
const loading = ref(true)
const page = ref(1)
const { show } = useToast()

onMounted(async () => {
  try {
    const [typeResult, blogResult] = await Promise.all([discoveryApi.types(), discoveryApi.hotBlogs()])
    types.value = typeResult
    blogs.value = blogResult
  } catch (error) { show(error instanceof Error ? error.message : '内容加载失败', 'error') }
  finally { loading.value = false }
})

async function loadMore() {
  try { blogs.value.push(...await discoveryApi.hotBlogs(++page.value)) }
  catch (error) { page.value--; show(error instanceof Error ? error.message : '加载失败', 'error') }
}
async function like(blog: Blog) {
  try { await discoveryApi.likeBlog(blog.id); Object.assign(blog, await discoveryApi.blog(blog.id)) }
  catch (error) { show(error instanceof Error ? error.message : '点赞失败', 'error') }
}
</script>

<template>
  <div class="product-shell">
    <ProductHeader title="">
      <template #action><a class="header-search" href="/shop-list.html?type=1&name=美食"><Search :size="18" /><span>搜店铺、地点</span></a></template>
    </ProductHeader>
    <main class="product-page product-page--with-nav">
      <section class="discover-hero">
        <p class="eyebrow">HANGZHOU · TODAY</p>
        <h1>今天，去哪里吃？</h1>
        <p><MapPin :size="16" /> 杭州 · 发现附近值得专程前往的店</p>
      </section>
      <section class="category-rail" aria-label="店铺分类">
        <a v-for="item in types" :key="item.id" :href="`/shop-list.html?type=${item.id}&name=${encodeURIComponent(item.name)}`">
          <span class="category-rail__icon"><img :src="`/imgs/${item.icon}`" :alt="item.name" /></span><span>{{ item.name }}</span>
        </a>
      </section>
      <section class="feed-section">
        <div class="section-title"><div><p class="eyebrow">EDITOR'S PICKS</p><h2>正在发生的好味道</h2></div><a href="/meetmate.html">去聚会 <ArrowRight :size="16" /></a></div>
        <div v-if="loading" class="content-state">正在挑选今日灵感…</div>
        <div v-else class="discovery-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" @like="like" /></div>
        <button v-if="blogs.length" class="quiet-load" type="button" @click="loadMore">加载更多推荐</button>
      </section>
    </main>
    <BottomNav active="home" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ImagePlus, LoaderCircle, Search, Send, X } from '@lucide/vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Shop } from '../api/discovery'
import { useToast } from '../composables/useToast'

const title = ref('')
const content = ref('')
const shopName = ref('')
const shops = ref<Shop[]>([])
const shop = ref<Shop | null>(null)
const images = ref<string[]>([])
const busy = ref(false)
const { show } = useToast()
const canSubmit = computed(() => title.value.trim() && content.value.trim() && shop.value && images.value.length)

async function search() {
  if (!shopName.value.trim()) return
  try { shops.value = await discoveryApi.shopsByName(shopName.value.trim()) }
  catch (error) { show(error instanceof Error ? error.message : '店铺搜索失败', 'error') }
}
async function upload(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || []).slice(0, 6 - images.value.length)
  if (!files.length) return
  busy.value = true
  try { images.value.push(...await Promise.all(files.map(file => discoveryApi.uploadImage(file)))) }
  catch (error) { show(error instanceof Error ? error.message : '图片上传失败', 'error') }
  finally { busy.value = false }
}
async function remove(image: string) {
  images.value = images.value.filter(item => item !== image)
  try { await discoveryApi.deleteImage(image) } catch { /* image cleanup is best effort */ }
}
async function submit() {
  if (!canSubmit.value || !shop.value) { show('请完善标题、内容、店铺和图片', 'info'); return }
  busy.value = true
  try { await discoveryApi.createBlog({ title: title.value.trim(), content: content.value.trim(), images: images.value.join(','), shopId: shop.value.id }); show('发布成功', 'success'); window.location.assign('/info.html') }
  catch (error) { show(error instanceof Error ? error.message : '发布失败', 'error') }
  finally { busy.value = false }
}
</script>

<template>
  <div class="product-shell">
    <ProductHeader title="发布笔记" back />
    <main class="product-page editor-page">
      <section class="editor-intro"><p class="eyebrow">SHARE A GOOD FIND</p><h1>把这次心动，写下来。</h1><p>一张好照片，一段真实感受，会帮助更多人找到心仪的地方。</p></section>
      <form class="editor-form" @submit.prevent="submit">
        <label>笔记标题<input v-model="title" maxlength="100" placeholder="给这次发现起一个标题" /></label>
        <label>正文<textarea v-model="content" maxlength="1000" rows="7" placeholder="这家店有什么让你印象深刻的地方？"></textarea><small>{{ content.length }}/1000</small></label>
        <label>关联店铺<div class="search-field"><Search :size="18" /><input v-model="shopName" placeholder="输入店铺名查找" @keydown.enter.prevent="search" /><button type="button" @click="search">搜索</button></div></label>
        <div v-if="shops.length" class="shop-suggestions"><button v-for="candidate in shops" :key="candidate.id" type="button" :class="{ selected: shop?.id === candidate.id }" @click="shop = candidate">{{ candidate.name }}<span>{{ candidate.area || '杭州' }}</span></button></div>
        <div class="upload-heading"><label>图片</label><span>最多 6 张</span></div>
        <div class="image-upload-grid"><figure v-for="image in images" :key="image"><img :src="image" alt="已上传图片" /><button type="button" aria-label="移除图片" @click="remove(image)"><X :size="15" /></button></figure><label v-if="images.length < 6" class="image-upload"><ImagePlus :size="25" /><span>添加图片</span><input type="file" accept="image/*" multiple @change="upload" /></label></div>
        <button class="primary-button primary-button--full" :disabled="busy || !canSubmit" type="submit"><LoaderCircle v-if="busy" class="spin" :size="18" /><Send v-else :size="18" />{{ busy ? '处理中' : '发布笔记' }}</button>
      </form>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, MapPin, Star } from '@lucide/vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Shop, type ShopType } from '../api/discovery'
import { useToast } from '../composables/useToast'

const params = new URLSearchParams(location.search)
const typeId = ref(Number(params.get('type')) || 1)
const typeName = ref(params.get('name') || '好店推荐')
const types = ref<ShopType[]>([])
const shops = ref<Shop[]>([])
const loading = ref(true)
const page = ref(1)
const { show } = useToast()
const sort = ref('距离')
const activeType = computed(() => types.value.find(item => item.id === typeId.value))

onMounted(async () => {
  try {
    types.value = await discoveryApi.types()
    typeName.value = activeType.value?.name || typeName.value
    await load(true)
  } catch (error) { show(error instanceof Error ? error.message : '店铺加载失败', 'error') }
  finally { loading.value = false }
})
async function load(reset = false) {
  if (reset) { page.value = 1; shops.value = [] }
  const list = await discoveryApi.shops({ typeId: typeId.value, current: page.value, x: 120.149993, y: 30.334229 })
  shops.value.push(...list)
}
async function changeType(item: ShopType) {
  typeId.value = item.id; typeName.value = item.name; await load(true)
}
async function order(label: string) {
  sort.value = label
  // Current backend accepts the historic distance ordering; retain the same API behavior.
  await load(true)
}
function image(shop: Shop) { return shop.images?.split(',')[0] || shop.image || '/imgs/blogs/blog1.jpg' }
function distance(value?: number) { return !value ? '附近' : value < 1000 ? `${value.toFixed(0)} m` : `${(value / 1000).toFixed(1)} km` }
</script>

<template>
  <div class="product-shell">
    <ProductHeader :title="typeName" back />
    <main class="product-page listing-page">
      <section class="filter-row">
        <label class="select-chip"><span>{{ typeName }}</span><ChevronDown :size="15" /><select v-model.number="typeId" @change="changeType(activeType!)"><option v-for="item in types" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <button v-for="item in ['距离', '人气', '评分']" :key="item" :class="{ active: sort === item }" type="button" @click="order(item)">{{ item }}</button>
      </section>
      <div v-if="loading" class="content-state">正在寻找附近好店…</div>
      <section v-else class="shop-list">
        <a v-for="shop in shops" :key="shop.id" class="shop-card" :href="`/shop-detail.html?id=${shop.id}`">
          <img :src="image(shop)" :alt="shop.name" />
          <div class="shop-card__content"><div><h2>{{ shop.name }}</h2><p class="rating"><Star :size="14" fill="currentColor" />{{ ((shop.score || 0) / 10).toFixed(1) }} <span>{{ shop.comments || 0 }} 条评价</span></p></div><p class="shop-card__meta"><span>{{ shop.area || '杭州' }}</span><span>{{ distance(shop.distance) }}</span></p><p class="shop-card__price">¥{{ shop.avgPrice || '—' }} / 人</p><p class="shop-card__address"><MapPin :size="14" />{{ shop.address || '地址待补充' }}</p></div>
        </a>
        <button class="quiet-load" type="button" @click="page++; load()">继续浏览</button>
      </section>
    </main>
  </div>
</template>

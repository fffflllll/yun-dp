<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Clock3, MapPin, Navigation, Star, Ticket, TriangleAlert } from '@lucide/vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type Shop, type Voucher } from '../api/discovery'
import { useToast } from '../composables/useToast'

const id = Number(new URLSearchParams(location.search).get('id'))
const shop = ref<Shop | null>(null)
const vouchers = ref<Voucher[]>([])
const { show } = useToast()
const images = computed(() => shop.value?.images?.split(',').filter(Boolean) || [])
const activeVouchers = computed(() => vouchers.value.filter(item => !item.endTime || new Date(item.endTime).getTime() > Date.now()))

onMounted(async () => {
  if (!id) { show('店铺链接无效', 'error'); return }
  try { [shop.value, vouchers.value] = await Promise.all([discoveryApi.shop(id), discoveryApi.vouchers(id)]) }
  catch (error) { show(error instanceof Error ? error.message : '店铺加载失败', 'error') }
})
function price(value: number) { return (value / 100).toFixed(2) }
function available(voucher: Voucher) { return !voucher.beginTime || new Date(voucher.beginTime).getTime() <= Date.now() }
async function buy(voucher: Voucher) {
  if (!sessionStorage.getItem('token')) { window.location.assign('/login.html'); return }
  if (!available(voucher)) { show('活动尚未开始', 'info'); return }
  if ((voucher.stock ?? 1) < 1) { show('来晚了，券已抢完', 'info'); return }
  try { show(`抢购成功，订单号：${await discoveryApi.buyVoucher(voucher.id)}`, 'success') }
  catch (error) { show(error instanceof Error ? error.message : '抢购失败', 'error') }
}
</script>

<template>
  <div class="product-shell">
    <ProductHeader :title="shop?.name || '店铺详情'" back />
    <main v-if="shop" class="product-page detail-page">
      <section class="shop-hero"><div><p class="eyebrow">RESTAURANT GUIDE</p><h1>{{ shop.name }}</h1><p class="rating"><Star :size="16" fill="currentColor" />{{ ((shop.score || 0) / 10).toFixed(1) }} <span>{{ shop.comments || 0 }} 条真实评价</span></p></div><span class="rank-pill">本地精选</span></section>
      <section v-if="images.length" class="image-gallery"><img v-for="image in images.slice(0, 3)" :key="image" :src="image" :alt="shop.name" /></section>
      <section class="detail-card detail-card--location"><MapPin :size="21" /><div><h2>{{ shop.address || '地址待补充' }}</h2><p>{{ shop.area || '杭州' }} · 距你不远</p></div><Navigation :size="20" /></section>
      <section class="detail-card"><Clock3 :size="21" /><div><p class="eyebrow">营业时间</p><h2>{{ shop.openHours || '以门店实际营业时间为准' }}</h2></div></section>
      <section class="offer-section"><div class="section-title"><div><p class="eyebrow">EXCLUSIVE OFFERS</p><h2>到店可用优惠</h2></div><Ticket :size="21" /></div><div v-if="!activeVouchers.length" class="empty-inline">暂时没有可领取的优惠券</div><article v-for="voucher in activeVouchers" :key="voucher.id" class="offer-card"><div class="offer-card__value"><strong>¥{{ price(voucher.payValue) }}</strong><span>价值 ¥{{ price(voucher.actualValue) }}</span></div><div class="offer-card__content"><h3>{{ voucher.title }}</h3><p>{{ voucher.subTitle || '到店使用，详见购买须知' }}</p><small v-if="voucher.stock !== undefined">剩余 {{ voucher.stock }} 张</small></div><button class="secondary-button" :disabled="!available(voucher) || (voucher.stock ?? 1) < 1" type="button" @click="buy(voucher)">{{ available(voucher) ? '抢购' : '未开始' }}</button></article></section>
      <section class="detail-note"><TriangleAlert :size="16" />页面展示的评价和标签仅供参考，到店前建议联系商家确认。</section>
    </main>
    <main v-else class="product-page content-state">正在打开店铺…</main>
  </div>
</template>

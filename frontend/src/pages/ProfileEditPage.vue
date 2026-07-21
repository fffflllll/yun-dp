<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { CircleHelp } from '@lucide/vue'
import ProductHeader from '../components/ProductHeader.vue'
import { discoveryApi, type User, type UserInfo } from '../api/discovery'
import { useToast } from '../composables/useToast'

const user = ref<User | null>(null)
const info = ref<UserInfo | null>(null)
const { show } = useToast()
onMounted(async () => {
  try { user.value = await discoveryApi.me(); info.value = await discoveryApi.userInfo(user.value.id) }
  catch (error) { show(error instanceof Error ? error.message : '资料加载失败', 'error') }
})
</script>

<template>
  <div class="product-shell">
    <ProductHeader title="个人资料" back />
    <main v-if="user" class="product-page settings-page"><section class="settings-profile"><img :src="user.icon || '/imgs/icons/default-icon.png'" alt="" /><div><strong>{{ user.nickName || 'MeetMate 用户' }}</strong><span>当前账号</span></div></section><section class="settings-list"><div><span>昵称</span><strong>{{ user.nickName || '未设置' }}</strong></div><div><span>个人介绍</span><strong>{{ info?.introduce || '介绍一下自己' }}</strong></div><div><span>性别</span><strong>{{ info?.gender || '未设置' }}</strong></div><div><span>城市</span><strong>{{ info?.city || '未设置' }}</strong></div><div><span>生日</span><strong>{{ info?.birthday || '未设置' }}</strong></div></section><p class="settings-help"><CircleHelp :size="15" />当前后端暂未提供资料编辑接口，以上信息为只读展示。</p></main>
  </div>
</template>

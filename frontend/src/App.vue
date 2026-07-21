<script setup lang="ts">
import { computed } from 'vue'
import ToastViewport from './components/ToastViewport.vue'
import LobbyPage from './pages/LobbyPage.vue'
import CreateRoomPage from './pages/CreateRoomPage.vue'
import RoomPage from './pages/RoomPage.vue'
import HomePage from './pages/HomePage.vue'
import ShopListPage from './pages/ShopListPage.vue'
import ShopDetailPage from './pages/ShopDetailPage.vue'
import BlogDetailPage from './pages/BlogDetailPage.vue'
import BlogEditPage from './pages/BlogEditPage.vue'
import LoginPage from './pages/LoginPage.vue'
import ProfilePage from './pages/ProfilePage.vue'
import OtherProfilePage from './pages/OtherProfilePage.vue'
import ProfileEditPage from './pages/ProfileEditPage.vue'

const page = computed(() => {
  if (location.pathname.endsWith('/login.html') || location.pathname.endsWith('/login2.html')) return LoginPage
  if (location.pathname.endsWith('/shop-list.html')) return ShopListPage
  if (location.pathname.endsWith('/shop-detail.html')) return ShopDetailPage
  if (location.pathname.endsWith('/blog-detail.html')) return BlogDetailPage
  if (location.pathname.endsWith('/blog-edit.html')) return BlogEditPage
  if (location.pathname.endsWith('/info-edit.html')) return ProfileEditPage
  if (location.pathname.endsWith('/other-info.html')) return OtherProfilePage
  if (location.pathname.endsWith('/info.html')) return ProfilePage
  if (location.pathname.endsWith('/meetmate-create.html')) return CreateRoomPage
  if (location.pathname.endsWith('/meetmate-room.html')) return RoomPage
  if (location.pathname.endsWith('/meetmate.html')) return LobbyPage
  return HomePage
})
</script>

<template>
  <component :is="page" />
  <ToastViewport />
</template>

import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: {
        lobby: fileURLToPath(new URL('meetmate.html', import.meta.url)),
        create: fileURLToPath(new URL('meetmate-create.html', import.meta.url)),
        room: fileURLToPath(new URL('meetmate-room.html', import.meta.url)),
        home: fileURLToPath(new URL('index.html', import.meta.url)),
        login: fileURLToPath(new URL('login.html', import.meta.url)),
        passwordLogin: fileURLToPath(new URL('login2.html', import.meta.url)),
        shops: fileURLToPath(new URL('shop-list.html', import.meta.url)),
        shop: fileURLToPath(new URL('shop-detail.html', import.meta.url)),
        blog: fileURLToPath(new URL('blog-detail.html', import.meta.url)),
        editor: fileURLToPath(new URL('blog-edit.html', import.meta.url)),
        profile: fileURLToPath(new URL('info.html', import.meta.url)),
        profileEdit: fileURLToPath(new URL('info-edit.html', import.meta.url)),
        otherProfile: fileURLToPath(new URL('other-info.html', import.meta.url)),
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})

<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { X } from '@lucide/vue'

defineProps<{ open: boolean; title: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = useTemplateRef<HTMLDivElement>('dialog')

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="modal-backdrop" @mousedown.self="emit('close')">
        <div ref="dialog" class="modal-sheet" role="dialog" aria-modal="true" :aria-label="title">
          <div class="modal-sheet__header">
            <h2>{{ title }}</h2>
            <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')"><X :size="20" /></button>
          </div>
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

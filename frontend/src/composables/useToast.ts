import { reactive } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'
export interface ToastItem { id: number; message: string; tone: ToastTone }

const items = reactive<ToastItem[]>([])
let nextId = 1

function show(message: string, tone: ToastTone = 'info') {
  const item = { id: nextId++, message, tone }
  items.push(item)
  window.setTimeout(() => dismiss(item.id), 3600)
}

function dismiss(id: number) {
  const index = items.findIndex((item) => item.id === id)
  if (index >= 0) items.splice(index, 1)
}

export function useToast() {
  return { items, show, dismiss }
}

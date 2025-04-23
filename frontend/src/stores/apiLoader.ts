import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useApiLoader = defineStore('apiLoader', () => {
  const isLoading = ref(false)
  const loadingMessage = ref('')
  
  const startLoading = (message: string = '加载中...') => {
    isLoading.value = true
    loadingMessage.value = message
  }
  
  const finishLoading = () => {
    isLoading.value = false
    loadingMessage.value = ''
  }
  
  return {
    isLoading,
    loadingMessage,
    startLoading,
    finishLoading
  }
}) 
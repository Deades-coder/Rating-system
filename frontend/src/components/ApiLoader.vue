<script setup lang="ts">
import { useApiLoader } from '@/stores/apiLoader';

// 从store获取loading状态
const apiLoader = useApiLoader();
</script>

<template>
  <div v-if="apiLoader.isLoading" class="fixed inset-0 z-50 flex items-center justify-center bg-black/10 backdrop-blur-sm">
    <div class="bg-white rounded-lg shadow-lg p-6 flex flex-col items-center space-y-4">
      <div class="loader"></div>
      <p class="text-gray-600">{{ apiLoader.loadingMessage || '加载中...' }}</p>
    </div>
  </div>
</template>

<style scoped>
.loader {
  border: 4px solid #f3f3f3;
  border-top: 4px solid #4F46E5;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style> 
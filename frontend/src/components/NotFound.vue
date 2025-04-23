<script setup lang="ts">
import { useRouter } from 'vue-router';
import NavBar from './NavBar.vue';

const router = useRouter();

const goToHome = () => {
  router.push('/');
};
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <NavBar />
    
    <div class="flex-1 flex flex-col items-center justify-center px-4 py-12">
      <div class="text-9xl font-bold text-gray-200">404</div>
      <h1 class="mt-4 text-2xl font-semibold text-gray-800">页面未找到</h1>
      <p class="mt-2 text-gray-600">您访问的页面不存在或已被删除</p>
      
      <button 
        @click="goToHome"
        class="mt-8 px-6 py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition duration-300 flex items-center"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
        返回首页
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 如果需要可以添加额外样式 */
</style> 
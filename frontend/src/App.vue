<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useUserStore } from './stores/user';
import ApiLoader from './components/ApiLoader.vue';
import { BlogApi } from './api/blog';

const userStore = useUserStore();
const backendStatus = ref('正在检测后端连接...');
const initializationComplete = ref(false);

onMounted(async () => {
  console.log('App组件挂载，开始初始化...');
  
  // 首先测试API连接
  try {
    console.log('测试后端API连接...');
    await BlogApi.testConnection();
    backendStatus.value = '后端连接正常';
    console.log('后端API连接成功');
  } catch (error) {
    console.error('后端API连接失败:', error);
    backendStatus.value = '无法连接到后端服务';
  }
  
  // 然后检查当前用户状态
  try {
    console.log('检查当前用户状态...');
    await userStore.getCurrentUser();
    console.log('用户状态检查完成，已登录:', userStore.isAuthenticated);
  } catch (error) {
    console.error('获取用户状态失败:', error);
  }
  
  initializationComplete.value = true;
  console.log('应用初始化完成');
});
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <!-- 全局API加载器 -->
    <ApiLoader />
    
    <!-- 初始化状态提示 -->
    <div v-if="!initializationComplete" class="fixed inset-0 flex items-center justify-center bg-black/80 z-50 text-white">
      <div class="text-center">
        <div class="loader mx-auto mb-4"></div>
        <p class="text-xl">{{ backendStatus }}</p>
      </div>
    </div>
    
    <!-- 主内容区 -->
    <main class="flex-1">
      <router-view />
    </main>
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
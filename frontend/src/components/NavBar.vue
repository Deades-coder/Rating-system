<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import LoginBadge from './LoginBadge.vue';

const router = useRouter();
const isMobileMenuOpen = ref(false);

const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value;
};

const goToHome = () => {
  router.push('/');
  isMobileMenuOpen.value = false;
};
</script>

<template>
  <nav class="bg-white shadow-sm sticky top-0 z-40">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex justify-between h-16">
        <!-- 左侧 Logo 和导航 -->
        <div class="flex">
          <div class="flex-shrink-0 flex items-center">
            <h1 
              @click="goToHome"
              class="text-xl font-bold text-primary cursor-pointer"
            >
              博客点赞系统
            </h1>
          </div>
        </div>
        
        <!-- 右侧用户信息区域 -->
        <div class="flex items-center">
          <LoginBadge />
        </div>
        
        <!-- 移动菜单按钮 -->
        <div class="flex items-center sm:hidden">
          <button 
            @click="toggleMobileMenu" 
            class="inline-flex items-center justify-center p-2 rounded-md text-gray-400 hover:text-gray-500 hover:bg-gray-100 focus:outline-none"
          >
            <svg class="h-6 w-6" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path 
                v-if="!isMobileMenuOpen" 
                stroke-linecap="round" 
                stroke-linejoin="round" 
                stroke-width="2" 
                d="M4 6h16M4 12h16M4 18h16" 
              />
              <path 
                v-else 
                stroke-linecap="round" 
                stroke-linejoin="round" 
                stroke-width="2" 
                d="M6 18L18 6M6 6l12 12" 
              />
            </svg>
          </button>
        </div>
      </div>
    </div>
    
    <!-- 移动菜单面板 -->
    <div 
      v-if="isMobileMenuOpen" 
      class="sm:hidden"
    >
      <div class="pt-2 pb-3 space-y-1">
        <button 
          @click="goToHome"
          class="block px-3 py-2 rounded-md text-base font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-50 w-full text-left"
        >
          首页
        </button>
      </div>
    </div>
  </nav>
</template> 
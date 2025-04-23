<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import type { BlogVO } from '@/types';
import { formatDateTime, truncateText } from '@/utils';
import ThumbButton from './ThumbButton.vue';

const props = defineProps<{
  blog: BlogVO;
}>();

const router = useRouter();

const formattedDate = computed(() => {
  return formatDateTime(props.blog.createTime);
});

const summary = computed(() => {
  return truncateText(props.blog.content, 100);
});

const navigateToBlog = () => {
  console.log('正在跳转到博客详情页，博客ID:', props.blog.id);
  // 确保ID是数字
  const id = Number(props.blog.id);
  if (isNaN(id)) {
    console.error('博客ID无效:', props.blog.id);
    return;
  }
  
  // 直接使用原始路由导航
  router.push({
    name: 'BlogDetail',
    params: { id }
  }).catch(err => {
    console.error('导航失败:', err);
    // 尝试备用导航方式
    window.location.href = `/blog/${id}`;
  });
};

// 直接跳转到详情页的通用方法
const goToBlogDetail = () => {
  console.log('点击了博客卡片，准备跳转');
  navigateToBlog();
};
</script>

<template>
  <div class="card group relative" @click="goToBlogDetail">
    <!-- 覆盖整个卡片的可点击区域 -->
    <div class="absolute inset-0 z-10 cursor-pointer" @click="goToBlogDetail"></div>
    
    <!-- 封面图 -->
    <div class="relative aspect-video overflow-hidden">
      <img 
        :src="blog.coverImg || 'https://via.placeholder.com/600x400?text=No+Image'" 
        :alt="blog.title" 
        class="card-img h-full w-full object-cover group-hover:scale-105 transition-transform duration-300"
      >
      <!-- 作者信息悬浮层 -->
      <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-4">
        <span class="text-white text-sm font-medium">作者ID: {{ blog.userId }}</span>
      </div>
    </div>
    
    <!-- 内容部分 -->
    <div class="p-4">
      <h3 class="text-lg font-semibold mb-2 group-hover:text-primary transition-colors duration-200">
        {{ blog.title }}
      </h3>
      <p class="text-gray-600 text-sm mb-4">{{ summary }}</p>
      
      <!-- 卡片底部 -->
      <div class="flex items-center justify-between mt-2 text-sm text-gray-500">
        <!-- 左侧日期信息 -->
        <span class="flex-shrink-0">{{ formattedDate }}</span>
        
        <!-- 右侧按钮区域 -->
        <div class="flex items-center">
          <!-- 查看详情按钮 -->
          <div class="opacity-0 group-hover:opacity-100 transition-opacity duration-200 mr-3">
            <button 
              class="bg-blue-600 text-white px-3 py-1 rounded-md text-sm hover:bg-blue-700 transition"
              @click.stop="goToBlogDetail"
            >
              查看详情
            </button>
          </div>
          
          <!-- 点赞按钮，阻止点击事件冒泡 -->
          <div class="z-20 relative" @click.stop>
            <ThumbButton 
              :blog-id="blog.id" 
              :has-thumb="blog.hasThumb" 
              :thumb-count="blog.thumbCount"
              size="small"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  @apply bg-white rounded-lg shadow-md overflow-hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.card:hover {
  @apply shadow-lg;
  transform: translateY(-2px);
}

.text-primary {
  @apply text-blue-600;
}
</style> 
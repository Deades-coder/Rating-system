<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBlogStore } from '@/stores/blog';
import { formatDateTime } from '@/utils';
import NavBar from '@/components/NavBar.vue';
import ThumbButton from '@/components/ThumbButton.vue';

const route = useRoute();
const router = useRouter();
const blogStore = useBlogStore();

// 博客ID
const blogId = computed(() => Number(route.params.id));

// 加载状态
const isLoading = ref(false);

// 格式化日期
const formattedDate = computed(() => {
  if (!blogStore.currentBlog) return '';
  return formatDateTime(blogStore.currentBlog.createTime);
});

// 获取博客详情
const fetchBlogDetail = async () => {
  if (!blogId.value) {
    console.error('未能获取有效的博客ID，重定向到首页');
    router.push('/');
    return;
  }
  
  isLoading.value = true;
  try {
    console.log(`开始获取博客详情，ID: ${blogId.value}`);
    const blog = await blogStore.fetchBlogDetail(blogId.value);
    
    if (!blog) {
      console.error(`未找到ID为${blogId.value}的博客`);
      setTimeout(() => {
        if (!blogStore.currentBlog) {
          router.push('/');
        }
      }, 2000);
    }
  } catch (error) {
    console.error('获取博客详情失败', error);
    // 延迟一下再返回，让用户看到错误信息
    setTimeout(() => {
      router.push('/');
    }, 2000);
  } finally {
    isLoading.value = false;
  }
};

// 返回列表
const goBack = () => {
  router.push('/');
};

// 挂载时获取数据
onMounted(() => {
  fetchBlogDetail();
});
</script>

<template>
  <div>
    <NavBar />
    
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 返回按钮 -->
      <button 
        @click="goBack" 
        class="mb-6 flex items-center text-gray-600 hover:text-primary"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
        返回列表
      </button>
      
      <!-- 加载中状态 -->
      <div v-if="isLoading" class="flex justify-center py-12">
        <div class="loader"></div>
      </div>
      
      <!-- 博客详情 -->
      <article v-else-if="blogStore.currentBlog" class="bg-white rounded-lg shadow-lg overflow-hidden">
        <!-- 封面图 -->
        <div v-if="blogStore.currentBlog.coverImg" class="aspect-video w-full">
          <img 
            :src="blogStore.currentBlog.coverImg" 
            :alt="blogStore.currentBlog.title" 
            class="w-full h-full object-cover"
          >
        </div>
        
        <!-- 内容区域 -->
        <div class="p-6">
          <!-- 标题和元信息 -->
          <header class="mb-6">
            <h1 class="text-3xl font-bold text-gray-900 mb-4">{{ blogStore.currentBlog.title }}</h1>
            
            <div class="flex items-center justify-between text-gray-500 text-sm">
              <div class="flex items-center space-x-4">
                <!-- 作者信息 -->
                <div class="flex items-center">
                  <span>作者ID: {{ blogStore.currentBlog.userId }}</span>
                </div>
                
                <!-- 发布时间 -->
                <span>{{ formattedDate }}</span>
              </div>
              
              <!-- 点赞按钮 -->
              <ThumbButton 
                :blog-id="blogStore.currentBlog.id" 
                :has-thumb="blogStore.currentBlog.hasThumb" 
                :thumb-count="blogStore.currentBlog.thumbCount"
                size="medium"
              />
            </div>
          </header>
          
          <!-- 博客内容 -->
          <div class="prose prose-primary mx-auto">
            <p class="whitespace-pre-line">{{ blogStore.currentBlog.content }}</p>
          </div>
        </div>
      </article>
      
      <!-- 未找到博客 -->
      <div v-else class="text-center py-12 text-gray-500">
        未找到该博客或已被删除
      </div>
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

.text-primary {
  color: #4F46E5;
}

.prose {
  max-width: 100%;
  color: #374151;
  line-height: 1.6;
}

.prose p {
  margin: 1.2em 0;
}
</style> 
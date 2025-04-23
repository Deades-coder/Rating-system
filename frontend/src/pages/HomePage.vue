<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RecycleScroller } from 'vue-virtual-scroller';
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css';
import { useBlogStore } from '@/stores/blog';
import BlogCard from '@/components/BlogCard.vue';
import NavBar from '@/components/NavBar.vue';

const blogStore = useBlogStore();

// 加载状态
const isLoading = ref(false);

// 初始化数据
const fetchData = async () => {
  isLoading.value = true;
  try {
    await blogStore.fetchBlogs();
  } catch (error) {
    console.error('获取博客列表失败', error);
  } finally {
    isLoading.value = false;
  }
};

// 挂载时获取数据
onMounted(() => {
  fetchData();
});
</script>

<template>
  <div>
    <NavBar />
    
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <header class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900">博客列表</h1>
        <p class="mt-2 text-gray-600">发现有趣的文章，表达你的喜欢</p>
      </header>
      
      <!-- 加载中状态 -->
      <div v-if="isLoading" class="flex justify-center py-12">
        <div class="loader"></div>
      </div>
      
      <!-- 内容列表 -->
      <div v-else>
        <!-- 使用虚拟滚动列表 -->
        <RecycleScroller
          class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 min-h-screen"
          :items="blogStore.sortedBlogs"
          :item-size="300"
          key-field="id"
        >
          <template #default="{ item }">
            <BlogCard :blog="item" />
          </template>
        </RecycleScroller>
        
        <!-- 无数据提示 -->
        <div v-if="blogStore.blogs.length === 0" class="text-center py-12 text-gray-500">
          暂无博客数据
        </div>
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
</style> 
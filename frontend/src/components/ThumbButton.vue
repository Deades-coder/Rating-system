<script setup lang="ts">
import { computed } from 'vue';
import { ThumbApi } from '@/api/thumb';
import { useBlogStore } from '@/stores/blog';
import { useUserStore } from '@/stores/user';
import { debounce } from '@/utils';
import { formatNumber } from '@/utils';

// 接收博客信息
const props = defineProps<{
  blogId: number;
  hasThumb: boolean;
  thumbCount: number;
  size?: 'small' | 'medium' | 'large';
}>();

const blogStore = useBlogStore();
const userStore = useUserStore();

// 点赞按钮尺寸
const buttonSize = computed(() => {
  switch (props.size) {
    case 'small': return 'text-sm';
    case 'large': return 'text-xl';
    default: return 'text-base';
  }
});

// 点赞数量格式化
const formattedThumbCount = computed(() => {
  // 确保thumbCount为有效数字，否则默认为0
  const thumbCount = props.thumbCount !== undefined && props.thumbCount !== null ? props.thumbCount : 0;
  console.log(`格式化点赞数：博客ID=${props.blogId}, 点赞数=${thumbCount}, 是否点赞=${props.hasThumb}`);
  return formatNumber(thumbCount);
});

// 防抖点赞处理函数
const handleThumb = debounce(async () => {
  // 未登录时提示登录
  if (!userStore.isAuthenticated) {
    alert('请先登录');
    return;
  }
  
  try {
    if (props.hasThumb) {
      // 取消点赞
      const response = await ThumbApi.undoThumb(props.blogId);
      if (response.code === 0) {
        blogStore.updateBlogThumbStatus(props.blogId, false);
      }
    } else {
      // 点赞
      const response = await ThumbApi.doThumb(props.blogId);
      if (response.code === 0) {
        blogStore.updateBlogThumbStatus(props.blogId, true);
      }
    }
  } catch (error) {
    console.error('点赞/取消点赞失败', error);
  }
}, 300);
</script>

<template>
  <button
    @click="handleThumb"
    :disabled="!userStore.isAuthenticated"
    :class="[
      'transition-all duration-300 flex items-center space-x-1 focus:outline-none',
      buttonSize,
      props.hasThumb 
        ? 'text-danger hover:text-danger-dark' 
        : 'text-gray-500 hover:text-gray-700',
      !userStore.isAuthenticated && 'opacity-50 cursor-not-allowed'
    ]"
  >
    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 transition-transform duration-300" :class="{'scale-125': props.hasThumb}" fill="none" viewBox="0 0 24 24" stroke="currentColor" :stroke-width="props.hasThumb ? 2 : 1.5">
      <path stroke-linecap="round" stroke-linejoin="round" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" :fill="props.hasThumb ? 'currentColor' : 'none'" />
    </svg>
    
    <span :class="{'font-medium': props.hasThumb}">
      <transition name="count">
        <span :key="props.thumbCount">{{ formattedThumbCount }}</span>
      </transition>
    </span>
  </button>
</template>

<style scoped>
.count-enter-active, .count-leave-active {
  transition: all 0.3s ease;
}

.count-enter-from, .count-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.text-danger {
  color: #e53e3e;
}

.text-danger-dark {
  color: #c53030;
}
</style> 
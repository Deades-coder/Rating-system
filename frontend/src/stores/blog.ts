import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { BlogVO } from '@/types';
import { BlogApi } from '@/api/blog';

export const useBlogStore = defineStore('blog', () => {
  // 状态
  const blogs = ref<BlogVO[]>([]);
  const currentBlog = ref<BlogVO | null>(null);
  const isLoading = ref<boolean>(false);
  
  // 计算属性
  const sortedBlogs = computed(() => {
    return [...blogs.value].sort((a, b) => 
      new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
    );
  });
  
  // 最近访问的博客ID列表
  const recentViewedIds = ref<number[]>([]);
  
  // Actions
  async function fetchBlogs() {
    isLoading.value = true;
    try {
      console.log('开始获取博客列表...');
      const response = await BlogApi.getBlogList();
      console.log('获取博客列表响应:', response);
      
      if (response && response.code === 0) {
        blogs.value = response.data || [];
        console.log('更新后的博客列表:', blogs.value);
        return blogs.value;
      } else {
        console.error('获取博客列表失败, 响应码非0:', response?.code, response?.message);
        throw new Error(response?.message || '获取博客列表失败');
      }
    } catch (error) {
      console.error('获取博客列表失败, 捕获到异常:', error);
      return [];
    } finally {
      isLoading.value = false;
    }
  }
  
  async function fetchBlogDetail(blogId: number) {
    isLoading.value = true;
    try {
      console.log(`开始获取博客详情，ID: ${blogId}，类型: ${typeof blogId}`);
      const numId = Number(blogId);
      if (isNaN(numId)) {
        console.error('博客ID无效:', blogId);
        throw new Error('博客ID必须是数字');
      }
      
      console.log(`发送API请求获取博客, ID: ${numId}`);
      const response = await BlogApi.getBlogDetail(numId);
      console.log('博客详情API响应:', response);
      
      if (response.code === 0 && response.data) {
        currentBlog.value = response.data;
        console.log('获取到博客详情:', currentBlog.value);
        
        // 更新最近访问记录
        addToRecentViewed(numId);
        
        return currentBlog.value;
      } else {
        console.error('获取博客详情失败，响应码非0或无数据:', response.code, response.message);
        throw new Error(response.message || '获取博客详情失败');
      }
    } catch (error) {
      console.error(`获取博客${blogId}详情失败, 捕获到异常:`, error);
      currentBlog.value = null;
      return null;
    } finally {
      isLoading.value = false;
    }
  }
  
  // 更新博客点赞状态
  function updateBlogThumbStatus(blogId: number, hasThumb: boolean) {
    // 更新列表中的博客
    const blog = blogs.value.find(b => b.id === blogId);
    if (blog) {
      const oldThumbCount = blog.thumbCount;
      blog.hasThumb = hasThumb;
      blog.thumbCount = hasThumb 
        ? blog.thumbCount + 1 
        : Math.max(0, blog.thumbCount - 1);
      console.log(`更新博客列表中点赞状态：ID=${blogId}, 旧点赞数=${oldThumbCount}, 新点赞数=${blog.thumbCount}, 点赞状态=${hasThumb}`);
    } else {
      console.warn(`未找到要更新点赞状态的博客：ID=${blogId}`);
    }
    
    // 更新当前查看的博客
    if (currentBlog.value && currentBlog.value.id === blogId) {
      const oldThumbCount = currentBlog.value.thumbCount;
      currentBlog.value.hasThumb = hasThumb;
      currentBlog.value.thumbCount = hasThumb 
        ? currentBlog.value.thumbCount + 1 
        : Math.max(0, currentBlog.value.thumbCount - 1);
      console.log(`更新当前博客点赞状态：ID=${blogId}, 旧点赞数=${oldThumbCount}, 新点赞数=${currentBlog.value.thumbCount}, 点赞状态=${hasThumb}`);
    }
  }
  
  // 添加到最近访问
  function addToRecentViewed(blogId: number) {
    // 移除已存在的相同ID
    recentViewedIds.value = recentViewedIds.value.filter(id => id !== blogId);
    // 添加到开头
    recentViewedIds.value.unshift(blogId);
    // 只保留最近10条记录
    if (recentViewedIds.value.length > 10) {
      recentViewedIds.value = recentViewedIds.value.slice(0, 10);
    }
    // 保存到本地存储
    localStorage.setItem('recent-viewed-blogs', JSON.stringify(recentViewedIds.value));
  }
  
  // 从本地存储加载最近访问记录
  function loadRecentViewed() {
    const saved = localStorage.getItem('recent-viewed-blogs');
    if (saved) {
      try {
        recentViewedIds.value = JSON.parse(saved);
      } catch (e) {
        console.error('解析最近访问记录失败', e);
      }
    }
  }
  
  // 初始化调用
  loadRecentViewed();
  
  return {
    blogs,
    currentBlog,
    isLoading,
    sortedBlogs,
    recentViewedIds,
    fetchBlogs,
    fetchBlogDetail,
    updateBlogThumbStatus
  };
}); 
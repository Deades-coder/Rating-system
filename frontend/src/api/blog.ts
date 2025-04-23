import api from './index';
import type { BaseResponse, BlogVO } from '@/types';

export const BlogApi = {
  /**
   * 获取博客列表
   */
  getBlogList(): Promise<BaseResponse<BlogVO[]>> {
    return api.get('/blog/list').then(response => {
      // 检查返回的博客数据结构
      if (response.data && Array.isArray(response.data)) {
        console.log('博客列表数据示例:', response.data[0]);
        console.log('所有博客的点赞数:', response.data.map(blog => ({
          id: blog.id,
          thumbCount: blog.thumbCount
        })));
      }
      return response;
    });
  },
  
  /**
   * 获取博客详情
   * @param blogId 博客ID
   */
  getBlogDetail(blogId: number): Promise<BaseResponse<BlogVO>> {
    console.log(`调用getBlogDetail，blogId=${blogId}，类型：${typeof blogId}`);
    // 直接使用blogId作为参数而不嵌套在params中
    return api.get('/blog/get', { blogId }).then(response => {
      // 检查博客详情数据
      if (response.data) {
        console.log('博客详情数据:', {
          id: response.data.id,
          title: response.data.title,
          thumbCount: response.data.thumbCount,
          hasThumb: response.data.hasThumb
        });
      }
      return response;
    });
  },
  
  /**
   * 测试API连接
   */
  testConnection(): Promise<BaseResponse<string>> {
    console.log('测试后端连接...');
    return api.get('/ping');
  }
}; 
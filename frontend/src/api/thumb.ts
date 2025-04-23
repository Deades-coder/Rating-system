import api from './index';
import type { BaseResponse, DoThumbRequest } from '@/types';

export const ThumbApi = {
  /**
   * 点赞博客
   * @param blogId 博客ID
   */
  doThumb(blogId: number): Promise<BaseResponse<boolean>> {
    const params: DoThumbRequest = { blogId };
    return api.post('/thumb/do', params);
  },
  
  /**
   * 取消点赞
   * @param blogId 博客ID
   */
  undoThumb(blogId: number): Promise<BaseResponse<boolean>> {
    const params: DoThumbRequest = { blogId };
    return api.post('/thumb/undo', params);
  }
}; 
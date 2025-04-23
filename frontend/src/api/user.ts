import api from './index';
import type { BaseResponse, User } from '@/types';

export const UserApi = {
  /**
   * 用户登录（使用用户ID）
   * @param userId 用户ID
   */
  login(userId: number): Promise<BaseResponse<User>> {
    console.log(`调用UserApi.login, userId=${userId}, 类型: ${typeof userId}`);
    return api.get('/user/login', { userId });
  },
  
  /**
   * 获取当前登录用户信息
   */
  getCurrentUser(): Promise<BaseResponse<User>> {
    return api.get('/user/get/login');
  }
}; 
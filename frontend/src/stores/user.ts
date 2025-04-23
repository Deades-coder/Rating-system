import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { User } from '@/types';
import { UserApi } from '@/api/user';

export const useUserStore = defineStore('user', () => {
  const currentUser = ref<User | null>(null);
  const isAuthenticated = ref(false);
  const loading = ref(false);  // 添加loading状态

  /**
   * 设置loading状态
   */
  const setLoading = (value: boolean) => {
    loading.value = value;
  };

  /**
   * 获取当前登录用户信息
   */
  const getCurrentUser = async () => {
    try {
      console.log('开始获取当前登录用户信息...');
      loading.value = true;
      const response = await UserApi.getCurrentUser();
      console.log('获取当前登录用户响应:', response);
      
      if (response?.code === 0 && response?.data) {
        console.log('成功获取到用户信息:', response.data);
        currentUser.value = response.data;
        isAuthenticated.value = true;
      } else {
        console.log('响应成功但未获取到用户信息 code:', response?.code, 'message:', response?.message);
        // 如果请求成功但没有用户信息，清除本地状态
        currentUser.value = null;
        isAuthenticated.value = false;
      }
      return response;
    } catch (error) {
      // 处理异常情况
      console.error('获取当前用户异常:', error);
      currentUser.value = null;
      isAuthenticated.value = false;
      throw error;
    } finally {
      loading.value = false;
    }
  };

  // 为了兼容性，添加别名
  const fetchCurrentUser = getCurrentUser;

  /**
   * 用户登录（使用ID）
   */
  const login = async (userId: number) => {
    try {
      console.log(`开始登录过程，用户ID: ${userId}，类型: ${typeof userId}`);
      // 确保userId是数字类型
      const id = Number(userId);
      if (isNaN(id)) {
        throw new Error('用户ID必须是数字');
      }
      
      loading.value = true;
      console.log(`发送登录请求，ID: ${id}`);
      
      // 使用修复后的API调用
      const response = await UserApi.login(id);
      console.log('登录响应:', response);
      
      if (response?.code === 0 && response?.data) {
        console.log('登录成功，用户信息:', response.data);
        currentUser.value = response.data;
        isAuthenticated.value = true;
        return response;
      } else {
        console.error('登录失败，响应码非0或无数据:', response?.code, response?.message);
        throw new Error(response?.message || '登录失败');
      }
    } catch (error: any) {
      // 重置用户状态
      console.error('登录过程捕获异常:', error.message || error);
      currentUser.value = null;
      isAuthenticated.value = false;
      throw error;
    } finally {
      loading.value = false;
    }
  };

  /**
   * 用户登出
   */
  const logout = () => {
    console.log('用户登出，清除状态');
    // 直接清除前端状态
    currentUser.value = null;
    isAuthenticated.value = false;
  };

  // 为了支持登录框
  const setShowLoginModal = (value: boolean) => {
    // 这里未实际实现，仅为API兼容性添加
    console.log('登录框显示状态:', value);
  };

  return {
    currentUser,
    isAuthenticated,
    loading,
    setLoading,
    getCurrentUser,
    fetchCurrentUser,
    login,
    logout,
    setShowLoginModal
  };
}, {
  persist: {
    key: 'user-store',
    storage: localStorage,
    paths: ['currentUser', 'isAuthenticated']
  }
}); 
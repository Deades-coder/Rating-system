<script setup lang="ts">
import { ref, computed } from 'vue';
import { useUserStore } from '@/stores/user';
import { UserApi } from '@/api/user';
import api from '@/api/index';

const userStore = useUserStore();

const showModal = ref(false);
const userId = ref('');
const errorMessage = ref('');
const isLoading = ref(false);

const hasUser = computed(() => !!userStore.currentUser);

// 显示登录模态框
const openLoginModal = () => {
  showModal.value = true;
  errorMessage.value = '';
  userId.value = '';
};

// 隐藏登录模态框
const closeLoginModal = () => {
  showModal.value = false;
};

// 直接测试API请求
const testDirectRequest = async () => {
  if (!userId.value) {
    errorMessage.value = '用户ID不能为空';
    return;
  }
  
  const id = parseInt(userId.value);
  if (isNaN(id)) {
    errorMessage.value = '请输入有效的用户ID（数字）';
    return;
  }
  
  isLoading.value = true;
  errorMessage.value = '';
  
  try {
    console.log('直接测试API请求 - 开始');
    // 使用正确的参数传递方式
    const response = await api.get('/user/login', { userId: id });
    console.log('直接API请求结果:', response);
    alert('API请求成功，查看控制台获取详情');
  } catch (error) {
    console.error('直接API请求错误:', error);
    errorMessage.value = '直接API请求失败，查看控制台获取详情';
  } finally {
    isLoading.value = false;
  }
};

// 添加使用Fetch API直接发送请求的测试方法
const testFetchRequest = async () => {
  if (!userId.value) {
    errorMessage.value = '用户ID不能为空';
    return;
  }
  
  const id = parseInt(userId.value);
  if (isNaN(id)) {
    errorMessage.value = '请输入有效的用户ID（数字）';
    return;
  }
  
  isLoading.value = true;
  errorMessage.value = '';
  
  try {
    console.log('使用Fetch API测试请求 - 开始');
    const response = await fetch(`/api/user/login?userId=${id}`, {
      method: 'GET',
      credentials: 'include'
    });
    const data = await response.json();
    console.log('Fetch API请求结果:', data);
    alert('Fetch API请求成功，查看控制台获取详情');
  } catch (error) {
    console.error('Fetch API请求错误:', error);
    errorMessage.value = 'Fetch API请求失败，查看控制台获取详情';
  } finally {
    isLoading.value = false;
  }
};

// 处理登录请求
const handleLogin = async () => {
  if (!userId.value) {
    errorMessage.value = '用户ID不能为空';
    return;
  }
  
  // 确保ID是数字
  const id = parseInt(userId.value);
  if (isNaN(id)) {
    errorMessage.value = '请输入有效的用户ID（数字）';
    return;
  }
  
  console.log(`正在尝试登录，用户ID: ${id}`);
  errorMessage.value = '';
  isLoading.value = true;
  
  try {
    await userStore.login(id);
    console.log('登录成功，当前用户:', userStore.currentUser);
    closeLoginModal();
  } catch (error: any) {
    console.error('登录组件捕获到错误:', error);
    errorMessage.value = error.message || '登录失败，请重试';
  } finally {
    isLoading.value = false;
  }
};

// 处理登出请求
const handleLogout = () => {
  userStore.logout();
};
</script>

<template>
  <div class="login-badge">
    <!-- 已登录状态 -->
    <div v-if="hasUser" class="user-info">
      <span class="username">{{ userStore.currentUser?.username }}</span>
      <button 
        class="logout-btn" 
        @click="handleLogout"
      >
        登出
      </button>
    </div>
    
    <!-- 未登录状态 -->
    <button 
      v-else 
      class="login-btn" 
      @click="openLoginModal"
    >
      登录
    </button>
    
    <!-- 登录模态框 -->
    <div v-if="showModal" class="modal-overlay" @click="closeLoginModal">
      <div class="modal-content" @click.stop>
        <h2>用户登录</h2>
        
        <div class="form-group">
          <label for="userId">用户ID</label>
          <input 
            id="userId" 
            v-model="userId" 
            type="number" 
            placeholder="请输入用户ID"
          />
        </div>
        
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        
        <div class="modal-actions">
          <button 
            class="cancel-btn" 
            @click="closeLoginModal" 
            :disabled="isLoading"
          >
            取消
          </button>
          <button 
            class="submit-btn" 
            @click="handleLogin" 
            :disabled="isLoading"
          >
            {{ isLoading ? '登录中...' : '登录' }}
          </button>
          <button
            class="test-btn"
            @click="testDirectRequest"
            :disabled="isLoading"
          >
            测试API
          </button>
          <button
            class="test-fetch-btn"
            @click="testFetchRequest"
            :disabled="isLoading"
          >
            测试Fetch API
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-badge {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  font-size: 14px;
  font-weight: 500;
}

.login-btn, .logout-btn {
  padding: 6px 12px;
  border-radius: 4px;
  border: none;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.login-btn {
  background-color: #4CAF50;
  color: white;
}

.login-btn:hover {
  background-color: #45a049;
}

.logout-btn {
  background-color: #f44336;
  color: white;
}

.logout-btn:hover {
  background-color: #d32f2f;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 8px;
  padding: 24px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.modal-content h2 {
  margin-top: 0;
  color: #333;
  font-size: 20px;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #555;
}

.form-group input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.error-message {
  color: #f44336;
  font-size: 14px;
  margin-bottom: 16px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn, .submit-btn, .test-btn, .test-fetch-btn {
  padding: 8px 16px;
  border-radius: 4px;
  border: none;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn {
  background-color: #f5f5f5;
  color: #333;
}

.cancel-btn:hover {
  background-color: #e0e0e0;
}

.submit-btn {
  background-color: #4CAF50;
  color: white;
}

.submit-btn:hover {
  background-color: #45a049;
}

.test-btn {
  background-color: #2196F3;
  color: white;
}

.test-btn:hover {
  background-color: #0b7dda;
}

.test-fetch-btn {
  background-color: #2196F3;
  color: white;
}

.test-fetch-btn:hover {
  background-color: #0b7dda;
}

.submit-btn:disabled, .cancel-btn:disabled, .test-btn:disabled, .test-fetch-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style> 
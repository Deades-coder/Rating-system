<script setup lang="ts">
import { useRouter } from 'vue-router';
import NavBar from '@/components/NavBar.vue';

const router = useRouter();

const goToHome = () => {
  router.push('/');
};
</script>

<template>
  <div class="not-found-page">
    <NavBar />
    <div class="not-found-container">
      <div class="not-found-content">
        <h1 class="error-code">404</h1>
        <h2 class="error-message">页面不存在</h2>
        <p class="error-description">您访问的页面不存在或已被删除</p>
        <button @click="goToHome" class="back-home-button">
          返回首页
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.not-found-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 2rem;
}

.not-found-content {
  text-align: center;
  max-width: 600px;
}

.error-code {
  font-size: 6rem;
  font-weight: 700;
  color: #374151;
  margin: 0;
}

.error-message {
  font-size: 2rem;
  color: #4b5563;
  margin: 1rem 0;
}

.error-description {
  font-size: 1.125rem;
  color: #6b7280;
  margin-bottom: 2rem;
}

.back-home-button {
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
  font-weight: 500;
  color: white;
  background-color: #3b82f6;
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

.back-home-button:hover {
  background-color: #2563eb;
}

@media (max-width: 640px) {
  .error-code {
    font-size: 4rem;
  }
  
  .error-message {
    font-size: 1.5rem;
  }
}
</style> 
import axios, { AxiosRequestConfig } from 'axios';
import { useApiLoader } from '@/stores/apiLoader';
import { BaseResponse } from '@/types';

// 创建axios实例
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true // 添加跨域请求时携带cookie
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 在这里控制loading状态
    const apiLoader = useApiLoader();
    apiLoader.startLoading();
    
    console.log(`发送${config.method?.toUpperCase()}请求: ${config.url}`, config.params || config.data);
    return config;
  },
  (error) => {
    console.error('请求拦截器捕获错误:', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // 响应后关闭loading状态
    const apiLoader = useApiLoader();
    apiLoader.finishLoading();
    
    // 确保返回BaseResponse类型的完整响应
    const res = response.data;
    console.log(`响应数据 [${response.config.url}]:`, res);
    
    // 可以在这里统一处理错误码，但让业务代码自己处理更灵活
    return res;
  },
  (error) => {
    // 响应出错也关闭loading状态
    const apiLoader = useApiLoader();
    apiLoader.finishLoading();
    
    // 处理HTTP错误
    if (error.response) {
      // 服务器返回了错误状态码
      console.error('HTTP错误:', error.response.status, error.response.data);
      return Promise.reject({
        message: `服务器错误 (${error.response.status}): ${error.response.data?.message || '未知错误'}`,
        status: error.response.status,
        data: error.response.data
      });
    } else if (error.request) {
      // 请求已发送但未收到响应
      console.error('网络错误: 未收到响应', error.request);
      return Promise.reject({
        message: '网络错误: 未能连接到服务器，请检查网络连接',
        request: error.request
      });
    } else {
      // 请求配置出错
      console.error('请求配置错误:', error.message);
      return Promise.reject({
        message: `请求错误: ${error.message}`
      });
    }
  }
);

// 封装请求方法
export const api = {
  get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<BaseResponse<T>> {
    // 确保params参数正确传递
    const options = config || {};
    if (params) {
      options.params = params;
    }
    return request.get(url, options);
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<BaseResponse<T>> {
    return request.post(url, data, config);
  }
};

export default api; 
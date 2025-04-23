// API响应基础类型
export interface BaseResponse<T = any> {
  code: number;
  data: T;
  message: string;
}

// 用户相关类型
export interface User {
  id: number;
  username: string;
}

// 登录请求类型
export interface LoginRequest {
  userAccount: string;
  userPassword: string;
}

// 点赞请求类型
export interface DoThumbRequest {
  blogId: number;
}

// 博客相关类型
export interface Blog {
  id: number;
  userId: number;
  title: string;
  coverImg?: string;
  content: string;
  thumbCount: number;
  createTime: string;
  updateTime: string;
}

export interface BlogVO extends Blog {
  username?: string;
  userAvatar?: string;
  hasThumb: boolean;
}

export interface BlogRequest {
  id?: number;
  title: string;
  content: string;
  coverImage?: string;
}

// 点赞相关类型
export interface Thumb {
  id: number;
  userId: number;
  blogId: number;
  createTime: string;
}

export interface ThumbRequest {
  blogId: number;
} 
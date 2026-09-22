import { create } from 'zustand';
import axios from '../api/axiosInstance';

const useAuthStore = create((set) => ({
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  loading: false,

  // 个人中心改了姓名后同步顶栏显示（token/role 不变）
  setUser: (patch) =>
    set((state) => {
      const user = { ...(state.user || {}), ...patch };
      localStorage.setItem('user', JSON.stringify(user));
      return { user };
    }),

  login: async (credentials) => {
    set({ loading: true });
    try {
      const { data } = await axios.post('/auth/login', credentials);
      const user = { name: data.name, role: data.role, token: data.token };

      // 存储 user 对象（原有逻辑）
      localStorage.setItem('user', JSON.stringify(user));
      // ★ 同时存储单独的 token，供请求拦截器使用
      localStorage.setItem('token', data.token);

      set({ user, loading: false });
    } catch (error) {
      set({ loading: false });
      throw error;
    }
  },

  logout: () => {
    localStorage.removeItem('user');
    localStorage.removeItem('token');   // ★ 清除 token
    set({ user: null });
  },
}));

export default useAuthStore;
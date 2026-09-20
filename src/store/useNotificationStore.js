import { create } from 'zustand';
import axios from '../api/axiosInstance';

const useNotificationStore = create((set, get) => ({
  notifications: [],
  stats: null,
  loading: false,
  pagination: { page: 0, size: 10, total: 0 },
  filters: {},

  fetchNotifications: async (page = 0, size = 10) => {
    set({ loading: true });
    try {
      const currentFilters = get().filters;
      const params = {};
      Object.keys(currentFilters).forEach(key => {
        if (currentFilters[key] !== undefined && currentFilters[key] !== null && currentFilters[key] !== '') {
          params[key] = currentFilters[key];
        }
      });
      params.page = page;
      params.size = size;
      const { data } = await axios.get('/notifications', { params });
      set({
        notifications: data.content || [],
        pagination: {
          page: data.number ?? 0,
          size: data.size ?? 10,
          total: data.totalElements ?? 0,
        },
        loading: false,
      });
    } catch (e) {
      set({ loading: false });
    }
  },

  // 仪表盘默认只看当前登录客户自己的通知；全行统计仅 ADMIN 可取（后端已鉴权，普通用户会拿到 403）
  fetchStats: async (scope = 'mine') => {
    try {
      const url = scope === 'all' ? '/notifications/stats/all' : '/notifications/stats';
      const { data } = await axios.get(url);
      set({ stats: data });
    } catch (e) {
      console.error(e);
    }
  },

  setFilters: (filters) => set({ filters }),

  latest: [],
  latestLoading: false,
  unreadCount: 0,

  fetchUnreadCount: async () => {
    try {
      const { data } = await axios.get('/notifications/unread-count');
      set({ unreadCount: data.unread ?? 0 });
    } catch (e) {
      console.error(e);
    }
  },

  // 下拉面板只列真正投递过的通知；SKIPPED / FAILED_VALIDATION 属于运维可见信息，留在历史页看。
  // mine=true 是必须的：管理员在列表端点上默认能看全行，否则铃铛会列出别人的通知，
  // 点已读时被归属校验拒绝成 403。
  fetchLatest: async () => {
    set({ latestLoading: true });
    try {
      const { data } = await axios.get('/notifications', {
        params: { page: 0, size: 5, statuses: 'PENDING,SENT,FAILED', mine: true },
      });
      set({ latest: data.content || [], latestLoading: false });
    } catch (e) {
      set({ latestLoading: false });
    }
    get().fetchUnreadCount();
  },

  markRead: async (id) => {
    await axios.patch(`/notifications/${id}/read`);
    set((s) => ({
      latest: s.latest.map((n) => (n.id === id ? { ...n, read: true } : n)),
      unreadCount: s.latest.some((n) => n.id === id && !n.read)
        ? Math.max(0, s.unreadCount - 1)
        : s.unreadCount,
    }));
  },

  markAllRead: async () => {
    await axios.patch('/notifications/read-all');
    set((s) => ({
      latest: s.latest.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    }));
  },
}));

export default useNotificationStore;
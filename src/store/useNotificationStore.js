import { create } from 'zustand';
import axios from '../api/axiosInstance';
import { isSoundEnabled, setSoundEnabled } from '../utils/notifySound';

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
  soundEnabled: isSoundEnabled(),

  toggleSound: () => {
    const next = !get().soundEnabled;
    setSoundEnabled(next);
    set({ soundEnabled: next });
    return next;
  },

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
  // silent=true 给轮询用：不置 latestLoading，否则面板每 15 秒闪一次骨架。
  // 返回值是这一页里最大的通知 id（请求失败返回 null），调用方拿它判断"有没有新消息"。
  fetchLatest: async ({ silent = false } = {}) => {
    if (!silent) set({ latestLoading: true });
    let content;
    try {
      const { data } = await axios.get('/notifications', {
        params: { page: 0, size: 5, statuses: 'PENDING,SENT,FAILED', mine: true },
      });
      content = data.content || [];
    } catch (e) {
      if (!silent) set({ latestLoading: false });
      return null;
    }
    set({ latest: content, latestLoading: false });
    get().fetchUnreadCount();
    return content.reduce((max, n) => Math.max(max, n.id ?? 0), 0);
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
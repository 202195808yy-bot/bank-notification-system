import { create } from 'zustand';
import axios from '../api/axiosInstance';
import { isSoundEnabled, setSoundEnabled } from '../utils/notifySound';

const useNotificationStore = create((set, get) => ({
  notifications: [],
  stats: null,
  loading: false,
  // 列表加载失败必须与"没有通知"区分开：此前 catch 只把 loading 置回 false，
  // 于是网关 502 / 令牌过期在界面上渲染成一屏"暂无数据"（PRD-54①）
  listError: false,
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
        listError: false,
      });
    } catch (e) {
      set({ loading: false, listError: true });
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
  // silent=true 给轮询/推送用：不置 latestLoading，否则面板每刷新一次闪一次骨架。
  //
  // ⚠️ 这是"有没有新消息"的唯一判定入口：15 秒轮询与 SSE 推送都走这里，
  // 所以响的那一帧和列表更新的那一帧必然是同一帧（此前两条路径各判各的，表现为"叮了但看不到"）。
  // 判定看的是**状态转入 SENT**，不是"这一行是不是新面孔"：
  //   · PENDING 本身不响 —— 那一刻消息还没发出去，响了就是假承诺；
  //   · 但一行先以 PENDING 被看见、之后变 SENT **必须响** —— 否则"客户点开过一次铃铛"
  //     就永久吃掉这条消息的提示音（EMAIL 的 PENDING 停留 p95 约 9 秒，而轮询 15 秒，是常态不是竞态）；
  //   · SKIPPED / FAILED_VALIDATION 不响 —— 被抑制的通知不该用铃声打扰客户。
  // 返回 true 表示这一批里有真送达的新消息；调用方据此决定响不响（一次事件多渠道只响一声）。
  bellSeen: null,

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
      return false;
    }
    set({ latest: content, latestLoading: false });
    get().fetchUnreadCount();
    let seen = get().bellSeen;
    if (!seen) {
      // 首帧只建立基线：刷新页面不能把历史消息全响一遍
      set({ bellSeen: new Map(content.map((n) => [n.id, n.status])) });
      return false;
    }
    let delivered = false;
    content.forEach((n) => {
      if (seen.get(n.id) === n.status) return;
      seen.set(n.id, n.status);
      if (n.status === 'SENT') delivered = true;
    });
    // 只留当前窗口里的行：本列表是最多 5 条"最新通知"，被挤出去的旧行不会再回来，
    // 而"回来时状态未知"只会让它再响一次，不是漏响。
    seen.forEach((_, id) => {
      if (!content.some((n) => n.id === id)) seen.delete(id);
    });
    return delivered;
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
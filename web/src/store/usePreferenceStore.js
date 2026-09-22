import { create } from 'zustand';
import { getPreferences, updatePreferences as updatePrefsApi } from '../api/customerApi';

const usePreferenceStore = create((set, get) => ({
  prefs: [],
  loading: false,
  /** 最近一次保存后的「渠道缺收件地址」警告（PRD-40）。只在内存里，刷新即清 */
  warnings: [],

  fetchPreferences: async () => {
    set({ loading: true });
    try {
      const data = await getPreferences();
      const parsed = (Array.isArray(data) ? data : []).map(p => ({
        ...p,
        channels: Array.isArray(p.channels) ? p.channels : [],
      }));
      set({ prefs: parsed, loading: false });
    } catch (e) {
      set({ loading: false });
      throw e;
    }
  },

  updatePreferences: async (newPrefs) => {
    // 按 eventType 去重（后端同一 (customer_id, event_type) 唯一）
    const map = new Map();
    newPrefs.forEach(p => {
      map.set(p.eventType, {
        ...p,
        channels: p.channels || [],
      });
    });
    const formatted = Array.from(map.values());

    const result = await updatePrefsApi(formatted);
    const warnings = Array.isArray(result?.warnings) ? result.warnings : [];
    await get().fetchPreferences();
    set({ warnings });
    return warnings;
  },

  clearWarnings: () => set({ warnings: [] }),
}));

export default usePreferenceStore;
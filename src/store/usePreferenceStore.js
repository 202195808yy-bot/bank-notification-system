import { create } from 'zustand';
import { getPreferences, updatePreferences as updatePrefsApi } from '../api/customerApi';

const usePreferenceStore = create((set, get) => ({
  prefs: [],
  loading: false,

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

    await updatePrefsApi(formatted);
    await get().fetchPreferences();
  },
}));

export default usePreferenceStore;
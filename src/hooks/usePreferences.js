import { useState, useEffect } from 'react';
import { getPreferences, updatePreferences as updatePrefsApi } from '../api/customerApi';

export function usePreferences() {
  const [prefs, setPrefs] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetch = async () => {
    setLoading(true);
    try {
      const data = await getPreferences();
      // 将后端返回的 channels 字符串转为数组，方便前端表格/表单使用
      const parsed = data.map(p => ({
        ...p,
        channels: p.channels ? JSON.parse(p.channels) : [],
      }));
      setPrefs(parsed);
    } catch (e) {
      console.error('加载偏好失败', e);
      setPrefs([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetch(); }, []);

  const update = async (newPrefs) => {
    // channels 序列化由 customerApi.updatePreferences 统一处理，此处只透传
    await updatePrefsApi(newPrefs);
    await fetch(); // 刷新列表
  };

  return { prefs, loading, update };
}
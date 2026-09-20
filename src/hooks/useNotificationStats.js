import { useState, useEffect } from 'react';
import { getStats } from '../api/notificationApi';

export function useNotificationStats() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getStats()
      .then(setStats)
      .catch(err => {
        console.error('加载统计数据失败', err);
        setStats(null);
      })
      .finally(() => setLoading(false));
  }, []);

  return { stats, loading };
}
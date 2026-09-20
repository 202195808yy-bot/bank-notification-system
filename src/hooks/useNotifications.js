import { useState, useEffect, useCallback } from 'react';
import { getNotifications } from '../api/notificationApi';

export function useNotifications(initialParams = {}) {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 0, size: 10, total: 0 });
  const [params, setParams] = useState(initialParams);

  const fetch = useCallback(async (page = 0, size = 10) => {
    setLoading(true);
    try {
      const res = await getNotifications({ ...params, page, size });
      setData(res.content || []);
      setPagination({
        page: res.number ?? page,
        size: res.size ?? size,
        total: res.totalElements ?? 0,
      });
    } catch (error) {
      console.error('加载通知失败', error);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetch(0, pagination.size);
  }, [fetch]);

  return { data, loading, pagination, fetch, params, setParams };
}
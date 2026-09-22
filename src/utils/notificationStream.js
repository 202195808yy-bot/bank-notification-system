/**
 * 通知的实时通道（SSE）。
 *
 * 为什么不用浏览器原生 EventSource：它不能带 Authorization 头，只能把令牌塞进 query，
 * 而 URL 会进访问日志、Referer 与浏览器历史 —— 为了不把令牌暴露在这些地方，
 * 这里用 fetch + ReadableStream 自己解析 SSE 帧。
 *
 * 通道只当"该刷新了"的信号用，界面内容仍然以 REST 查询为准：
 * 推送与查询共用同一个取数入口，就不会出现"声音说有一条、列表却是旧的"。
 *
 * @param {{onNotification: (event: object) => void, onStateChange?: (state: 'open'|'down') => void}} handlers
 * @returns {() => void} 关闭连接
 */
const STREAM_PATH = '/api/notifications/stream';

const readToken = () => {
  try {
    const stored = JSON.parse(localStorage.getItem('user') || 'null');
    if (stored?.token) return stored.token;
  } catch (e) {
    // localStorage 里是脏数据，回落到独立键
  }
  return localStorage.getItem('token') || '';
};

export function openNotificationStream({ onNotification, onStateChange } = {}) {
  let closed = false;
  let controller = null;
  let retryTimer = null;
  let attempt = 0;

  const scheduleReconnect = () => {
    if (closed) return;
    // 指数退避、30 秒封顶：网关重启或代理不支持流式时，不会变成疯狂重连
    const delay = Math.min(30000, 1000 * 2 ** Math.min(attempt, 5));
    attempt += 1;
    retryTimer = setTimeout(connect, delay);
  };

  const handleFrame = (frame) => {
    let event = 'message';
    const dataLines = [];
    frame.split('\n').forEach((line) => {
      if (line.startsWith(':')) return;            // 心跳注释帧
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
    });
    if (event !== 'notification' || dataLines.length === 0) return;
    try {
      onNotification?.(JSON.parse(dataLines.join('\n')));
    } catch (e) {
      // 帧内容不完整或不是 JSON：丢掉这一帧即可，轮询会补上真实状态
    }
  };

  const connect = async () => {
    if (closed) return;
    controller = new AbortController();
    try {
      const response = await fetch(STREAM_PATH, {
        headers: { Authorization: `Bearer ${readToken()}`, Accept: 'text/event-stream' },
        signal: controller.signal,
        cache: 'no-store',
      });
      if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
      attempt = 0;
      onStateChange?.('open');
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let sep = buffer.indexOf('\n\n');
        while (sep >= 0) {
          handleFrame(buffer.slice(0, sep));
          buffer = buffer.slice(sep + 2);
          sep = buffer.indexOf('\n\n');
        }
      }
      throw new Error('流已结束');
    } catch (e) {
      if (closed) return;
      onStateChange?.('down');
      scheduleReconnect();
    }
  };

  connect();

  return () => {
    closed = true;
    if (retryTimer) clearTimeout(retryTimer);
    if (controller) controller.abort();
  };
}

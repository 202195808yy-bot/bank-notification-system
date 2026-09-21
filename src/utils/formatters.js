/**
 * 后端的 createdAt/updatedAt 是 LocalDateTime，序列化成不带时区偏移的 ISO 串
 * （实测 `2026-09-21T04:35:36.026731`）。容器里的 JVM 默认时区是 UTC，而 JS 会把不带偏移的串
 * 按本地时间解析 —— 东八区浏览器上"刚收到的通知"就显示成"8 小时前"。
 * 所以这里把无偏移的串按 UTC 解释。⚠️ 这是**部署口径**（compose 未给 Java 服务设 TZ，即 UTC）；
 * 正确的长期解法是后端输出自带偏移，见 PRODUCT_CODE_REVIEW.md 的 PRD-39。
 */
export const parseServerInstant = (value) => {
  if (!value) return null;
  const hasOffset = /(?:Z|[+-]\d{2}:?\d{2})$/.test(String(value));
  const date = new Date(hasOffset ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? null : date;
};

export function formatDateTime(dateString, locale) {
  const date = parseServerInstant(dateString);
  if (!date) return '';
  // 以前写死 'en-US'：中文/俄语界面里日期仍是 09/21/2026, 04:22 AM 这种美式格式
  return date.toLocaleString(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

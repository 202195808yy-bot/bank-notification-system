import axios from './axiosInstance';

export function login(credentials) {
  return axios.post('/auth/login', credentials).then(res => res.data);
}

export function getCurrentCustomer() {
  return axios.get('/customers/me').then(res => res.data);
}

export function updateProfile(profile) {
  return axios.patch('/customers/me', profile).then(res => res.data);
}

// 管理员用户目录（后端 GET /api/customers，非 ADMIN 返回 403 ADMIN_REQUIRED）
export function listCustomers() {
  return axios.get('/customers').then(res => res.data);
}

export function getPreferences() {
  return axios.get('/preferences').then(res => res.data);
}

export function updatePreferences(prefs) {
  // channels 必须按数组发送：后端 PreferenceController 直接绑定 List<NotificationPreference>，
  // 实体里的 channels 是 List<String>，@Convert(StringListConverter) 只负责落库成 JSON 文本。
  // 传字符串会让 Jackson 抛 HttpMessageNotReadableException，接口返回 400。
  const formatted = prefs.map(p => ({
    ...p,
    channels: Array.isArray(p.channels) ? p.channels : [],
  }));
  // 返回值是 { warnings: [...] }：保存成功但某个渠道缺收件地址时的提示（PRD-40）
  return axios.put('/preferences', formatted).then(res => res.data);
}

// 某个事件类型的可送达面（后端 GET /api/preferences/reach，ADMIN-only）
export function getEventReach(eventType) {
  return axios.get('/preferences/reach', { params: { eventType } }).then(res => res.data);
}

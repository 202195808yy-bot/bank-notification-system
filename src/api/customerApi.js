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
  return axios.put('/preferences', formatted).then(res => res.data);
}

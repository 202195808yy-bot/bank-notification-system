import axios from './axiosInstance';

export function getTemplates(params) {
  return axios.get('/templates', { params }).then(res => {
    const data = res.data;
    // 自动识别数组：如果是数组直接返回，如果有 content 字段则返回 content，否则返回空数组
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data.content)) return data.content;
    if (data && Array.isArray(data.data)) return data.data;
    return [];
  });
}

export function createTemplate(data) {
  return axios.post('/templates', data).then(res => res.data);
}

export function updateTemplate(id, data) {
  return axios.put(`/templates/${id}`, data).then(res => res.data);
}

export function deleteTemplate(id) {
  return axios.delete(`/templates/${id}`).then(res => res.data);
}
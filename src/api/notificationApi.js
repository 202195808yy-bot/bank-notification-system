import axios from './axiosInstance';

export function getNotifications(params) {
  return axios.get('/notifications', { params }).then(res => res.data);
}

export function retryNotification(id) {
  return axios.post(`/notifications/${id}/retry`);
}

export function getStats() {
  return axios.get('/notifications/stats').then(res => res.data);
}
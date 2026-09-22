import axios from 'axios';
import { message } from 'antd';
import { messageText as t, errorText as codeText } from '../i18n';

const instance = axios.create({
    baseURL: '/api',
    timeout: 10000,
});

/**
 * 从 JWT token 中解析 payload（基础实现）
 */
const parseJwtPayload = (token) => {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = atob(base64);
        return JSON.parse(jsonPayload);
    } catch (e) {
        console.error('JWT 解析失败', e);
        return null;
    }
};

// 请求拦截：自动带上 token 和用户 ID（从 JWT 提取）
instance.interceptors.request.use(config => {
    const stored = localStorage.getItem('user');
    if (stored) {
        try {
            const user = JSON.parse(stored);
            if (user.token) {
                // 1. 设置 Authorization（确保无拼写错误）
                config.headers.Authorization = `Bearer ${user.token}`;

                // 2. 从 JWT 中提取用户 ID（忽略 localStorage 中的其他字段）
                const payload = parseJwtPayload(user.token);
                if (payload && payload.sub) {
                    config.headers['X-User-Id'] = payload.sub; // sub 对应真实用户 ID
                } else {
                    console.warn('JWT 中未找到 sub 字段，请求可能失败');
                }
            }
        } catch (e) {
            console.error('解析登录信息失败', e);
        }
    }
    return config;
});

// 响应拦截：统一错误提示，保留登录页自行处理 401
instance.interceptors.response.use(
    response => response,
    error => {
        const status = error.response?.status;
        const msg = error.response?.data?.message;
        const code = error.response?.data?.code;
        const text = codeText(code);
        const url = error.config?.url;

        // 登录接口的 401 不跳转，由页面自行处理
        if (status === 401 && url === '/auth/login') {
            return Promise.reject(error);
        }
        // 其他接口的 401 清除登录信息并跳转登录页
        if (status === 401) {
            localStorage.removeItem('user');
            window.location.href = '/login';
            return Promise.reject(error);
        }

        switch (status) {
            case 400: message.error(text || msg || t('error.400')); break;
            case 403: message.error(text || t('error.403')); break;
            case 404: message.error(text || t('error.404')); break;
            case 409: message.error(text || msg || t('error.409')); break;
            case 500: message.error(t('error.500')); break;
            // 502/503 = "上游活着但暂时不能用"（如 Kafka 不可达时事件入口返回的 EVENT_PUBLISH_FAILED）。
            // 走 default 会显示成一句笼统的网络错误，用户不知道该重试还是该放弃。
            case 502:
            case 503: message.error(text || msg || t('error.500')); break;
            default: message.error(t('error.network'));
        }
        return Promise.reject(error);
    }
);

export default instance;
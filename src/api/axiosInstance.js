import axios from 'axios';
import { message } from 'antd';
import messages from '../i18n/messages';

// 拦截器在 React 树之外，拿不到 useIntl，只能按 localStorage 的语言直接查语料
const t = (id) => {
    const locale = localStorage.getItem('locale') || 'ru';
    return messages[locale]?.[id] ?? messages.ru[id];
};

// 后端业务错误统一返回 {code: '...'}，语料里对应 key 为 error.code.<CODE>；没有则回落到通用文案
const codeText = (code) => (code ? t(`error.code.${code}`) : undefined);

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
            default: message.error(t('error.network'));
        }
        return Promise.reject(error);
    }
);

export default instance;
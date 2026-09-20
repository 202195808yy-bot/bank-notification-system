export const EVENT_TYPES = {
    TRANSACTION: 'Transaction Alert',
    RISK_ALERT: 'Risk Alert',
    BILL: 'Bill Reminder',
    LOGIN: 'Login Notification',
    SECURITY: 'Security Alert',
    PROMOTION: 'Promotion',
};

export const EVENT_TYPE_COLORS = {
    TRANSACTION: { bg: '#e6f7ff', border: '#1890ff', text: '#1890ff', tag: 'blue' },
    RISK_ALERT: { bg: '#fff2f0', border: '#ff4d4f', text: '#ff4d4f', tag: 'red' },
    BILL: { bg: '#f6ffed', border: '#52c41a', text: '#52c41a', tag: 'green' },
    LOGIN: { bg: '#fffbe6', border: '#faad14', text: '#faad14', tag: 'gold' },
    SECURITY: { bg: '#e6f7ff', border: '#1890ff', text: '#1890ff', tag: 'blue' },
    PROMOTION: { bg: '#f9f0ff', border: '#722ed1', text: '#722ed1', tag: 'purple' },
};

export const EVENT_TYPE_ICONS = {
    TRANSACTION: '💳',
    RISK_ALERT: '⚠️',
    BILL: '📄',
    LOGIN: '🔐',
    SECURITY: '🛡️',
    PROMOTION: '🎁',
};

export const CHANNELS = {
    SMS: 'SMS',
    EMAIL: 'Email',
    PUSH: 'Push',
};

export const CHANNEL_COLORS = {
    SMS: { bg: '#e6f7ff', tag: 'blue', icon: '📱' },
    EMAIL: { bg: '#f6ffed', tag: 'green', icon: '📧' },
    PUSH: { bg: '#fffbe6', tag: 'gold', icon: '🔔' },
};

// 与后端 com.bank.common.enums.SendStatus 一一对应，新增状态必须同时改这里
export const STATUS_MAP = {
    PENDING: 'Pending',
    SENT: 'Sent',
    FAILED: 'Failed',
    SKIPPED: 'Skipped',
    FAILED_VALIDATION: 'Validation failed',
};

export const STATUS_COLOR = {
    PENDING: 'processing',
    SENT: 'success',
    FAILED: 'error',
    SKIPPED: 'default',
    FAILED_VALIDATION: 'warning',
};

export const STATUS_DETAILS = {
    PENDING: { icon: '⏳', color: '#faad14', bg: '#fffbe6' },
    SENT: { icon: '✅', color: '#52c41a', bg: '#f6ffed' },
    FAILED: { icon: '❌', color: '#ff4d4f', bg: '#fff2f0' },
    SKIPPED: { icon: '⏭️', color: '#64748b', bg: '#f1f5f9' },
    FAILED_VALIDATION: { icon: '🚫', color: '#d46b08', bg: '#fff7e6' },
};

// 通知未投递成功的原因码（Notification.reason），i18n key 为 enum.reason.<CODE>
export const REASONS = {
    QUIET_PERIOD: 'Quiet period',
    TEMPLATE_MISSING: 'No template',
    NO_PHONE: 'Phone missing',
    NO_EMAIL: 'Email missing',
    NO_PUSH_TOKEN: 'Push token missing',
    CONTACT_UNAVAILABLE: 'Contact unavailable',
    UNKNOWN_CHANNEL: 'Unknown channel',
};

export const MODERN_THEME = {
    colors: {
        primary: '#1677ff',
        primaryLight: '#4096ff',
        primaryDark: '#0958d9',
        secondary: '#4096ff',
        accent: '#0958d9',
        success: '#52c41a',
        successLight: '#73d13d',
        warning: '#faad14',
        warningLight: '#ffc53d',
        error: '#ff4d4f',
        errorLight: '#ff7875',
        info: '#1677ff',
        textPrimary: '#1e293b',
        textSecondary: '#64748b',
        textTertiary: '#94a3b8',
        background: '#f5f5f5',
        pageBg: '#ffffff',
        cardBg: '#ffffff',
        cardBgLight: '#fafafa',
        sideBarBg: '#ffffff',
        sideBarBgHover: '#f5f5f5',
        sideBarActive: '#1677ff',
        sideBarText: '#64748b',
        sideBarTextActive: '#1677ff',
        border: '#f0f0f0',
        borderLight: '#e8e8e8',
        hover: '#f5f5f5',
        glass: 'rgba(22, 119, 255, 0.05)',
        glassBorder: 'rgba(22, 119, 255, 0.1)',
    },
    gradients: {
        primary: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
        secondary: 'linear-gradient(135deg, #4096ff 0%, #1677ff 100%)',
        success: 'linear-gradient(135deg, #52c41a 0%, #389e0d 100%)',
        warning: 'linear-gradient(135deg, #faad14 0%, #d48806 100%)',
        error: 'linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%)',
        info: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
        card: 'linear-gradient(145deg, #ffffff, #fafafa)',
        header: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
    },
    shadows: {
        card: '0 2px 8px rgba(0, 0, 0, 0.04)',
        cardHover: '0 4px 16px rgba(0, 0, 0, 0.08)',
        button: '0 2px 8px rgba(22, 119, 255, 0.2)',
        buttonHover: '0 4px 16px rgba(22, 119, 255, 0.3)',
        dropdown: '0 4px 16px rgba(0, 0, 0, 0.1)',
        glow: '0 0 16px rgba(22, 119, 255, 0.2)',
    },
    borderRadius: {
        card: '12px',
        cardLarge: '16px',
        button: '8px',
        buttonLarge: '12px',
        tag: '6px',
        avatar: '50%',
    },
    transitions: {
        fast: '0.15s ease',
        normal: '0.25s ease',
        slow: '0.4s ease',
        bounce: '0.4s cubic-bezier(0.68, -0.55, 0.265, 1.55)',
    },
    spacing: {
        xs: '4px',
        sm: '8px',
        md: '12px',
        lg: '16px',
        xl: '24px',
        xxl: '32px',
        xxxl: '48px',
    },
    typography: {
        heading1: '28px',
        heading2: '22px',
        heading3: '18px',
        heading4: '16px',
        body: '14px',
        caption: '12px',
    },
};
import { Dropdown } from 'antd';
import { GlobalOutlined, DownOutlined } from '@ant-design/icons';
import { resolveLocale } from '../i18n';

// 语言名一律用该语言自己的写法，这是语言切换器的通行做法，所以这里刻意不接 i18n
const LOCALES = [
  { key: 'ru', label: 'Русский' },
  { key: 'zh', label: '中文' },
  { key: 'en', label: 'English' },
];

/**
 * 全站唯一的语言切换器：Header 与登录/注册页共用这一份实现。
 * 以前 Header 里另写了一个下拉（同样的三种语言、同样的切换逻辑），于是「当前语言」在两处
 * 表现不一致 —— 登录页有选中态、Header 没有。
 * 登录/注册页在 Layout 之外，所以两者都得自己挂切换器；与 Header 用同一套机制：
 * 写 localStorage 后整页重载（界面语言不做运行时热切）。
 *
 * variant="plain" 给 Header 用（无边框、靠 hover 提示可点），默认带边框给未登录页用。
 */
export default function LocaleSwitcher({ variant = 'bordered' }) {
  const current = resolveLocale();

  const handleChange = (key) => {
    localStorage.setItem('locale', key);
    window.location.reload();
  };

  const triggerStyle = variant === 'plain'
    ? {
        padding: '8px 12px',
        minWidth: '92px',
        justifyContent: 'center',
        color: '#374151',
        border: '1px solid transparent',
        background: 'transparent',
      }
    : {
        border: '1px solid #e5e7eb',
        background: '#fff',
        color: '#374151',
      };

  return (
    <Dropdown
      menu={{
        items: LOCALES.map(({ key, label }) => ({ key, label, onClick: () => handleChange(key) })),
        selectedKeys: [current],
      }}
      placement="bottomRight"
    >
      <div
        role="button"
        tabIndex={0}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          padding: '8px 12px',
          borderRadius: '8px',
          fontSize: '13px',
          fontWeight: 500,
          cursor: 'pointer',
          transition: 'all 0.2s ease',
          ...triggerStyle,
        }}
        onMouseEnter={(e) => { e.currentTarget.style.background = variant === 'plain' ? '#f3f4f6' : '#f9fafb'; }}
        onMouseLeave={(e) => { e.currentTarget.style.background = variant === 'plain' ? 'transparent' : '#fff'; }}
      >
        <GlobalOutlined style={{ color: '#6b7280' }} />
        <span>{LOCALES.find((l) => l.key === current)?.label || LOCALES[0].label}</span>
        <DownOutlined style={{ fontSize: '10px', color: '#9ca3af' }} />
      </div>
    </Dropdown>
  );
}

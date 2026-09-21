import { Dropdown } from 'antd';
import { GlobalOutlined, DownOutlined } from '@ant-design/icons';

// 语言名一律用该语言自己的写法，这是语言切换器的通行做法，所以这里刻意不接 i18n
const LOCALES = [
  { key: 'ru', label: 'Русский' },
  { key: 'zh', label: '中文' },
  { key: 'en', label: 'English' },
];

/**
 * 登录/注册页在 Layout 之外，拿不到 Header 里那个语言下拉，
 * 以前未登录用户根本没有切换语言的入口。与 Header 用同一套机制：写 localStorage 后整页重载。
 */
export default function LocaleSwitcher() {
  const current = localStorage.getItem('locale') || 'ru';

  const handleChange = (key) => {
    localStorage.setItem('locale', key);
    window.location.reload();
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
          border: '1px solid #e5e7eb',
          background: '#fff',
          color: '#374151',
          fontSize: '13px',
          fontWeight: 500,
          cursor: 'pointer',
        }}
      >
        <GlobalOutlined style={{ color: '#6b7280' }} />
        <span>{LOCALES.find((l) => l.key === current)?.label || LOCALES[0].label}</span>
        <DownOutlined style={{ fontSize: '10px', color: '#9ca3af' }} />
      </div>
    </Dropdown>
  );
}

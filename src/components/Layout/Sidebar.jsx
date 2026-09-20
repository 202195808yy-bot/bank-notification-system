import React from 'react';
import { Layout, Menu } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  SettingOutlined,
  BellOutlined,
  FileTextOutlined,
  SendOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useIntl } from 'react-intl';
import useAuthStore from '../../store/useAuthStore';

const { Sider } = Layout;

const menuItems = [
  {
    key: '/dashboard',
    icon: DashboardOutlined,
    id: 'menu.dashboard',
  },
  {
    key: '/preferences',
    icon: SettingOutlined,
    id: 'menu.preferences',
  },
  {
    key: '/notifications',
    icon: BellOutlined,
    id: 'menu.history',
  },
  {
    key: '/profile',
    icon: UserOutlined,
    id: 'menu.profile',
  },
  {
    key: '/admin/templates',
    icon: FileTextOutlined,
    id: 'menu.templates',
    roles: ['ADMIN'],
  },
  {
    key: '/admin/events',
    icon: SendOutlined,
    id: 'menu.events',
    roles: ['ADMIN'],
  },
];

export default function Sidebar() {
  const intl = useIntl();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = React.useState(false);
  const [isMobile, setIsMobile] = React.useState(false);

  React.useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768);
      if (window.innerWidth < 768) {
        setCollapsed(true);
      }
    };
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  const currentPath = location.pathname;

  const role = useAuthStore((state) => state.user?.role);

  const items = menuItems
    .filter((item) => !item.roles || item.roles.includes(role))
    .map((item) => {
      const IconComponent = item.icon;
      return {
        key: item.key,
        icon: <IconComponent style={{ fontSize: '16px' }} />,
        label: collapsed ? null : intl.formatMessage({ id: item.id }),
      };
    });

  return (
    <Sider
      width={collapsed ? 64 : 200}
      style={{
        background: '#fff',
        boxShadow: collapsed ? 'none' : '2px 0 8px rgba(0, 0, 0, 0.05)',
        position: 'fixed',
        left: 0,
        top: 64,
        bottom: 0,
        zIndex: 99,
        borderRight: '1px solid #f0f0f0',
        overflow: 'hidden',
        height: 'calc(100vh - 64px)',
        transition: 'width 0.25s ease, box-shadow 0.25s ease',
      }}
      collapsible={true}
      collapsed={collapsed}
      trigger={null}
    >
      <div style={{ 
        padding: '16px 8px',
        borderBottom: '1px solid #f0f0f0',
      }}>
        {!collapsed && (
          <div style={{ 
            fontSize: '14px', 
            fontWeight: 600, 
            color: 'rgba(0, 0, 0, 0.88)',
            paddingLeft: '8px',
            animation: 'fadeIn 0.2s ease',
          }}>
            {intl.formatMessage({ id: 'sidebar.menu' })}
          </div>
        )}
      </div>

      <Menu
        mode="inline"
        selectedKeys={[currentPath]}
        style={{ 
          height: 'calc(100% - 80px)',
          borderRight: 0,
          padding: '8px',
          background: 'transparent',
        }}
        items={items}
        onClick={({ key }) => {
          navigate(key);
          if (isMobile) {
            setCollapsed(true);
          }
        }}
        defaultOpenKeys={['/admin']}
      />

      <div style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        padding: '12px',
        borderTop: '1px solid #f0f0f0',
        background: '#fff',
      }}>
        <button
          onClick={() => setCollapsed(!collapsed)}
          style={{
            width: '100%',
            height: '36px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: '#f5f5f5',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => e.currentTarget.style.background = '#e8e8e8'}
          onMouseLeave={(e) => e.currentTarget.style.background = '#f5f5f5'}
        >
          {collapsed ? (
            <MenuUnfoldOutlined style={{ fontSize: '16px', color: 'rgba(0, 0, 0, 0.65)' }} />
          ) : (
            <MenuFoldOutlined style={{ fontSize: '16px', color: 'rgba(0, 0, 0, 0.65)' }} />
          )}
        </button>
      </div>
    </Sider>
  );
}
import { Layout, Menu } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import useAuthStore from '../../store/useAuthStore';
import { visibleMenu } from './menu';

const { Sider } = Layout;

/**
 * 折叠态由 Layout 持有并回传：Content 的 margin-left 必须与这里真实的宽度同源，
 * 否则「收起菜单」只会让菜单变窄，正文却仍按 200px 让位，中间留下一条 136px 的空白。
 * 移动端不渲染本组件（导航走 Header 里的抽屉），fixed 侧栏压在正文上是遮挡。
 */
export default function Sidebar({ collapsed, onToggle }) {
    const intl = useIntl();
    const navigate = useNavigate();
    const location = useLocation();
    const role = useAuthStore((state) => state.user?.role);
    const currentPath = location.pathname;

    const items = visibleMenu(role).map((item) => {
        const IconComponent = item.icon;
        return {
            key: item.key,
            icon: <IconComponent style={{ fontSize: '16px' }} />,
            // 折叠时也必须把文字交给 antd：它自己会用 CSS 隐掉（opacity:0），
            // 并把它当作悬停 Tooltip 的内容。这里给 null 会同时丢掉两样东西 ——
            // 图标不再有名称提示，且弹出一个 28×32 的空黑气泡。
            label: intl.formatMessage({ id: item.id }),
        };
    });

    return (
        <Sider
            width={200}
            style={{
                background: '#fff',
                boxShadow: collapsed ? 'none' : '2px 0 8px rgba(0, 0, 0, 0.05)',
                position: 'fixed',
                left: 0,
                top: 'var(--layout-header-height)',
                bottom: 0,
                zIndex: 99,
                borderRight: '1px solid #f0f0f0',
                overflow: 'hidden',
                height: 'calc(100vh - var(--layout-header-height))',
                transition: 'width 0.25s ease, box-shadow 0.25s ease',
            }}
            collapsible={true}
            collapsed={collapsed}
            collapsedWidth={64}
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
                onClick={({ key }) => navigate(key)}
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
                    onClick={onToggle}
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

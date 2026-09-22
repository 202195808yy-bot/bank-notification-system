import { useEffect, useState } from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { Layout as AntLayout, Spin } from 'antd';
import { useIntl } from 'react-intl';
import Header from './Header';
import Sidebar from './Sidebar';
import useAuthStore from '../../store/useAuthStore';

const { Content } = AntLayout;

// 头部高度与侧栏宽度都只认 index.css 里的 --layout-* 变量：JS 再抄一份数字，
// 就会出现「CSS 在 480px 以下把头部改成 56px，正文仍按 64px 让位」这类错位。
const HEADER_HEIGHT = 'var(--layout-header-height)';

export default function Layout() {
    const intl = useIntl();
    const user = useAuthStore((state) => state.user);
    const [loading, setLoading] = useState(true);
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [isMobile, setIsMobile] = useState(false);

    useEffect(() => {
        const stored = localStorage.getItem('user');
        if (stored) {
            useAuthStore.setState({ user: JSON.parse(stored) });
        }
        setTimeout(() => setLoading(false), 300);
    }, []);

    useEffect(() => {
        const checkMobile = () => setIsMobile(window.innerWidth < 768);
        checkMobile();
        window.addEventListener('resize', checkMobile);
        return () => window.removeEventListener('resize', checkMobile);
    }, []);

    if (loading) {
        return (
            <div style={{ 
                display: 'flex', 
                justifyContent: 'center', 
                alignItems: 'center', 
                minHeight: '100vh', 
                background: '#f5f5f5' 
            }}>
                <div style={{ textAlign: 'center' }}>
                    <div style={{ 
                        width: '48px', 
                        height: '48px', 
                        background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
                        borderRadius: '12px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        marginBottom: '12px',
                        boxShadow: '0 4px 12px rgba(22, 119, 255, 0.3)',
                    }}>
                        <Spin size="default" tip="" style={{ color: '#fff' }} />
                    </div>
                    <p style={{ color: 'rgba(0, 0, 0, 0.45)', fontSize: '14px' }}>
                        {intl.formatMessage({ id: 'common.loading' })}
                    </p>
                </div>
            </div>
        );
    }

    if (!user) return <Navigate to="/login" replace />;

    const sidebarState = isMobile ? 'hidden' : sidebarCollapsed ? 'collapsed' : 'expanded';

    return (
        <AntLayout style={{ minHeight: '100vh', background: '#f5f5f5', overflowX: 'hidden' }}>
            <Header isMobile={isMobile} />
            <div style={{ 
                paddingTop: HEADER_HEIGHT,
                minHeight: '100vh',
                boxSizing: 'border-box',
                overflowX: 'hidden',
            }}>
                <AntLayout hasSider style={{ marginLeft: 0, minHeight: 'calc(100vh - 64px)', overflowX: 'hidden' }}>
                    {!isMobile && (
                        <Sidebar
                            collapsed={sidebarCollapsed}
                            onToggle={() => setSidebarCollapsed((value) => !value)}
                        />
                    )}
                    <Content 
                        style={{ 
                            padding: '24px',
                            minHeight: 'calc(100vh - 64px)',
                            background: '#f5f5f5',
                            boxSizing: 'border-box',
                            overflowX: 'auto',
                        }}
                        data-sider={sidebarState}
                    >
                        <div style={{
                            background: '#ffffff',
                            borderRadius: '12px',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)',
                            padding: '24px',
                            minHeight: 'calc(100vh - 64px - 48px)',
                            animation: 'fadeIn 0.3s ease',
                            overflowX: 'auto',
                        }}>
                            <Outlet />
                        </div>
                    </Content>
                </AntLayout>
            </div>
        </AntLayout>
    );
}
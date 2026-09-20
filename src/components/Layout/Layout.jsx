import React, { useEffect, useState } from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { Layout as AntLayout, Spin } from 'antd';
import { useIntl } from 'react-intl';
import Header from './Header';
import Sidebar from './Sidebar';
import useAuthStore from '../../store/useAuthStore';

const { Content } = AntLayout;

const HEADER_HEIGHT = 64;
const SIDEBAR_WIDTH = 200;
const SIDEBAR_COLLAPSED_WIDTH = 64;

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
        const checkMobile = () => {
            setIsMobile(window.innerWidth < 768);
            if (window.innerWidth < 768) {
                setSidebarCollapsed(true);
            }
        };
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

    const sidebarWidth = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH;

    return (
        <AntLayout style={{ minHeight: '100vh', background: '#f5f5f5', overflowX: 'hidden' }}>
            <Header />
            <div style={{ 
                paddingTop: HEADER_HEIGHT,
                minHeight: '100vh',
                boxSizing: 'border-box',
                overflowX: 'hidden',
            }}>
                <AntLayout hasSider style={{ marginLeft: 0, minHeight: 'calc(100vh - 64px)', overflowX: 'hidden' }}>
                    <Sidebar />
                    <Content 
                        style={{ 
                            marginLeft: isMobile ? 0 : sidebarWidth,
                            padding: '24px',
                            minHeight: 'calc(100vh - 64px)',
                            background: '#f5f5f5',
                            transition: 'margin-left 0.25s ease',
                            boxSizing: 'border-box',
                            overflowX: 'auto',
                        }}
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
import React, { useState, useEffect } from 'react';
import { Layout, Dropdown, Avatar, Badge } from 'antd';
import { 
  BellOutlined, 
  DownOutlined, 
  BankOutlined, 
  UserOutlined,
  MenuOutlined,
  XOutlined,
  CaretRightOutlined
} from '@ant-design/icons';
import NotificationDropdown from './NotificationDropdown';
import { useNavigate, useLocation } from 'react-router-dom';
import { useIntl } from 'react-intl';
import useAuthStore from '../../store/useAuthStore';
import useNotificationStore from '../../store/useNotificationStore';

const { Header: AntHeader } = Layout;

const NAV_ITEMS = [
  { key: '/dashboard', id: 'menu.dashboard' },
  { key: '/notifications', id: 'menu.history' },
  { key: '/preferences', id: 'menu.preferences' },
];

export default function Header() {
    const { user, logout } = useAuthStore();
    const unreadCount = useNotificationStore((state) => state.unreadCount);
    const fetchUnreadCount = useNotificationStore((state) => state.fetchUnreadCount);
    const navigate = useNavigate();
    const location = useLocation();
    const intl = useIntl();
    const [currentLocale, setCurrentLocale] = useState(localStorage.getItem('locale') || 'ru');
    const [isMobile, setIsMobile] = useState(false);
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [isNotificationOpen, setIsNotificationOpen] = useState(false);

    useEffect(() => {
        fetchUnreadCount();
    }, [fetchUnreadCount]);

    useEffect(() => {
        const checkMobile = () => {
            const mobile = window.innerWidth < 768;
            setIsMobile(mobile);
            if (mobile) {
                setIsMenuOpen(false);
            }
        };
        checkMobile();
        window.addEventListener('resize', checkMobile);
        return () => window.removeEventListener('resize', checkMobile);
    }, []);

    const handleLocaleChange = (locale) => {
        setCurrentLocale(locale);
        localStorage.setItem('locale', locale);
        window.location.reload();
    };

    const localeMenuItems = [
        { key: 'zh', label: '中文', onClick: () => handleLocaleChange('zh') },
        { key: 'en', label: 'English', onClick: () => handleLocaleChange('en') },
        { key: 'ru', label: 'Русский', onClick: () => handleLocaleChange('ru') },
    ];

    const userMenuItems = [
        { 
            key: 'profile', 
            label: intl.formatMessage({ id: 'menu.profile' }),
            onClick: () => {
                setIsMenuOpen(false);
                navigate('/profile');
            }
        },
        { type: 'divider' },
        { 
            key: 'logout', 
            label: intl.formatMessage({ id: 'menu.logout' }), 
            onClick: () => {
                logout();
                setIsMenuOpen(false);
            }
        },
    ];

    const getLocaleLabel = (locale) => {
        switch (locale) {
            case 'zh': return '中文';
            case 'en': return 'English';
            default: return 'Русский';
        }
    };

    const currentPath = location.pathname;

    return (
        <>
            <AntHeader 
                style={{
                    background: 'linear-gradient(135deg, #ffffff 0%, #f8fafc 100%)',
                    padding: '0 24px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05), 0 1px 2px rgba(0, 0, 0, 0.03)',
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    zIndex: 999,
                    height: '64px',
                    minHeight: '64px',
                    borderBottom: '1px solid rgba(0, 0, 0, 0.06)',
                    transition: 'all 0.3s ease',
                }}
            >
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '16px',
                    flexShrink: 0,
                }}>
                    <button
                        onClick={() => setIsMenuOpen(!isMenuOpen)}
                        style={{
                            display: isMobile ? 'flex' : 'none',
                            width: '40px',
                            height: '40px',
                            borderRadius: '8px',
                            border: 'none',
                            background: 'transparent',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer',
                            transition: 'background 0.2s ease',
                            color: '#1f2937',
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(0,0,0,0.05)'}
                        onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                        {isMenuOpen ? (
                            <XOutlined style={{ fontSize: '20px' }} />
                        ) : (
                            <MenuOutlined style={{ fontSize: '20px' }} />
                        )}
                    </button>

                    <div 
                        style={{
                            width: '44px',
                            height: '44px',
                            background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
                            borderRadius: '12px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 4px 16px rgba(22, 119, 255, 0.3)',
                            transition: 'transform 0.2s ease, box-shadow 0.2s ease',
                            cursor: 'pointer',
                            position: 'relative',
                            overflow: 'hidden',
                            flexShrink: 0,
                        }}
                        onMouseEnter={(e) => {
                            e.currentTarget.style.transform = 'scale(1.05)';
                            e.currentTarget.style.boxShadow = '0 6px 20px rgba(22, 119, 255, 0.4)';
                        }}
                        onMouseLeave={(e) => {
                            e.currentTarget.style.transform = 'scale(1)';
                            e.currentTarget.style.boxShadow = '0 4px 16px rgba(22, 119, 255, 0.3)';
                        }}
                        onClick={() => navigate('/dashboard')}
                    >
                        <div style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            background: 'linear-gradient(135deg, rgba(255,255,255,0.25) 0%, transparent 50%)',
                            pointerEvents: 'none',
                        }} />
                        <BankOutlined style={{ fontSize: '22px', color: '#fff', position: 'relative', zIndex: 1 }} />
                    </div>

                    <div style={{
                        display: isMobile ? 'none' : 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        paddingLeft: '16px',
                        marginLeft: '16px',
                        borderLeft: '2px solid #e5e7eb',
                        gap: '3px',
                        maxWidth: '260px',
                        overflow: 'hidden',
                        position: 'relative',
                    }}>
                        <div style={{
                            fontSize: '17px',
                            fontWeight: 700,
                            color: '#111827',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                            lineHeight: 1.3,
                            letterSpacing: '-0.5px',
                            textShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
                        }}>
                            {intl.formatMessage({ id: 'app.title' })}
                        </div>
                        <div style={{
                            fontSize: '11px',
                            color: '#6b7280',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                            fontWeight: 500,
                            lineHeight: 1.3,
                            letterSpacing: '0.5px',
                            textTransform: 'uppercase',
                        }}>
                            Bank Notification System
                        </div>
                        <div style={{
                            position: 'absolute',
                            top: '50%',
                            left: 0,
                            transform: 'translateY(-50%)',
                            width: '2px',
                            height: '60%',
                            background: 'linear-gradient(180deg, transparent, #1677ff, transparent)',
                            opacity: 0.3,
                        }} />
                    </div>
                </div>

                <nav 
                    style={{
                        display: isMobile ? 'none' : 'flex',
                        alignItems: 'center',
                        gap: '4px',
                        flex: 1,
                        justifyContent: 'center',
                        padding: '0 16px',
                        maxWidth: '500px',
                        overflowX: 'auto',
                        whiteSpace: 'nowrap',
                    }}
                >
                    {NAV_ITEMS.map((item) => (
                        <button
                            key={item.key}
                            onClick={() => navigate(item.key)}
                            style={{
                                position: 'relative',
                                padding: '10px 20px',
                                borderRadius: '8px',
                                border: 'none',
                                background: currentPath === item.key 
                                    ? 'rgba(22, 119, 255, 0.08)' 
                                    : 'transparent',
                                color: currentPath === item.key ? '#1677ff' : '#4b5563',
                                fontSize: '14px',
                                fontWeight: currentPath === item.key ? 600 : 500,
                                cursor: 'pointer',
                                transition: 'all 0.25s ease',
                                fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '6px',
                            }}
                            onMouseEnter={(e) => {
                                e.currentTarget.style.background = currentPath === item.key 
                                    ? 'rgba(22, 119, 255, 0.12)' 
                                    : 'rgba(0, 0, 0, 0.04)';
                                e.currentTarget.style.color = '#1677ff';
                            }}
                            onMouseLeave={(e) => {
                                e.currentTarget.style.background = currentPath === item.key 
                                    ? 'rgba(22, 119, 255, 0.08)' 
                                    : 'transparent';
                                e.currentTarget.style.color = currentPath === item.key ? '#1677ff' : '#4b5563';
                            }}
                        >
                            {intl.formatMessage({ id: item.id })}
                            {currentPath === item.key && (
                                <span style={{
                                    position: 'absolute',
                                    bottom: 0,
                                    left: '50%',
                                    transform: 'translateX(-50%)',
                                    width: '24px',
                                    height: '2px',
                                    background: '#1677ff',
                                    borderRadius: '1px',
                                }} />
                            )}
                        </button>
                    ))}
                </nav>

                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                    flexShrink: 0,
                }}>
                    <Dropdown 
                        menu={{ 
                            items: localeMenuItems,
                            dropdownRender: (menu) => (
                                <div 
                                    style={{
                                        background: '#fff',
                                        borderRadius: '12px',
                                        padding: '8px',
                                        boxShadow: '0 10px 40px rgba(0, 0, 0, 0.1)',
                                        border: '1px solid rgba(0, 0, 0, 0.08)',
                                        animation: 'slideDown 0.2s ease',
                                    }}
                                >
                                    {menu}
                                </div>
                            ),
                        }} 
                        placement="bottomRight"
                    >
                        <div style={{
                            cursor: 'pointer',
                            padding: '8px 12px',
                            borderRadius: '8px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            transition: 'all 0.2s ease',
                            minWidth: '72px',
                            justifyContent: 'center',
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.background = '#f3f4f6'}
                        onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                        >
                            <span style={{ 
                                fontSize: '13px', 
                                color: '#374151', 
                                fontWeight: 500,
                                fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                            }}>
                                {getLocaleLabel(currentLocale)}
                            </span>
                            <DownOutlined style={{ fontSize: '12px', color: '#9ca3af' }} />
                        </div>
                    </Dropdown>

                    <Dropdown
                        open={isNotificationOpen}
                        onOpenChange={(open) => {
                            setIsNotificationOpen(open);
                            if (open) fetchUnreadCount();
                        }}
                        dropdownRender={() => (
                            <NotificationDropdown onClose={() => setIsNotificationOpen(false)} />
                        )}
                        placement="bottomRight"
                    >
                        <div style={{
                            position: 'relative',
                            width: '40px',
                            height: '40px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            borderRadius: '10px',
                            cursor: 'pointer',
                            transition: 'all 0.2s ease',
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.background = '#f3f4f6'}
                        onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                        >
                            <Badge count={unreadCount} size="small" offset={[2, -2]} overflowCount={99}>
                                <BellOutlined style={{ fontSize: '19px', color: '#4b5563' }} />
                            </Badge>
                        </div>
                    </Dropdown>

                    <Dropdown 
                        menu={{ 
                            items: userMenuItems,
                            dropdownRender: (menu) => (
                                <div 
                                    style={{
                                        background: '#fff',
                                        borderRadius: '12px',
                                        padding: '8px',
                                        boxShadow: '0 10px 40px rgba(0, 0, 0, 0.1)',
                                        border: '1px solid rgba(0, 0, 0, 0.08)',
                                        width: '180px',
                                        animation: 'slideDown 0.2s ease',
                                    }}
                                >
                                    {menu}
                                </div>
                            ),
                        }} 
                        placement="bottomRight"
                    >
                        <div style={{
                            cursor: 'pointer',
                            padding: '6px 10px',
                            borderRadius: '10px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '10px',
                            transition: 'all 0.2s ease',
                            border: '1px solid transparent',
                        }}
                        onMouseEnter={(e) => {
                            e.currentTarget.style.background = '#f9fafb';
                            e.currentTarget.style.borderColor = '#e5e7eb';
                        }}
                        onMouseLeave={(e) => {
                            e.currentTarget.style.background = 'transparent';
                            e.currentTarget.style.borderColor = 'transparent';
                        }}
                        >
                            <Avatar 
                                size={34}
                                icon={<UserOutlined />} 
                                style={{ 
                                    background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
                                    fontSize: '15px',
                                    boxShadow: '0 2px 8px rgba(22, 119, 255, 0.3)',
                                    border: '2px solid rgba(255, 255, 255, 0.9)',
                                    boxSizing: 'border-box',
                                }} 
                            />
                            {!isMobile && (
                                <div style={{ 
                                    textAlign: 'left', 
                                    minWidth: '60px', 
                                    maxWidth: '90px',
                                    overflow: 'hidden',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '2px',
                                }}>
                                    <div style={{ 
                                        fontSize: '13px', 
                                        fontWeight: 600, 
                                        color: '#1f2937',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap',
                                        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                                        lineHeight: '1.2',
                                    }}>
                                        {user?.name || intl.formatMessage({ id: 'common.admin' })}
                                    </div>
                                    <div style={{ 
                                        fontSize: '10px', 
                                        color: '#6b7280',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap',
                                        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                                        fontWeight: 500,
                                        lineHeight: '1.2',
                                    }}>
                                        {user?.role === 'ADMIN' 
                                            ? intl.formatMessage({ id: 'common.adminRole' }) 
                                            : intl.formatMessage({ id: 'common.userRole' })}
                                    </div>
                                </div>
                            )}
                            <CaretRightOutlined style={{ fontSize: '12px', color: '#9ca3af' }} />
                        </div>
                    </Dropdown>
                </div>
            </AntHeader>

            <div 
                style={{
                    position: 'fixed',
                    top: '64px',
                    left: 0,
                    right: 0,
                    background: '#fff',
                    boxShadow: '0 4px 20px rgba(0, 0, 0, 0.08)',
                    padding: '16px 24px',
                    zIndex: 998,
                    display: isMenuOpen ? 'block' : 'none',
                    animation: 'slideDown 0.25s ease',
                }}
            >
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px',
                }}>
                    {NAV_ITEMS.map((item) => (
                        <button
                            key={item.key}
                            onClick={() => {
                                navigate(item.key);
                                setIsMenuOpen(false);
                            }}
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '10px',
                                border: 'none',
                                background: currentPath === item.key 
                                    ? 'rgba(22, 119, 255, 0.1)' 
                                    : 'transparent',
                                color: currentPath === item.key ? '#1677ff' : '#374151',
                                fontSize: '15px',
                                fontWeight: currentPath === item.key ? 600 : 500,
                                cursor: 'pointer',
                                transition: 'all 0.2s ease',
                                textAlign: 'left',
                                fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(22, 119, 255, 0.08)'}
                        >
                            {intl.formatMessage({ id: item.id })}
                            {currentPath === item.key && (
                                <CaretRightOutlined style={{ fontSize: '16px' }} />
                            )}
                        </button>
                    ))}
                </div>
            </div>

            <style>{`
                @keyframes slideDown {
                    from {
                        opacity: 0;
                        transform: translateY(-10px);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0);
                    }
                }
                @keyframes pulse {
                    0%, 100% {
                        opacity: 1;
                    }
                    50% {
                        opacity: 0.5;
                    }
                }
            `}</style>
        </>
    );
}

import { useEffect } from 'react';
import { BellOutlined, CheckCircleOutlined, ClockCircleOutlined, WarningOutlined, XOutlined, SoundOutlined, MutedOutlined } from '@ant-design/icons';
import { message, Tooltip } from 'antd';
import { useIntl } from 'react-intl';
import { useNavigate } from 'react-router-dom';
import useNotificationStore from '../../store/useNotificationStore';
import { playNotifySound } from '../../utils/notifySound';
import { parseServerInstant } from '../../utils/formatters';
import { CHANNEL_COLORS } from '../../utils/constants';
import { enumLabel } from '../../utils/labels';

const STATUS_STYLE = {
  SENT: { icon: CheckCircleOutlined, color: '#10b981' },
  PENDING: { icon: ClockCircleOutlined, color: '#f59e0b' },
  FAILED: { icon: WarningOutlined, color: '#ef4444' },
};

const formatRelative = (intl, timestamp) => {
  const date = parseServerInstant(timestamp);
  if (!date) return '';
  const minutes = Math.floor((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return intl.formatMessage({ id: 'notification.justNow' });
  if (minutes < 60) return intl.formatMessage({ id: 'notification.minutesAgo' }, { count: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return intl.formatMessage({ id: 'notification.hoursAgo' }, { count: hours });
  const days = Math.floor(hours / 24);
  if (days < 7) return intl.formatMessage({ id: 'notification.daysAgo' }, { count: days });
  return date.toLocaleDateString(intl.locale);
};

export default function NotificationDropdown({ onClose }) {
  const intl = useIntl();
  const navigate = useNavigate();
  const { latest: notifications, latestLoading: loading, unreadCount, soundEnabled, toggleSound, fetchLatest, markRead, markAllRead } =
    useNotificationStore();

  useEffect(() => {
    fetchLatest();
  }, [fetchLatest]);

  const handleItemClick = async (notification) => {
    // 已读的条目也要能打开：以前 read=true 直接 return，那条目就成了死点击
    let opened = notification;
    if (!notification.read) {
      try {
        await markRead(notification.id);
        opened = { ...notification, read: true };
      } catch (e) {
        message.error(intl.formatMessage({ id: 'common.operationFailed' }));
      }
    }
    onClose?.();
    navigate('/notifications', { state: { focusNotification: opened } });
  };

  const handleToggleSound = () => {
    // 打开时立刻响一声：既让用户确认开关生效，也顺带满足浏览器的自动播放手势要求
    if (toggleSound()) playNotifySound();
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllRead();
      message.success(intl.formatMessage({ id: 'notification.markAllReadSuccess' }));
    } catch (e) {
      message.error(intl.formatMessage({ id: 'common.operationFailed' }));
    }
  };

  return (
    <div style={{
      background: '#fff',
      borderRadius: '12px',
      boxShadow: '0 10px 40px rgba(0, 0, 0, 0.1)',
      border: '1px solid rgba(0, 0, 0, 0.08)',
      width: '360px',
      maxHeight: '480px',
      display: 'flex',
      flexDirection: 'column',
      animation: 'slideDown 0.2s ease',
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '16px',
        borderBottom: '1px solid #f3f4f6',
      }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}>
          <BellOutlined style={{ fontSize: '16px', color: '#1677ff' }} />
          <span style={{
            fontSize: '15px',
            fontWeight: 600,
            color: '#1f2937',
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
          }}>
            {intl.formatMessage({ id: 'notification.center' })}
          </span>
          {unreadCount > 0 && (
            <span style={{
              background: '#ef4444',
              color: '#fff',
              fontSize: '11px',
              fontWeight: 600,
              padding: '2px 6px',
              borderRadius: '10px',
            }}>
              {unreadCount}
            </span>
          )}
        </div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}>
          <Tooltip title={intl.formatMessage({ id: soundEnabled ? 'notification.soundOn' : 'notification.soundOff' })}>
            <button
              onClick={handleToggleSound}
              aria-label={intl.formatMessage({ id: soundEnabled ? 'notification.soundOn' : 'notification.soundOff' })}
              style={{
                fontSize: '14px',
                color: soundEnabled ? '#1677ff' : '#9ca3af',
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: '4px 6px',
                borderRadius: '6px',
                transition: 'all 0.2s ease',
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = '#f3f4f6'}
              onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
            >
              {soundEnabled ? <SoundOutlined /> : <MutedOutlined />}
            </button>
          </Tooltip>
          <button
            onClick={handleMarkAllRead}
            style={{
              fontSize: '12px',
              color: '#1677ff',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: '4px 8px',
              borderRadius: '6px',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(22, 119, 255, 0.1)'}
            onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
          >
            {intl.formatMessage({ id: 'notification.markAllRead' })}
          </button>
          <button
            onClick={onClose}
            style={{
              fontSize: '14px',
              color: '#9ca3af',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: '4px',
              borderRadius: '6px',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={(e) => e.currentTarget.style.background = '#f3f4f6'}
          >
            <XOutlined />
          </button>
        </div>
      </div>

      <div style={{
        flex: 1,
        overflowY: 'auto',
        padding: '8px',
      }}>
        {loading ? (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '40px',
          }}>
            <div style={{
              width: '20px',
              height: '20px',
              border: '2px solid #e5e7eb',
              borderTopColor: '#1677ff',
              borderRadius: '50%',
              animation: 'spin 0.8s linear infinite',
            }} />
          </div>
        ) : notifications.length === 0 ? (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '40px 20px',
            color: '#9ca3af',
          }}>
            <BellOutlined style={{ fontSize: '40px', marginBottom: '12px', opacity: 0.5 }} />
            <span style={{ fontSize: '14px' }}>{intl.formatMessage({ id: 'notification.empty' })}</span>
            <span style={{ fontSize: '12px', marginTop: '4px' }}>{intl.formatMessage({ id: 'notification.emptyDesc' })}</span>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {notifications.map((notification) => {
              const typeInfo = STATUS_STYLE[notification.status] || STATUS_STYLE.PENDING;
              const TypeIcon = typeInfo.icon;

              return (
                <div
                  key={notification.id}
                  onClick={() => handleItemClick(notification)}
                  style={{
                    padding: '12px',
                    borderRadius: '10px',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    background: notification.read ? 'transparent' : '#fef3c7',
                    border: notification.read ? 'none' : '1px solid #fde68a',
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.background = notification.read ? '#f9fafb' : '#fde68a'}
                  onMouseLeave={(e) => e.currentTarget.style.background = notification.read ? 'transparent' : '#fef3c7'}
                >
                  <div style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '10px',
                  }}>
                    <div style={{
                      width: '36px',
                      height: '36px',
                      borderRadius: '10px',
                      background: `${typeInfo.color}15`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}>
                      <TypeIcon style={{ fontSize: '16px', color: typeInfo.color }} />
                    </div>
                    <div style={{
                      flex: 1,
                      minWidth: 0,
                    }}>
                      <div style={{
                        fontSize: '13px',
                        fontWeight: 500,
                        color: '#1f2937',
                        marginBottom: '4px',
                        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                      }}>
                        {enumLabel(intl, 'eventType', notification.eventType)}
                        {/* 一个事件按渠道各落一行（(event_id, customer_id, channel) 唯一），
                            不标渠道时两条看起来像重复推送 */}
                        <span style={{
                          marginLeft: '8px',
                          fontSize: '11px',
                          fontWeight: 400,
                          padding: '1px 6px',
                          borderRadius: '6px',
                          background: CHANNEL_COLORS[notification.channel]?.bg || '#f1f5f9',
                          color: '#475569',
                        }}>
                          {enumLabel(intl, 'channel', notification.channel)}
                        </span>
                      </div>
                      <div style={{
                        fontSize: '12px',
                        color: '#6b7280',
                        marginBottom: '6px',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}>
                        {notification.content}
                      </div>
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px',
                      }}>
                        <span style={{
                          fontSize: '11px',
                          color: typeInfo.color,
                          fontWeight: 500,
                        }}>
                          {enumLabel(intl, 'status', notification.status)}
                        </span>
                        <span style={{
                          fontSize: '11px',
                          color: '#9ca3af',
                        }}>
                          {formatRelative(intl, notification.createdAt)}
                        </span>
                      </div>
                    </div>
                    {!notification.read && (
                      <div style={{
                        width: '6px',
                        height: '6px',
                        background: '#ef4444',
                        borderRadius: '50%',
                        marginTop: '8px',
                      }} />
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {!loading && notifications.length > 0 && (
        <div style={{
          padding: '12px 16px',
          borderTop: '1px solid #f3f4f6',
          textAlign: 'center',
        }}>
          <button
            style={{
              fontSize: '13px',
              color: '#1677ff',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: '6px 12px',
              borderRadius: '8px',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(22, 119, 255, 0.1)'}
            onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
            onClick={() => {
              navigate('/notifications');
              onClose();
            }}
          >
            {intl.formatMessage({ id: 'notification.viewAll' })}
          </button>
        </div>
      )}
    </div>
  );
}

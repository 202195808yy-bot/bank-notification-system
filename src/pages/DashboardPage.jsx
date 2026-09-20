import React, { useEffect, useState, useCallback } from 'react';
import { Row, Col, Card, Statistic, Spin, Button, Space, Typography, Progress, Divider, Alert, Segmented } from 'antd';
import { ReloadOutlined, PlayCircleOutlined, PauseCircleOutlined, ClockCircleOutlined, BellOutlined, CheckCircleOutlined, ExclamationCircleOutlined, SyncOutlined, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import useNotificationStore from '../store/useNotificationStore';
import useAuthStore from '../store/useAuthStore';
import { useIntl } from 'react-intl';

const { Title, Text } = Typography;

export default function DashboardPage() {
    const intl = useIntl();
    const { stats, fetchStats, loading } = useNotificationStore();
    const isAdmin = useAuthStore((state) => state.user?.role) === 'ADMIN';
    // 全行统计只对 ADMIN 开放，普通用户固定看自己的数据
    const [scope, setScope] = useState('mine');
    const [autoRefresh, setAutoRefresh] = useState(false);
    const [refreshInterval, setRefreshInterval] = useState(10);
    const [lastUpdate, setLastUpdate] = useState(null);
    const [isAutoRefreshing, setIsAutoRefreshing] = useState(false);

    const handleFetchStats = useCallback(() => {
        setIsAutoRefreshing(true);
        fetchStats(scope).finally(() => {
            setIsAutoRefreshing(false);
            setLastUpdate(new Date());
        });
    }, [fetchStats, scope]);

    useEffect(() => {
        handleFetchStats();
    }, []);

    useEffect(() => {
        let interval;
        if (autoRefresh) {
            interval = setInterval(() => {
                handleFetchStats();
            }, refreshInterval * 1000);
        }
        return () => {
            if (interval) clearInterval(interval);
        };
    }, [autoRefresh, refreshInterval, handleFetchStats]);

    useEffect(() => {
        const handleEventSent = () => {
            setTimeout(() => {
                handleFetchStats();
            }, 500);
        };
        const handleFocus = () => handleFetchStats();

        window.addEventListener('bank-event-sent', handleEventSent);
        window.addEventListener('focus', handleFocus);

        return () => {
            window.removeEventListener('bank-event-sent', handleEventSent);
            window.removeEventListener('focus', handleFocus);
        };
    }, [handleFetchStats]);

    const toggleAutoRefresh = () => {
        setAutoRefresh(!autoRefresh);
        if (!autoRefresh) {
            setLastUpdate(new Date());
        }
    };

    const handleIntervalChange = (value) => {
        setRefreshInterval(value || 10);
    };

    const total = stats?.total || 0;
    const pending = stats?.pending || 0;
    const sent = stats?.sent || 0;
    const failed = stats?.failed || 0;
    const skipped = stats?.skipped || 0;
    const failedValidation = stats?.['failed_validation'] || 0;
    // 成功率只按真正尝试投递的记录算：SKIPPED（静音/无模板）不是投递失败，不该拉低比率
    const attempted = Math.max(total - skipped, 0);
    const successRate = attempted > 0 ? Math.round((sent / attempted) * 100) : 0;

    const distribution = [
        { key: 'PENDING', value: pending, gradient: 'linear-gradient(180deg, #faad14, #ffc53d)', color: '#faad14', labelId: 'dashboard.pending' },
        { key: 'SENT', value: sent, gradient: 'linear-gradient(180deg, #52c41a, #73d13d)', color: '#52c41a', labelId: 'dashboard.sent' },
        { key: 'FAILED', value: failed, gradient: 'linear-gradient(180deg, #ff4d4f, #ff7875)', color: '#ff4d4f', labelId: 'dashboard.failed' },
        { key: 'FAILED_VALIDATION', value: failedValidation, gradient: 'linear-gradient(180deg, #d46b08, #ffa940)', color: '#d46b08', labelId: 'enum.status.FAILED_VALIDATION' },
        { key: 'SKIPPED', value: skipped, gradient: 'linear-gradient(180deg, #64748b, #94a3b8)', color: '#64748b', labelId: 'enum.status.SKIPPED' },
    ];

    if (loading && !stats) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px', background: '#f5f5f5' }}>
                <Spin size="large" tip={intl.formatMessage({ id: 'common.loading' })} />
            </div>
        );
    }

    return (
        <div style={{ animation: 'fadeIn 0.3s ease' }}>
            <div style={{
                background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
                borderRadius: '12px',
                marginBottom: '24px',
                padding: '24px',
                color: '#fff',
                boxShadow: '0 4px 16px rgba(22, 119, 255, 0.2)',
                overflow: 'hidden',
            }}>
                <Row justify="space-between" align="middle">
                    <Col style={{ overflow: 'hidden' }}>
                        <Space>
                            <div style={{ width: '50px', height: '50px', borderRadius: '12px', background: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                <BellOutlined style={{ fontSize: '24px' }} />
                            </div>
                            <div style={{ maxWidth: '280px', minWidth: '150px', flex: 1, overflow: 'hidden' }}>
                                <Title level={2} style={{ 
                                    margin: 0, 
                                    fontWeight: 600, 
                                    fontSize: '20px',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                }}>
                                    {intl.formatMessage({ id: 'dashboard.title' })}
                                </Title>
                                <Text style={{ 
                                    color: 'rgba(255,255,255,0.85)', 
                                    marginTop: '4px', 
                                    display: 'block', 
                                    fontSize: '13px',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                }}>
                                    {intl.formatMessage({ id: 'dashboard.subtitle' })}
                                </Text>
                            </div>
                        </Space>
                    </Col>
                    <Col style={{ overflow: 'hidden', display: 'flex', alignItems: 'center' }}>
                        <Space size="middle" style={{ flexWrap: 'wrap', gap: '8px' }}>
                            {lastUpdate && (
                                <div style={{ 
                                    display: 'flex', 
                                    alignItems: 'center', 
                                    gap: '6px', 
                                    color: '#fff', 
                                    fontSize: '12px',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    maxWidth: '200px',
                                }}>
                                    <ClockCircleOutlined style={{ fontSize: '12px', flexShrink: 0 }} />
                                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{intl.formatMessage({ id: 'dashboard.lastUpdate' })} {lastUpdate.toLocaleTimeString()}</span>
                                </div>
                            )}
                            <Button
                                type="primary"
                                ghost
                                icon={autoRefresh ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                                onClick={toggleAutoRefresh}
                                style={{ borderRadius: '8px', padding: '8px 16px', fontWeight: 500, background: 'rgba(255,255,255,0.15)', borderColor: 'rgba(255,255,255,0.3)', color: '#fff', flexShrink: 0 }}
                            >
                                {autoRefresh ? intl.formatMessage({ id: 'dashboard.pauseRefresh' }) : intl.formatMessage({ id: 'dashboard.autoRefresh' })}
                            </Button>
                            <Button
                                type="primary"
                                ghost
                                icon={<ReloadOutlined spin={isAutoRefreshing} />}
                                onClick={handleFetchStats}
                                loading={isAutoRefreshing}
                                style={{ borderRadius: '8px', padding: '8px 16px', fontWeight: 500, background: 'rgba(255,255,255,0.15)', borderColor: 'rgba(255,255,255,0.3)', color: '#fff', flexShrink: 0 }}
                            >
                                {intl.formatMessage({ id: 'dashboard.refresh' })}
                            </Button>
                            {isAdmin && (
                                <Segmented
                                    value={scope}
                                    onChange={(value) => {
                                        setScope(value);
                                        fetchStats(value);
                                    }}
                                    options={[
                                        { label: intl.formatMessage({ id: 'dashboard.scopeMine' }), value: 'mine' },
                                        { label: intl.formatMessage({ id: 'dashboard.scopeAll' }), value: 'all' },
                                    ]}
                                    style={{ flexShrink: 0 }}
                                />
                            )}
                        </Space>
                    </Col>
                </Row>
            </div>

            {autoRefresh && (
                <Card style={{ marginBottom: '24px', background: '#e6f4ff', borderRadius: '12px', border: '1px solid #91caff' }}>
                    <Row justify="space-between" align="middle">
                        <Col>
                            <Space>
                                <SyncOutlined spin style={{ color: '#1677ff' }} />
                                <Text style={{ color: 'rgba(0, 0, 0, 0.88)', fontWeight: 500 }}>
                                    {intl.formatMessage({ id: 'dashboard.autoRefreshStarted' }, { interval: refreshInterval })}
                                </Text>
                            </Space>
                        </Col>
                        <Col>
                            <Button.Group>
                                {[5, 10, 30, 60].map(sec => (
                                    <Button
                                        key={sec}
                                        type={refreshInterval === sec ? 'primary' : 'default'}
                                        onClick={() => handleIntervalChange(sec)}
                                        size="small"
                                        style={{ borderRadius: '6px', fontWeight: 500 }}
                                    >
                                        {sec}s
                                    </Button>
                                ))}
                            </Button.Group>
                        </Col>
                    </Row>
                </Card>
            )}

            <Row gutter={[16, 16]} style={{ marginBottom: '24px' }}>
                <Col xs={24} sm={12} md={6}>
                    <Card 
                        hoverable
                        bodyStyle={{ padding: '20px' }}
                        style={{ borderRadius: '12px', border: '1px solid #f0f0f0' }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                            <div style={{ width: '44px', height: '44px', borderRadius: '12px', background: '#e6f7ff', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '12px' }}>
                                <BellOutlined style={{ color: '#1677ff', fontSize: '22px' }} />
                            </div>
                            <div>
                                <Text style={{ color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px', fontWeight: 500, display: 'block' }}>
                                    {intl.formatMessage({ id: 'dashboard.totalNotifications' })}
                                </Text>
                                <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginTop: '4px' }}>
                                    <Text style={{ fontSize: '32px', fontWeight: 700, color: 'rgba(0, 0, 0, 0.88)' }}>
                                        {total.toLocaleString()}
                                    </Text>
                                    <ArrowUpOutlined style={{ fontSize: '14px', color: '#52c41a' }} />
                                </div>
                            </div>
                        </div>
                        <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #f0f0f0' }}>
                            <Text style={{ color: 'rgba(0, 0, 0, 0.45)', fontSize: '12px' }}>
                                {intl.formatMessage({ id: scope === 'all' ? 'dashboard.totalDesc' : 'dashboard.scopeMine' })}
                            </Text>
                        </div>
                    </Card>
                </Col>

                <Col xs={24} sm={12} md={6}>
                    <Card 
                        hoverable
                        bodyStyle={{ padding: '20px' }}
                        style={{ borderRadius: '12px', border: '1px solid #f0f0f0' }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                            <div style={{ width: '44px', height: '44px', borderRadius: '12px', background: '#fffbe6', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '12px' }}>
                                <SyncOutlined style={{ color: '#faad14', fontSize: '22px' }} />
                            </div>
                            <div>
                                <Text style={{ color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px', fontWeight: 500, display: 'block' }}>
                                    {intl.formatMessage({ id: 'dashboard.pending' })}
                                </Text>
                                <Text style={{ fontSize: '32px', fontWeight: 700, color: '#faad14', marginTop: '4px', display: 'block' }}>
                                    {pending.toLocaleString()}
                                </Text>
                            </div>
                        </div>
                        <div style={{ marginTop: '12px' }}>
                            <Progress percent={Math.round((pending / Math.max(total, 1)) * 100)} strokeColor="#faad14" showInfo={false} strokeWidth={6} />
                            <Text style={{ color: 'rgba(0, 0, 0, 0.45)', fontSize: '12px', marginTop: '6px', display: 'block' }}>
                                {intl.formatMessage({ id: 'dashboard.pendingPercent' }, { percent: Math.round((pending / Math.max(total, 1)) * 100) })}
                            </Text>
                        </div>
                    </Card>
                </Col>

                <Col xs={24} sm={12} md={6}>
                    <Card 
                        hoverable
                        bodyStyle={{ padding: '20px' }}
                        style={{ borderRadius: '12px', border: '1px solid #f0f0f0' }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                            <div style={{ width: '44px', height: '44px', borderRadius: '12px', background: '#f6ffed', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '12px' }}>
                                <CheckCircleOutlined style={{ color: '#52c41a', fontSize: '22px' }} />
                            </div>
                            <div>
                                <Text style={{ color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px', fontWeight: 500, display: 'block' }}>
                                    {intl.formatMessage({ id: 'dashboard.sent' })}
                                </Text>
                                <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginTop: '4px' }}>
                                    <Text style={{ fontSize: '32px', fontWeight: 700, color: '#52c41a' }}>
                                        {sent.toLocaleString()}
                                    </Text>
                                    <Text style={{ fontSize: '14px', color: '#52c41a', fontWeight: 500 }}>
                                        ({successRate}%)
                                    </Text>
                                </div>
                            </div>
                        </div>
                        <div style={{ marginTop: '12px' }}>
                            <Progress percent={successRate} strokeColor="#52c41a" showInfo={false} strokeWidth={6} />
                        </div>
                    </Card>
                </Col>

                <Col xs={24} sm={12} md={6}>
                    <Card 
                        hoverable
                        bodyStyle={{ padding: '20px' }}
                        style={{ borderRadius: '12px', border: '1px solid #f0f0f0' }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                            <div style={{ width: '44px', height: '44px', borderRadius: '12px', background: '#fff2f0', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '12px' }}>
                                <ExclamationCircleOutlined style={{ color: '#ff4d4f', fontSize: '22px' }} />
                            </div>
                            <div>
                                <Text style={{ color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px', fontWeight: 500, display: 'block' }}>
                                    {intl.formatMessage({ id: 'dashboard.failed' })}
                                </Text>
                                <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginTop: '4px' }}>
                                    <Text style={{ fontSize: '32px', fontWeight: 700, color: '#ff4d4f' }}>
                                        {failed.toLocaleString()}
                                    </Text>
                                    {failed > 0 && (
                                        <ArrowDownOutlined style={{ fontSize: '14px', color: '#ff4d4f' }} />
                                    )}
                                </div>
                            </div>
                        </div>
                        {failed > 0 && (
                            <Alert 
                                type="error" 
                                message={intl.formatMessage({ id: 'dashboard.failedAction' })} 
                                style={{ marginTop: '12px', fontSize: '12px', background: '#fff2f0', borderColor: '#ffccc7' }}
                                showIcon
                            />
                        )}
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col xs={24} md={16}>
                    <Card style={{ borderRadius: '12px', border: '1px solid #f0f0f0' }} title={intl.formatMessage({ id: 'dashboard.distribution' })}>
                        <div style={{ display: 'flex', justifyContent: 'space-around', alignItems: 'flex-end', height: '200px', padding: '16px 0' }}>
                            {distribution.map((item) => (
                                <div key={item.key} style={{ textAlign: 'center', flex: 1 }}>
                                    <div style={{
                                        width: '48px',
                                        margin: '0 auto',
                                        height: `${Math.max(30, (item.value / Math.max(attempted, 1)) * 150)}px`,
                                        background: item.gradient,
                                        borderRadius: '8px 8px 0 0',
                                        transition: 'height 0.8s ease',
                                    }} />
                                    <div style={{ marginTop: '12px' }}>
                                        <Text strong style={{ display: 'block', marginTop: '8px', color: 'rgba(0, 0, 0, 0.88)', fontWeight: 600, fontSize: '13px' }}>
                                            {intl.formatMessage({ id: item.labelId })}
                                        </Text>
                                        <Text style={{ fontSize: '18px', fontWeight: 700, color: item.color, display: 'block', marginTop: '4px' }}>
                                            {item.value}
                                        </Text>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Card>
                </Col>

                <Col xs={24} md={8}>
                    <Card style={{ borderRadius: '12px', border: '1px solid #f0f0f0' }} title={intl.formatMessage({ id: 'dashboard.successRate' })}>
                        <div style={{ textAlign: 'center', padding: '16px 0' }}>
                            <Progress
                                type="circle"
                                percent={successRate}
                                strokeColor={{
                                    '0%': '#52c41a',
                                    '100%': '#1677ff',
                                }}
                                strokeWidth={10}
                                size={120}
                                format={(percent) => (
                                    <div>
                                        <Text style={{ fontSize: '32px', fontWeight: 700, color: 'rgba(0, 0, 0, 0.88)' }}>
                                            {percent}%
                                        </Text>
                                        <Text style={{ display: 'block', fontSize: '12px', color: 'rgba(0, 0, 0, 0.45)', marginTop: '4px' }}>
                                            {intl.formatMessage({ id: 'dashboard.successRate' })}
                                        </Text>
                                    </div>
                                )}
                            />
                            <Divider style={{ margin: '16px 0' }} />
                            <Row gutter={12}>
                                <Col span={12}>
                                    <Statistic
                                        title={intl.formatMessage({ id: 'dashboard.sentSuccess' })}
                                        value={sent}
                                        valueStyle={{ color: '#52c41a', fontSize: '18px', fontWeight: 700 }}
                                    />
                                </Col>
                                <Col span={12}>
                                    <Statistic
                                        title={intl.formatMessage({ id: 'dashboard.sentFailed' })}
                                        value={failed}
                                        valueStyle={{ color: '#ff4d4f', fontSize: '18px', fontWeight: 700 }}
                                    />
                                </Col>
                            </Row>
                        </div>
                    </Card>
                </Col>
            </Row>

            <Card style={{ marginTop: '24px', background: '#f0f5ff', borderRadius: '12px', border: '1px solid #dbeafe' }}>
                <Title level={4} style={{ color: '#1677ff', marginBottom: '16px', fontSize: '16px' }}>
                    {intl.formatMessage({ id: 'dashboard.systemFeatures' })}
                </Title>
                <Row gutter={[12, 10]}>
                    {[
                        { title: 'dashboard.realTimeMonitoring', icon: '📊' },
                        { title: 'dashboard.multiChannel', icon: '🔔' },
                        { title: 'dashboard.quickSend', icon: '⚡' },
                        { title: 'dashboard.dataSecurity', icon: '🔒' },
                        { title: 'dashboard.i18n', icon: '🌐' },
                        { title: 'dashboard.responsive', icon: '📱' },
                    ].map((feature, index) => (
                        <Col key={index} xs={24} sm={12} md={8}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', background: '#ffffff', borderRadius: '10px', border: '1px solid #f0f0f0' }}>
                                <span style={{ fontSize: '18px' }}>{feature.icon}</span>
                                <Text style={{ color: 'rgba(0, 0, 0, 0.88)', fontSize: '13px' }}>
                                    {intl.formatMessage({ id: feature.title })}
                                </Text>
                            </div>
                        </Col>
                    ))}
                </Row>
            </Card>
        </div>
    );
}
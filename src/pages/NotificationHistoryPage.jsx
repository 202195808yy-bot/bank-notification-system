import { useEffect, useState } from 'react';
import { Table, Select, Space, Tag, Button, DatePicker, Skeleton, message, Tooltip, Card, Typography, Row, Col, Statistic, Divider, Modal, Descriptions, Alert } from 'antd';
import { ReloadOutlined, SearchOutlined, FilterOutlined, ClearOutlined, EyeOutlined, ClockCircleOutlined, BellOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import { useLocation, useNavigate } from 'react-router-dom';
import useNotificationStore from '../store/useNotificationStore';
import useAuthStore from '../store/useAuthStore';
import { EVENT_TYPES, CHANNELS, STATUS_MAP, EVENT_TYPE_COLORS, EVENT_TYPE_ICONS, CHANNEL_COLORS, STATUS_DETAILS, MODERN_THEME } from '../utils/constants';
import { formatDateTime } from '../utils/formatters';
import { enumLabel } from '../utils/labels';
import { listCustomers } from '../api/customerApi';
import axios from '../api/axiosInstance';

const { Option } = Select;
const { RangePicker } = DatePicker;
const { Title, Text } = Typography;

const styles = {
    pageContainer: {
        padding: '24px',
        background: MODERN_THEME.colors.background,
        minHeight: '100vh',
    },
    headerCard: {
        background: MODERN_THEME.colors.cardBg,
        borderRadius: MODERN_THEME.borderRadius.card,
        boxShadow: MODERN_THEME.shadows.card,
        marginBottom: '24px',
        padding: '20px 24px',
    },
    filterCard: {
        background: MODERN_THEME.colors.cardBg,
        borderRadius: MODERN_THEME.borderRadius.card,
        boxShadow: MODERN_THEME.shadows.card,
        marginBottom: '24px',
        border: `1px solid ${MODERN_THEME.colors.border}`,
    },
    statsCard: {
        background: MODERN_THEME.colors.cardBg,
        borderRadius: MODERN_THEME.borderRadius.card,
        boxShadow: MODERN_THEME.shadows.card,
        marginBottom: '24px',
    },
    tableCard: {
        background: MODERN_THEME.colors.cardBg,
        borderRadius: MODERN_THEME.borderRadius.card,
        boxShadow: MODERN_THEME.shadows.card,
    },
    eventTypeTag: (type) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        padding: '4px 12px',
        borderRadius: MODERN_THEME.borderRadius.tag,
        background: EVENT_TYPE_COLORS[type]?.bg || '#f0f0f0',
        border: `1px solid ${EVENT_TYPE_COLORS[type]?.border || '#d9d9d9'}`,
        color: EVENT_TYPE_COLORS[type]?.text || '#666',
        fontWeight: 500,
        fontSize: '13px',
    }),
    channelTag: (channel) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        padding: '4px 10px',
        borderRadius: MODERN_THEME.borderRadius.tag,
        background: CHANNEL_COLORS[channel]?.bg || '#f0f0f0',
    }),
    statusBadge: (status) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: '6px 12px',
        borderRadius: '8px',
        background: STATUS_DETAILS[status]?.bg || '#f0f0f0',
        color: STATUS_DETAILS[status]?.color || '#666',
        fontWeight: 600,
    }),
    filterButton: {
        borderRadius: MODERN_THEME.borderRadius.button,
        boxShadow: MODERN_THEME.shadows.button,
        transition: MODERN_THEME.transitions.normal,
    },
};

export default function NotificationHistoryPage() {
    const intl = useIntl();
    const location = useLocation();
    const navigate = useNavigate();
    const {
        notifications,
        loading,
        listError,
        pagination,
        fetchNotifications,
        setFilters,
    } = useNotificationStore();

    // 重投是运维动作：后端已限制为 ADMIN，前端据此隐藏按钮（普通用户看不到必然 403 的操作）
    const isAdmin = useAuthStore((state) => state.user?.role) === 'ADMIN';

    const [selectedFilters, setSelectedFilters] = useState({
        eventType: null,
        channel: null,
        status: null,
        startDate: null,
        endDate: null,
    });

    // 详情弹窗：以前「查看」按钮没有 onClick，是个死操作
    const [detail, setDetail] = useState(null);

    // customerId → 可读标识。历史页此前只显示裸 #id，管理员既不知道"这是谁"，
    // 也看不出某行是否指向已删除的客户（PRD-33 的孤儿行）。
    const [customerDirectory, setCustomerDirectory] = useState({});

    useEffect(() => {
        if (!isAdmin) return undefined;
        let cancelled = false;
        listCustomers()
            .then((data) => {
                if (cancelled) return;
                setCustomerDirectory(Object.fromEntries((data || []).map((c) => [c.id, c])));
            })
            .catch(() => {
                if (!cancelled) setCustomerDirectory({});
            });
        return () => { cancelled = true; };
    }, [isAdmin]);

    // 铃铛下拉点某条通知时会把记录经路由 state 传过来，落地后立刻清掉，避免刷新又弹出
    useEffect(() => {
        const focused = location.state?.focusNotification;
        if (!focused) return;
        setDetail(focused);
        navigate(location.pathname, { replace: true, state: null });
    }, [location.state]);

    useEffect(() => {
        fetchNotifications(0, 10);
    }, []);

    useEffect(() => {
        const hasFilters = Object.values(selectedFilters).some(v => v !== null && v !== undefined);
        if (hasFilters) {
            setFilters(selectedFilters);
            fetchNotifications(0, 10);
        } else {
            setFilters({});
        }
    }, [selectedFilters]);

    const handleFilterChange = (key, value) => {
        setSelectedFilters(prev => ({ ...prev, [key]: value || null }));
    };

    const handleReset = () => {
        setSelectedFilters({
            eventType: null,
            channel: null,
            status: null,
            startDate: null,
            endDate: null,
        });
        setFilters({});
        fetchNotifications(0, 10);
    };

    const handleView = (record) => setDetail(record);

    const handleRetry = async (id) => {
        try {
            await axios.post('/notifications/' + id + '/retry');
            message.success(intl.formatMessage({ id: 'history.retrySuccess' }));
            fetchNotifications(pagination.page, pagination.size);
        } catch (e) {
            message.error(intl.formatMessage({ id: 'history.retryFailed' }));
        }
    };

    const eventTypeStats = notifications.reduce((acc, n) => {
        const type = n.eventType || n.event_type;
        acc[type] = (acc[type] || 0) + 1;
        return acc;
    }, {});

    const customerIdentity = (id) => {
        const found = customerDirectory[id];
        return {
            known: !!found,
            primary: found ? (found.name || found.email || `#${id}`) : `#${id}`,
            secondary: found?.email || '',
        };
    };

    const columns = [
        {
            title: intl.formatMessage({ id: 'history.id' }),
            dataIndex: 'id',
            width: 70,
            render: (v) => <Text strong style={{ color: MODERN_THEME.colors.textSecondary }}>{v}</Text>,
        },
        {
            title: intl.formatMessage({ id: 'history.customer' }),
            dataIndex: 'customerId',
            width: 180,
            // 管理员看的是全行记录，客户必须可识别；普通用户只会被过滤出自己的数据
            hidden: !isAdmin,
            render: (v) => {
                const who = customerIdentity(v);
                if (!who.known) {
                    // notifications 对 customers 没有外键，删客户会留下这种孤儿行
                    return (
                        <Space size={6}>
                            <Text strong style={{ color: MODERN_THEME.colors.textSecondary }}>{who.primary}</Text>
                            <Tooltip title={intl.formatMessage({ id: 'history.customerDeletedTip' })}>
                                <Tag color="orange" style={{ margin: 0 }}>
                                    {intl.formatMessage({ id: 'history.customerDeleted' })}
                                </Tag>
                            </Tooltip>
                        </Space>
                    );
                }
                return (
                    <Space direction="vertical" size={0}>
                        <Text strong style={{ color: MODERN_THEME.colors.textPrimary }}>{who.primary}</Text>
                        {who.secondary && (
                            <Text style={{ color: MODERN_THEME.colors.textTertiary, fontSize: '11px' }}>
                                {who.secondary}
                            </Text>
                        )}
                    </Space>
                );
            },
        },
        {
            title: intl.formatMessage({ id: 'history.eventType' }),
            dataIndex: 'eventType',
            width: 180,
            render: (v, record) => {
                const eventType = v || record.event_type;
                const icon = EVENT_TYPE_ICONS[eventType] || '📌';
                return (
                    <span style={styles.eventTypeTag(eventType)}>
                        <span>{icon}</span>
                        <span>{enumLabel(intl, 'eventType', eventType)}</span>
                    </span>
                );
            },
        },
        {
            title: intl.formatMessage({ id: 'history.channel' }),
            dataIndex: 'channel',
            width: 120,
            render: (v) => (
                <span style={styles.channelTag(v)}>
                    <span>{CHANNEL_COLORS[v]?.icon || '📡'}</span>
                    <Tag color={CHANNEL_COLORS[v]?.tag || 'default'} style={{ margin: 0, borderRadius: '4px' }}>
                        {enumLabel(intl, 'channel', v)}
                    </Tag>
                </span>
            ),
        },
        {
            title: intl.formatMessage({ id: 'history.status' }),
            dataIndex: 'status',
            width: 130,
            render: (v) => (
                <span style={styles.statusBadge(v)}>
                    <span>{STATUS_DETAILS[v]?.icon || '•'}</span>
                    <span>{enumLabel(intl, 'status', v)}</span>
                </span>
            ),
        },
        {
            title: intl.formatMessage({ id: 'history.reason' }),
            dataIndex: 'reason',
            width: 160,
            render: (v) => (v ? (
                <Tooltip title={enumLabel(intl, 'reason', v)}>
                    <Text style={{ color: MODERN_THEME.colors.textSecondary, fontSize: '12px' }}>
                        {enumLabel(intl, 'reason', v)}
                    </Text>
                </Tooltip>
            ) : (
                <Text style={{ color: MODERN_THEME.colors.textTertiary, fontSize: '12px' }}>-</Text>
            )),
        },
        {
            title: intl.formatMessage({ id: 'history.content' }),
            dataIndex: 'content',
            ellipsis: true,
            render: (text) => text ? (
                <Tooltip title={text} placement="topLeft">
                    <Text style={{ color: MODERN_THEME.colors.textPrimary, fontSize: '13px' }}>
                        {text.substring(0, 60)}{text.length > 60 ? '...' : ''}
                    </Text>
                </Tooltip>
            ) : (
                // SKIPPED / FAILED_VALIDATION 的记录从未渲染过正文，这里不该显示成省略号
                <Text style={{ color: MODERN_THEME.colors.textTertiary, fontSize: '12px' }}>
                    {intl.formatMessage({ id: 'history.noContent' })}
                </Text>
            ),
        },
        {
            title: intl.formatMessage({ id: 'history.time' }),
            dataIndex: 'createdAt',
            width: 160,
            render: (v) => (
                <Space size="small">
                    <ClockCircleOutlined style={{ color: MODERN_THEME.colors.textSecondary }} />
                    <Text style={{ color: MODERN_THEME.colors.textSecondary, fontSize: '12px' }}>
                        {formatDateTime(v, intl.locale)}
                    </Text>
                </Space>
            ),
            sorter: (a, b) => new Date(a.createdAt) - new Date(b.createdAt),
            defaultSortOrder: 'descend',
        },
        {
            title: intl.formatMessage({ id: 'history.action' }),
            width: 150,
            fixed: 'right',
            render: (_, record) => (
                <Space size={0}>
                    <Button
                        type="link"
                        size="small"
                        icon={<EyeOutlined />}
                        onClick={() => handleView(record)}
                        style={{ color: MODERN_THEME.colors.primary }}
                    >
                        {intl.formatMessage({ id: 'history.view' })}
                    </Button>
                    {/* 重投是管理员专属，且只有真正发送失败的记录才能重投 */}
                    {isAdmin && record.status === 'FAILED' && (
                        <Button
                            type="link"
                            size="small"
                            onClick={() => handleRetry(record.id)}
                            icon={<ExclamationCircleOutlined />}
                            style={{ color: MODERN_THEME.colors.error }}
                        >
                            {intl.formatMessage({ id: 'history.retry' })}
                        </Button>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <div style={styles.pageContainer}>
            <Card style={styles.headerCard}>
                <Row justify="space-between" align="middle">
                    <Col>
                        <Space>
                            <BellOutlined style={{ fontSize: '28px', color: MODERN_THEME.colors.primary }} />
                            <Title level={3} style={{ margin: 0, color: MODERN_THEME.colors.textPrimary }}>
                                {intl.formatMessage({ id: 'history.title' })}
                            </Title>
                        </Space>
                        <Text style={{ color: MODERN_THEME.colors.textSecondary, marginTop: '8px', display: 'block' }}>
                            {intl.formatMessage({ id: 'history.subtitle' })}
                        </Text>
                    </Col>
                    <Col>
                        {/* 纯展示，不是按钮：Badge 的 count 会把 487 显示成 99+，所以直接出数字 */}
                        <Space align="center">
                            <BellOutlined style={{ fontSize: '18px', color: MODERN_THEME.colors.primary }} />
                            <Statistic
                                title={intl.formatMessage({ id: 'history.totalRecords' })}
                                value={pagination.total}
                                valueStyle={{ fontSize: '20px', color: MODERN_THEME.colors.textPrimary }}
                            />
                        </Space>
                    </Col>
                </Row>
            </Card>

            <Card style={styles.filterCard}>
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Row justify="space-between" align="middle">
                        <Col>
                            <Space>
                                <FilterOutlined style={{ fontSize: '18px', color: MODERN_THEME.colors.primary }} />
                                <Text strong style={{ fontSize: '16px', color: MODERN_THEME.colors.textPrimary }}>
                                    {intl.formatMessage({ id: 'history.advancedFilters' })}
                                </Text>
                            </Space>
                        </Col>
                        <Col>
                            <Button
                                type="text"
                                icon={<ClearOutlined />}
                                onClick={handleReset}
                                style={{ color: MODERN_THEME.colors.textSecondary }}
                            >
                                {intl.formatMessage({ id: 'history.clearAll' })}
                            </Button>
                        </Col>
                    </Row>
                    <Divider style={{ margin: '12px 0' }} />
                    <Row gutter={[16, 16]}>
                        <Col span={6}>
                            <Select
                                placeholder={intl.formatMessage({ id: 'history.selectEventType' })}
                                allowClear
                                style={{ width: '100%' }}
                                value={selectedFilters.eventType}
                                onChange={(v) => handleFilterChange('eventType', v)}
                                suffixIcon={<FilterOutlined />}
                            >
                                {Object.keys(EVENT_TYPES).map((k) => (
                                    <Option key={k} value={k}>
                                        <Space>
                                            <span>{EVENT_TYPE_ICONS[k]}</span>
                                            <span>{enumLabel(intl, 'eventType', k)}</span>
                                        </Space>
                                    </Option>
                                ))}
                            </Select>
                        </Col>
                        <Col span={6}>
                            <Select
                                placeholder={intl.formatMessage({ id: 'history.selectChannel' })}
                                allowClear
                                style={{ width: '100%' }}
                                value={selectedFilters.channel}
                                onChange={(v) => handleFilterChange('channel', v)}
                                suffixIcon={<FilterOutlined />}
                            >
                                {Object.keys(CHANNELS).map((k) => (
                                    <Option key={k} value={k}>
                                        <Space>
                                            <span>{CHANNEL_COLORS[k]?.icon}</span>
                                            <span>{enumLabel(intl, 'channel', k)}</span>
                                        </Space>
                                    </Option>
                                ))}
                            </Select>
                        </Col>
                        <Col span={6}>
                            <Select
                                placeholder={intl.formatMessage({ id: 'history.selectStatus' })}
                                allowClear
                                style={{ width: '100%' }}
                                value={selectedFilters.status}
                                onChange={(v) => handleFilterChange('status', v)}
                                suffixIcon={<FilterOutlined />}
                            >
                                {Object.keys(STATUS_MAP).map((k) => (
                                    <Option key={k} value={k}>
                                        <Space>
                                            <span>{STATUS_DETAILS[k]?.icon}</span>
                                            <span>{enumLabel(intl, 'status', k)}</span>
                                        </Space>
                                    </Option>
                                ))}
                            </Select>
                        </Col>
                        <Col span={6}>
                            <RangePicker
                                style={{ width: '100%' }}
                                placeholder={[intl.formatMessage({ id: 'history.startDate' }), intl.formatMessage({ id: 'history.endDate' })]}
                                onChange={(dates) => {
                                    // 后端用 LocalDateTime.parse 接收，带 Z/时区偏移的 ISO 串会解析失败并抛 500
                                    handleFilterChange('startDate', dates?.[0]?.format('YYYY-MM-DDTHH:mm:ss') || null);
                                    handleFilterChange('endDate', dates?.[1]?.format('YYYY-MM-DDTHH:mm:ss') || null);
                                }}
                            />
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Space>
                                <Button
                                    type="primary"
                                    icon={<SearchOutlined />}
                                    onClick={() => fetchNotifications(0, 10)}
                                    style={styles.filterButton}
                                    size="large"
                                >
                                    {intl.formatMessage({ id: 'history.applyFilters' })}
                                </Button>
                                <Button
                                    icon={<ReloadOutlined />}
                                    onClick={handleReset}
                                    style={styles.filterButton}
                                    size="large"
                                >
                                    {intl.formatMessage({ id: 'history.reset' })}
                                </Button>
                            </Space>
                        </Col>
                    </Row>
                </Space>
            </Card>

            {listError && (
                <Alert
                    type="error"
                    showIcon
                    style={{ marginBottom: '16px' }}
                    message={intl.formatMessage({ id: 'history.loadFailed' })}
                    action={
                        <Button
                            size="small"
                            icon={<ReloadOutlined />}
                            onClick={() => fetchNotifications(pagination.page, pagination.size)}
                        >
                            {intl.formatMessage({ id: 'history.retry' })}
                        </Button>
                    }
                />
            )}
            {loading ? (
                <Card style={styles.tableCard}>
                    <Skeleton active paragraph={{ rows: 8 }} />
                </Card>
            ) : (
                <>
                    {/* 这些数字是从当前页算出来的，标题必须把这一点说出来，否则读起来像全库统计（PRD-54②） */}
                    <Text strong style={{ display: 'block', marginBottom: '8px', color: MODERN_THEME.colors.textSecondary }}>
                        {intl.formatMessage({ id: 'history.pageStatsTitle' })}
                    </Text>
                    <Row gutter={[16, 16]} style={{ marginBottom: '24px' }}>
                        {Object.entries(eventTypeStats).map(([type, count]) => (
                            <Col flex="1 1 160px" key={type}>
                                <Card style={styles.statsCard} hoverable>
                                    <Statistic
                                        title={
                                            <Space>
                                                <span>{EVENT_TYPE_ICONS[type] || '📌'}</span>
                                                <span>{enumLabel(intl, 'eventType', type)}</span>
                                            </Space>
                                        }
                                        value={count}
                                        valueStyle={{
                                            color: EVENT_TYPE_COLORS[type]?.text || MODERN_THEME.colors.primary,
                                            fontWeight: 600,
                                        }}
                                    />
                                </Card>
                            </Col>
                        ))}
                    </Row>

                    <Card style={styles.tableCard}>
                        <Table
                            dataSource={notifications}
                            columns={columns}
                            rowKey="id"
                            pagination={{
                                current: pagination.page + 1,
                                pageSize: pagination.size,
                                total: pagination.total,
                                showSizeChanger: true,
                                showQuickJumper: true,
                                pageSizeOptions: ['10', '20', '50', '100'],
                                showTotal: (total, range) => (
                                    <Text style={{ color: MODERN_THEME.colors.textSecondary }}>
                                        {intl.formatMessage({ id: 'history.showingRecords' }, { start: range[0], end: range[1], total })}
                                    </Text>
                                ),
                                onChange: (page, size) => {
                                    fetchNotifications(page - 1, size);
                                },
                            }}
                            scroll={{ x: 1520 }}
                            style={{ borderRadius: MODERN_THEME.borderRadius.card }}
                            rowClassName={(record, index) => 
                                index % 2 === 0 ? 'even-row' : 'odd-row'
                            }
                        />
                    </Card>
                </>
            )}

            <Modal
                open={!!detail}
                onCancel={() => setDetail(null)}
                footer={<Button onClick={() => setDetail(null)}>{intl.formatMessage({ id: 'common.close' })}</Button>}
                title={intl.formatMessage({ id: 'history.detail' })}
                width={640}
            >
                {detail && (
                    <Descriptions column={1} bordered size="small">
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.id' })}>
                            {detail.id}
                        </Descriptions.Item>
                        {isAdmin && (
                            <Descriptions.Item label={intl.formatMessage({ id: 'history.customer' })}>
                                <Space size={6}>
                                    {customerIdentity(detail.customerId).primary}
                                    {customerIdentity(detail.customerId).secondary && (
                                        <Text style={{ color: MODERN_THEME.colors.textTertiary, fontSize: '12px' }}>
                                            {customerIdentity(detail.customerId).secondary}
                                        </Text>
                                    )}
                                    {!customerIdentity(detail.customerId).known && (
                                        <Tag color="orange" style={{ margin: 0 }}>
                                            {intl.formatMessage({ id: 'history.customerDeleted' })}
                                        </Tag>
                                    )}
                                </Space>
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.eventType' })}>
                            {enumLabel(intl, 'eventType', detail.eventType)}
                        </Descriptions.Item>
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.channel' })}>
                            {enumLabel(intl, 'channel', detail.channel)}
                        </Descriptions.Item>
                        {/* 直发（PRD-51）的地址根本不在客户档案里，不显示出来就没法解释这行发去了哪 */}
                        {detail.recipient && (
                            <Descriptions.Item label={intl.formatMessage({ id: 'history.recipient' })}>
                                <Text copyable style={{ fontSize: '12px' }}>{detail.recipient}</Text>
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.status' })}>
                            {STATUS_DETAILS[detail.status]?.icon} {enumLabel(intl, 'status', detail.status)}
                        </Descriptions.Item>
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.reason' })}>
                            {detail.reason
                                ? enumLabel(intl, 'reason', detail.reason)
                                : intl.formatMessage({ id: 'history.none' })}
                        </Descriptions.Item>
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.eventId' })}>
                            <Text copyable style={{ fontSize: '12px' }}>{detail.eventId}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.readState' })}>
                            {detail.read
                                ? intl.formatMessage({ id: 'history.readYes' })
                                : intl.formatMessage({ id: 'history.readNo' })}
                        </Descriptions.Item>
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.retryCount' })}>
                            {detail.retryCount ?? 0}
                        </Descriptions.Item>
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.time' })}>
                            {formatDateTime(detail.createdAt, intl.locale)}
                        </Descriptions.Item>
                        {detail.updatedAt && (
                            <Descriptions.Item label={intl.formatMessage({ id: 'history.updatedAt' })}>
                                {formatDateTime(detail.updatedAt, intl.locale)}
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label={intl.formatMessage({ id: 'history.content' })}>
                            {detail.content || intl.formatMessage({ id: 'history.noContent' })}
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Modal>

            <style>{`
                .even-row {
                    background: ${MODERN_THEME.colors.cardBg};
                }
                .odd-row {
                    background: ${MODERN_THEME.colors.background};
                }
                .ant-table-thead > tr > th {
                    background: ${MODERN_THEME.colors.background};
                    color: ${MODERN_THEME.colors.textPrimary};
                    font-weight: 600;
                    border-bottom: 2px solid ${MODERN_THEME.colors.border};
                }
                .ant-table-tbody > tr:hover > td {
                    background: ${MODERN_THEME.colors.primary}10;
                }
                .ant-card:hover {
                    box-shadow: ${MODERN_THEME.shadows.cardHover};
                    transition: ${MODERN_THEME.transitions.normal};
                }
            `}</style>
        </div>
    );
}
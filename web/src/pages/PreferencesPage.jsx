import { useEffect, useState } from 'react';
import { Table, Switch, Button, Modal, message, Form, Card, Space, Tooltip, Alert } from 'antd';
import { PlusOutlined, EditOutlined, InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import PreferenceForm from '../components/PreferenceForm';
import usePreferenceStore from '../store/usePreferenceStore';
import { enumLabel } from '../utils/labels';

export default function PreferencesPage() {
    const intl = useIntl();
    const { prefs, loading, warnings, fetchPreferences, updatePreferences } = usePreferenceStore();
    const [modalVisible, setModalVisible] = useState(false);
    const [editRecord, setEditRecord] = useState(null);
    const [form] = Form.useForm();

    useEffect(() => {
        fetchPreferences();
    }, []);

    const handleToggle = async (record) => {
        const updated = prefs.map((p) =>
            p.id === record.id ? { ...p, enabled: !p.enabled } : p
        );
        try {
            await updatePreferences(updated);
            message.success(intl.formatMessage({ id: 'common.toggleSuccess' }));
        } catch (e) {
            // axios 拦截器已按后端 code 弹过具体文案，这里再弹一次会把原因盖掉
            fetchPreferences();
        }
    };

    const handleSave = async (values) => {
        // channels 保持数组（接口契约就是数组），
        // 时间字段转为后端存储的 HH:mm:ss 字符串
        const formatted = {
            ...values,
            channels: values.channels || [],
            quietStart: values.quietStart ? values.quietStart.format('HH:mm:ss') : null,
            quietEnd: values.quietEnd ? values.quietEnd.format('HH:mm:ss') : null,
        };

        let updated;
        if (editRecord) {
            updated = prefs.map((p) =>
                p.id === editRecord.id ? { ...p, ...formatted } : p
            );
        } else {
            updated = [...prefs, formatted];
        }
        try {
            await updatePreferences(updated);
            setModalVisible(false);
            setEditRecord(null);
            form.resetFields();
            message.success(intl.formatMessage({ id: 'common.updatedSuccess' }));
        } catch (e) {
            // 同上：400 的 QUIET_PERIOD_* / 409 由拦截器给出语料文案
        }
    };

    const columns = [
        {
            title: (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span>{intl.formatMessage({ id: 'preferences.eventType' })}</span>
                </div>
            ),
            dataIndex: 'eventType',
            render: (v) => (
                <span style={{ 
                    padding: '4px 12px', 
                    background: 'var(--color-glass)',
                    borderRadius: 'var(--radius-sm)',
                    color: 'var(--color-text-primary)'
                }}>
                    {enumLabel(intl, 'eventType', v)}
                </span>
            ),
        },
        {
            title: intl.formatMessage({ id: 'preferences.channels' }),
            dataIndex: 'channels',
            render: (chs) => (
                <Space wrap>
                    {(chs || []).map((c) => (
                        <span 
                            key={c}
                            style={{ 
                                padding: '4px 10px', 
                                background: 'rgba(99, 102, 241, 0.15)',
                                borderRadius: 'var(--radius-sm)',
                                color: 'var(--color-primary-light)',
                                fontSize: '12px'
                            }}
                        >
                            {enumLabel(intl, 'channel', c)}
                        </span>
                    ))}
                </Space>
            ),
        },
        {
            title: (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span>{intl.formatMessage({ id: 'preferences.quietPeriod' })}</span>
                    <Tooltip title={intl.formatMessage({ id: 'preferences.quietPeriodTip' })}>
                        <InfoCircleOutlined style={{ fontSize: '12px', color: 'var(--color-text-tertiary)' }} />
                    </Tooltip>
                </div>
            ),
            render: (_, r) => (
                <span style={{ color: r.quietStart && r.quietEnd ? 'var(--color-text-secondary)' : 'var(--color-text-muted)' }}>
                    {r.quietStart && r.quietEnd ? `${r.quietStart}-${r.quietEnd}` : '-'}
                </span>
            ),
        },
        {
            title: intl.formatMessage({ id: 'preferences.enabled' }),
            dataIndex: 'enabled',
            render: (_, r) => (
                <Switch 
                    checked={r.enabled} 
                    onChange={() => handleToggle(r)}
                    checkedChildren={intl.formatMessage({ id: 'common.on' })}
                    unCheckedChildren={intl.formatMessage({ id: 'common.off' })}
                    style={{ background: r.enabled ? 'var(--color-success)' : 'var(--color-border)' }}
                />
            ),
        },
        {
            title: intl.formatMessage({ id: 'preferences.actions' }),
            render: (_, r) => (
                <Button
                    type="text"
                    icon={<EditOutlined />}
                    onClick={() => {
                        setEditRecord(r);
                        setModalVisible(true);
                    }}
                    style={{ color: 'var(--color-primary-light)' }}
                >
                    {intl.formatMessage({ id: 'preferences.edit' })}
                </Button>
            ),
        },
    ];

    return (
        <div style={{ animation: 'fadeIn 0.4s ease' }}>
            <div style={{ marginBottom: '24px' }}>
                <h2 style={{ 
                    fontSize: '20px', 
                    fontWeight: 600, 
                    color: 'var(--color-text-primary)',
                    marginBottom: '8px'
                }}>
                    {intl.formatMessage({ id: 'preferences.title' })}
                </h2>
                <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>
                    {intl.formatMessage({ id: 'preferences.description' })}
                </p>
            </div>

            <Card 
                style={{ 
                    marginBottom: '24px',
                    background: 'var(--color-glass)',
                    border: '1px solid var(--color-glass-border)'
                }}
                bodyStyle={{ padding: '16px' }}
            >
                <div style={{ 
                    display: 'flex', 
                    alignItems: 'center',
                    gap: '12px'
                }}>
                    <InfoCircleOutlined style={{ color: 'var(--color-info)', fontSize: '16px' }} />
                    <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', margin: 0 }}>
                        {intl.formatMessage({ id: 'preferences.tip' })}
                    </p>
                </div>
            </Card>

            {warnings.length > 0 && (
                <Alert
                    type="warning"
                    showIcon
                    icon={<WarningOutlined />}
                    closable
                    onClose={() => usePreferenceStore.setState({ warnings: [] })}
                    style={{ marginBottom: '16px' }}
                    message={intl.formatMessage({ id: 'preferences.unreachableTitle' })}
                    description={
                        <ul style={{ margin: 0, paddingLeft: '18px' }}>
                            {warnings.map((w) => (
                                <li key={`${w.eventType}-${w.channel}`}>
                                    {enumLabel(intl, 'eventType', w.eventType)}
                                    {' · '}
                                    {enumLabel(intl, 'channel', w.channel)}
                                    {' — '}
                                    {intl.formatMessage({ id: `enum.reason.${w.reason}` })}
                                </li>
                            ))}
                        </ul>
                    }
                />
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '16px' }}>
                <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => {
                        setEditRecord(null);
                        setModalVisible(true);
                    }}
                >
                    {intl.formatMessage({ id: 'preferences.addPreference' })}
                </Button>
            </div>

            <Card style={{ borderRadius: 'var(--radius-lg)' }}>
                <Table
                    rowKey="id"
                    columns={columns}
                    dataSource={prefs}
                    loading={loading}
                    pagination={false}
                    bordered={false}
                    style={{ background: 'transparent' }}
                    rowClassName="table-row"
                />
            </Card>

            <Modal
                title={editRecord 
                    ? intl.formatMessage({ id: 'preferences.editPreference' }) 
                    : intl.formatMessage({ id: 'preferences.addPreference' })}
                open={modalVisible}
                onCancel={() => {
                    setModalVisible(false);
                    setEditRecord(null);
                    form.resetFields();
                }}
                footer={null}
                destroyOnHidden
                width={480}
            >
                <PreferenceForm
                    initialValues={editRecord}
                    onSave={handleSave}
                    form={form}
                />
            </Modal>

            <style>{`
                .table-row:hover td {
                    background: rgba(99, 102, 241, 0.08) !important;
                }
            `}</style>
        </div>
    );
}
import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Form, Input, Space, Spin, Tag, Typography, message } from 'antd';
import { MailOutlined, MobileOutlined, NotificationOutlined } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import { getCurrentCustomer, updateProfile } from '../api/customerApi';
import useAuthStore from '../store/useAuthStore';
import { formatDateTime } from '../utils/formatters';
import { enumLabel } from '../utils/labels';

const { Title, Text } = Typography;

/**
 * 个人中心：联系方式决定 SMS/Email/Push 能否投递，
 * 后端 NotificationDispatchService 就是按这三个字段解析收件人的。
 */
export default function ProfilePage() {
    const intl = useIntl();
    const [form] = Form.useForm();
    const setUser = useAuthStore((state) => state.setUser);
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    const load = () => {
        setLoading(true);
        return getCurrentCustomer()
            .then((data) => {
                setProfile(data);
                form.setFieldsValue({
                    name: data.name,
                    email: data.email,
                    phone: data.phone,
                    pushToken: data.pushToken,
                });
            })
            .catch(() => message.error(intl.formatMessage({ id: 'profile.loadFailed' })))
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        load();
    }, []);

    const handleSave = async (values) => {
        setSaving(true);
        try {
            const data = await updateProfile(values);
            setProfile(data);
            setUser({ name: data.name });
            message.success(intl.formatMessage({ id: 'profile.saved' }));
        } catch (e) {
            // 具体失败文案（含 EMAIL_ALREADY_USED）由 axios 拦截器按 code 提示
        } finally {
            setSaving(false);
        }
    };

    const channels = [
        { key: 'SMS', ok: !!profile?.phone, missing: 'NO_PHONE', icon: <MobileOutlined /> },
        { key: 'EMAIL', ok: !!profile?.email, missing: 'NO_EMAIL', icon: <MailOutlined /> },
        { key: 'PUSH', ok: !!profile?.pushToken, missing: 'NO_PUSH_TOKEN', icon: <NotificationOutlined /> },
    ];

    if (loading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '80px 0' }}>
                <Spin />
            </div>
        );
    }

    return (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <div>
                <Title level={3} style={{ margin: 0 }}>{intl.formatMessage({ id: 'profile.title' })}</Title>
                <Text style={{ color: 'rgba(0, 0, 0, 0.45)' }}>
                    {intl.formatMessage({ id: 'profile.subtitle' })}
                </Text>
            </div>

            <Card title={intl.formatMessage({ id: 'profile.accountTitle' })}>
                <Descriptions column={{ xs: 1, sm: 2 }} size="small">
                    <Descriptions.Item label={intl.formatMessage({ id: 'profile.id' })}>{profile?.id}</Descriptions.Item>
                    <Descriptions.Item label={intl.formatMessage({ id: 'profile.role' })}>{profile?.role}</Descriptions.Item>
                    <Descriptions.Item label={intl.formatMessage({ id: 'profile.createdAt' })}>
                        {formatDateTime(profile?.createdAt)}
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card title={intl.formatMessage({ id: 'profile.contactTitle' })}>
                <Alert
                    type="info"
                    showIcon
                    message={intl.formatMessage({ id: 'profile.contactTip' })}
                    style={{ marginBottom: 16 }}
                />
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ maxWidth: 480 }}>
                    <Form.Item
                        name="name"
                        label={intl.formatMessage({ id: 'profile.name' })}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'profile.nameRequired' }) }]}
                    >
                        <Input maxLength={100} />
                    </Form.Item>
                    <Form.Item
                        name="email"
                        label={intl.formatMessage({ id: 'profile.email' })}
                        rules={[
                            { required: true, message: intl.formatMessage({ id: 'profile.emailRequired' }) },
                            { type: 'email', message: intl.formatMessage({ id: 'profile.emailInvalid' }) },
                        ]}
                    >
                        <Input maxLength={255} />
                    </Form.Item>
                    <Form.Item
                        name="phone"
                        label={intl.formatMessage({ id: 'profile.phone' })}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'profile.phoneRequired' }) }]}
                    >
                        <Input maxLength={20} />
                    </Form.Item>
                    <Form.Item
                        name="pushToken"
                        label={intl.formatMessage({ id: 'profile.pushToken' })}
                        tooltip={intl.formatMessage({ id: 'profile.pushTokenTip' })}
                    >
                        <Input maxLength={500} />
                    </Form.Item>
                    <Form.Item>
                        <Button type="primary" htmlType="submit" loading={saving}>
                            {intl.formatMessage({ id: 'profile.save' })}
                        </Button>
                    </Form.Item>
                </Form>
            </Card>

            <Card>
                <Space size="middle" wrap>
                    {channels.map((channel) => (
                        <Tag
                            key={channel.key}
                            color={channel.ok ? 'success' : 'error'}
                            icon={channel.icon}
                            style={{ padding: '4px 10px' }}
                        >
                            {enumLabel(intl, 'channel', channel.key)}
                            {' · '}
                            {channel.ok
                                ? intl.formatMessage({ id: 'profile.channelReady' })
                                : intl.formatMessage({ id: `enum.reason.${channel.missing}` })}
                        </Tag>
                    ))}
                </Space>
            </Card>
        </Space>
    );
}

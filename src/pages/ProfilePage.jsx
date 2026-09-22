import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Form, Input, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { MailOutlined, MobileOutlined, NotificationOutlined } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import { getCurrentCustomer, updateProfile } from '../api/customerApi';
import useAuthStore from '../store/useAuthStore';
import { formatDateTime } from '../utils/formatters';
import { NOTIFICATION_LOCALES, TIMEZONE_OPTIONS } from '../utils/constants';
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
                    accountNumber: data.accountNumber,
                    // 未设置就留空：把 NULL 渲染成「中文」等于替客户做决定，而保存时又会被写回库里
                    locale: data.locale || undefined,
                    timezone: data.timezone || undefined,
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
            // 清空时区/语言要显式传空串：后端 PATCH 语义里 null=不改、空串=清除（时区回退服务端默认钟点，
            // 语言回退派发端的默认模板语言）
            const data = await updateProfile({
                ...values,
                timezone: values.timezone || '',
                locale: values.locale || '',
            });
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
                        {formatDateTime(profile?.createdAt, intl.locale)}
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
                    <Form.Item
                        name="accountNumber"
                        label={intl.formatMessage({ id: 'profile.accountNumber' })}
                        tooltip={intl.formatMessage({ id: 'profile.accountNumberTip' })}
                    >
                        <Input maxLength={32} allowClear />
                    </Form.Item>
                    <Form.Item
                        name="locale"
                        label={intl.formatMessage({ id: 'profile.locale' })}
                        tooltip={intl.formatMessage({ id: 'profile.localeTip' })}
                    >
                        <Select
                            allowClear
                            placeholder={intl.formatMessage({ id: 'profile.localePlaceholder' })}
                            options={NOTIFICATION_LOCALES.map((code) => ({
                                value: code,
                                label: intl.formatMessage({ id: `enum.locale.${code}` }),
                            }))}
                        />
                    </Form.Item>
                    <Form.Item
                        name="timezone"
                        label={intl.formatMessage({ id: 'profile.timezone' })}
                        tooltip={intl.formatMessage({ id: 'profile.timezoneTip' })}
                    >
                        <Select
                            allowClear
                            showSearch
                            placeholder={intl.formatMessage({ id: 'profile.timezonePlaceholder' })}
                            options={TIMEZONE_OPTIONS.map((zone) => ({ value: zone, label: zone }))}
                        />
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

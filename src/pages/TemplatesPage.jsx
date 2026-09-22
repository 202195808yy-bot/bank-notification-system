import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tooltip, Typography, message } from 'antd';
import { useIntl } from 'react-intl';
import { CHANNELS, DEFAULT_NOTIFICATION_LOCALE, EVENT_TYPES, NOTIFICATION_LOCALES } from '../utils/constants';
import { enumLabel } from '../utils/labels';
import useTemplateStore from '../store/useTemplateStore';

const { Option } = Select;
const { Text } = Typography;

export default function TemplatesPage() {
    const intl = useIntl();
    const {
        templates,
        loading,
        fetchTemplates,
        addTemplate,
        editTemplate,
        removeTemplate,
    } = useTemplateStore();

    const [modalVisible, setModalVisible] = useState(false);
    const [editRecord, setEditRecord] = useState(null);
    const [filters, setFilters] = useState({ eventType: undefined, channel: undefined, locale: undefined });
    const [form] = Form.useForm();

    // filters 变化即回源：后端三个条件都可只传一部分
    const load = useCallback((next) => {
        fetchTemplates({
            eventType: next?.eventType,
            channel: next?.channel,
            locale: next?.locale,
        });
    }, [fetchTemplates]);

    useEffect(() => {
        load(filters);
    }, [filters, load]);

    const handleFilterChange = (key, value) => {
        setFilters((prev) => ({ ...prev, [key]: value || undefined }));
    };

    const openCreate = () => {
        setEditRecord(null);
        form.resetFields();
        form.setFieldsValue({ locale: DEFAULT_NOTIFICATION_LOCALE });
        setModalVisible(true);
    };

    const openEdit = (record) => {
        setEditRecord(record);
        form.setFieldsValue(record);
        setModalVisible(true);
    };

    const handleSubmit = async () => {
        let values;
        try {
            values = await form.validateFields();
        } catch (e) {
            return; // 字段级提示 antd 已经给在表单上
        }
        try {
            if (editRecord) {
                await editTemplate(editRecord.id, values);
                message.success(intl.formatMessage({ id: 'common.updatedSuccess' }));
            } else {
                await addTemplate(values);
                message.success(intl.formatMessage({ id: 'common.createdSuccess' }));
            }
        } catch (e) {
            // axios 拦截器已按 error.code.<CODE> 弹过一次文案，这里再弹一遍只会盖掉具体原因
            return;
        }
        setModalVisible(false);
        form.resetFields();
    };

    const handleDelete = async (id) => {
        try {
            await removeTemplate(id);
            message.success(intl.formatMessage({ id: 'common.deletedSuccess' }));
        } catch (e) {
            return;
        }
    };

    const columns = [
        { title: 'ID', dataIndex: 'id', width: 70 },
        {
            title: intl.formatMessage({ id: 'templates.eventType' }),
            dataIndex: 'eventType',
            render: (v) => enumLabel(intl, 'eventType', v),
        },
        {
            title: intl.formatMessage({ id: 'templates.channel' }),
            dataIndex: 'channel',
            render: (v) => enumLabel(intl, 'channel', v),
        },
        {
            title: intl.formatMessage({ id: 'templates.language' }),
            dataIndex: 'locale',
            render: (v) => enumLabel(intl, 'locale', v),
        },
        { title: intl.formatMessage({ id: 'templates.titleTemplate' }), dataIndex: 'titleTemplate', ellipsis: true },
        {
            title: intl.formatMessage({ id: 'templates.bodyTemplate' }),
            dataIndex: 'bodyTemplate',
            ellipsis: { showTitle: false },
            render: (v) => (
                <Tooltip placement="topLeft" title={v}>
                    <span style={{ fontFamily: 'monospace' }}>{v}</span>
                </Tooltip>
            ),
        },
        {
            title: intl.formatMessage({ id: 'templates.actions' }),
            width: 200,
            render: (_, record) => (
                <Space>
                    <Button type="link" onClick={() => openEdit(record)}>
                        {intl.formatMessage({ id: 'templates.edit' })}
                    </Button>
                    <Popconfirm title={intl.formatMessage({ id: 'common.areYouSure' })} onConfirm={() => handleDelete(record.id)}>
                        <Button type="link" danger>{intl.formatMessage({ id: 'templates.delete' })}</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const hasFilters = Object.values(filters).some(Boolean);

    return (
        <div style={{ padding: '24px' }}>
            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                <h2>{intl.formatMessage({ id: 'templates.title' })}</h2>
                <Button type="primary" onClick={openCreate}>
                    {intl.formatMessage({ id: 'templates.addTemplate' })}
                </Button>
            </div>
            <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message={intl.formatMessage({ id: 'templates.tripleUnique' })}
            />
            <Space wrap style={{ marginBottom: 16 }}>
                <Select
                    allowClear
                    style={{ minWidth: 200 }}
                    placeholder={intl.formatMessage({ id: 'templates.selectEventType' })}
                    value={filters.eventType}
                    onChange={(v) => handleFilterChange('eventType', v)}
                >
                    {Object.keys(EVENT_TYPES).map((k) => (
                        <Option key={k} value={k}>{enumLabel(intl, 'eventType', k)}</Option>
                    ))}
                </Select>
                <Select
                    allowClear
                    style={{ minWidth: 140 }}
                    placeholder={intl.formatMessage({ id: 'templates.selectChannel' })}
                    value={filters.channel}
                    onChange={(v) => handleFilterChange('channel', v)}
                >
                    {Object.keys(CHANNELS).map((k) => (
                        <Option key={k} value={k}>{enumLabel(intl, 'channel', k)}</Option>
                    ))}
                </Select>
                <Select
                    allowClear
                    style={{ minWidth: 180 }}
                    placeholder={intl.formatMessage({ id: 'templates.selectLanguage' })}
                    value={filters.locale}
                    onChange={(v) => handleFilterChange('locale', v)}
                >
                    {NOTIFICATION_LOCALES.map((code) => (
                        <Option key={code} value={code}>{enumLabel(intl, 'locale', code)}</Option>
                    ))}
                </Select>
                {hasFilters && (
                    <Button onClick={() => setFilters({ eventType: undefined, channel: undefined, locale: undefined })}>
                        {intl.formatMessage({ id: 'templates.clearFilters' })}
                    </Button>
                )}
            </Space>
            <Table
                rowKey="id"
                columns={columns}
                dataSource={templates}
                loading={loading}
                pagination={{ pageSize: 10, showSizeChanger: true }}
            />
            <Modal
                title={editRecord ? intl.formatMessage({ id: 'templates.editTemplate' }) : intl.formatMessage({ id: 'templates.addTemplate' })}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                destroyOnHidden
                forceRender
            >
                {editRecord && (
                    <Text type="secondary">
                        {intl.formatMessage({ id: 'templates.tripleLocked' })}
                    </Text>
                )}
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="eventType"
                        label={intl.formatMessage({ id: 'templates.eventType' })}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'templates.pleaseSelectEventType' }) }]}
                    >
                        <Select placeholder={intl.formatMessage({ id: 'templates.selectEventType' })} disabled={!!editRecord}>
                            {Object.keys(EVENT_TYPES).map((k) => (
                                <Option key={k} value={k}>{enumLabel(intl, 'eventType', k)}</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="channel"
                        label={intl.formatMessage({ id: 'templates.channel' })}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'templates.pleaseSelectChannel' }) }]}
                    >
                        <Select placeholder={intl.formatMessage({ id: 'templates.selectChannel' })} disabled={!!editRecord}>
                            {Object.keys(CHANNELS).map((k) => (
                                <Option key={k} value={k}>{enumLabel(intl, 'channel', k)}</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="locale"
                        label={intl.formatMessage({ id: 'templates.language' })}
                        initialValue={DEFAULT_NOTIFICATION_LOCALE}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'templates.selectLanguage' }) }]}
                    >
                        {/* 原先是自由文本 Input：写成 zh-CN / rus 都能存进库，但派发端按字符串精确匹配，
                            于是模板永远取不到，界面只显示「缺少对应模板」 */}
                        <Select disabled={!!editRecord}>
                            {NOTIFICATION_LOCALES.map((code) => (
                                <Option key={code} value={code}>{enumLabel(intl, 'locale', code)}</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="titleTemplate"
                        label={intl.formatMessage({ id: 'templates.titleTemplate' })}
                        rules={[{ max: 200, message: intl.formatMessage({ id: 'templates.titleTooLong' }) }]}
                    >
                        <Input placeholder={intl.formatMessage({ id: 'templates.supportsVariables' })} />
                    </Form.Item>
                    <Form.Item
                        name="bodyTemplate"
                        label={intl.formatMessage({ id: 'templates.bodyTemplate' })}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'templates.pleaseEnterBodyTemplate' }) }]}
                    >
                        <Input.TextArea rows={4} placeholder={intl.formatMessage({ id: 'templates.supportsVariables' })} />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
}

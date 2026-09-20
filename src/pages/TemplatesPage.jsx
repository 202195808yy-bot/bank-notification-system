import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Space, Popconfirm, message } from 'antd';
import { useIntl } from 'react-intl';
import { EVENT_TYPES, CHANNELS } from '../utils/constants';
import { enumLabel } from '../utils/labels';
import useTemplateStore from '../store/useTemplateStore';

const { Option } = Select;

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
    const [form] = Form.useForm();

    useEffect(() => {
        fetchTemplates();
    }, []);

    const handleSubmit = async () => {
        const values = await form.validateFields();
        try {
            if (editRecord) {
                await editTemplate(editRecord.id, values);
                message.success(intl.formatMessage({ id: 'common.updatedSuccess' }));
            } else {
                await addTemplate(values);
                message.success(intl.formatMessage({ id: 'common.createdSuccess' }));
            }
            setModalVisible(false);
            form.resetFields();
        } catch (e) {
            message.error(intl.formatMessage({ id: 'common.operationFailed' }));
        }
    };

    const handleDelete = async (id) => {
        try {
            await removeTemplate(id);
            message.success(intl.formatMessage({ id: 'common.deletedSuccess' }));
        } catch (e) {
            message.error(intl.formatMessage({ id: 'common.deleteFailed' }));
        }
    };

    const columns = [
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
        { title: intl.formatMessage({ id: 'templates.language' }), dataIndex: 'locale' },
        { title: intl.formatMessage({ id: 'templates.titleTemplate' }), dataIndex: 'titleTemplate', ellipsis: true },
        { title: intl.formatMessage({ id: 'templates.bodyTemplate' }), dataIndex: 'bodyTemplate', ellipsis: true },
        {
            title: intl.formatMessage({ id: 'templates.actions' }),
            render: (_, record) => (
                <Space>
                    <Button
                        type="link"
                        onClick={() => {
                            setEditRecord(record);
                            form.setFieldsValue(record);
                            setModalVisible(true);
                        }}
                    >
                        {intl.formatMessage({ id: 'templates.edit' })}
                    </Button>
                    <Popconfirm title={intl.formatMessage({ id: 'common.areYouSure' })} onConfirm={() => handleDelete(record.id)}>
                        <Button type="link" danger>{intl.formatMessage({ id: 'templates.delete' })}</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div style={{ padding: '24px' }}>
            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                <h2>{intl.formatMessage({ id: 'templates.title' })}</h2>
                <Button
                    type="primary"
                    onClick={() => {
                        setEditRecord(null);
                        form.resetFields();
                        setModalVisible(true);
                    }}
                >
                    {intl.formatMessage({ id: 'templates.addTemplate' })}
                </Button>
            </div>
            <Table
                rowKey="id"
                columns={columns}
                dataSource={templates}
                loading={loading}
            />
            <Modal
                title={editRecord ? intl.formatMessage({ id: 'templates.editTemplate' }) : intl.formatMessage({ id: 'templates.addTemplate' })}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                destroyOnHidden
                forceRender
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="eventType"
                        label={intl.formatMessage({ id: 'templates.eventType' })}
                        rules={[{ required: true, message: intl.formatMessage({ id: 'templates.pleaseSelectEventType' }) }]}
                    >
                        <Select placeholder={intl.formatMessage({ id: 'templates.selectEventType' })}>
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
                        <Select placeholder={intl.formatMessage({ id: 'templates.selectChannel' })}>
                            {Object.keys(CHANNELS).map((k) => (
                                <Option key={k} value={k}>{enumLabel(intl, 'channel', k)}</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item name="locale" label={intl.formatMessage({ id: 'templates.language' })} initialValue="zh_CN">
                        <Input />
                    </Form.Item>
                    <Form.Item name="titleTemplate" label={intl.formatMessage({ id: 'templates.titleTemplate' })}>
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
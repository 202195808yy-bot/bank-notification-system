import React from 'react';
import { Form, Select, Checkbox, TimePicker, Switch, Button } from 'antd';
import { EVENT_TYPES, CHANNELS } from '../utils/constants';
import dayjs from 'dayjs';

const { Option } = Select;

export default function PreferenceForm({ initialValues, onSave, form, onValuesChange }) {
    const [submitting, setSubmitting] = React.useState(false);

    const handleFinish = async (values) => {
        setSubmitting(true);
        try {
            const formatted = {
                ...values,
                channels: JSON.stringify(values.channels),
                quietStart: values.quietStart ? values.quietStart.format('HH:mm') : null,
                quietEnd: values.quietEnd ? values.quietEnd.format('HH:mm') : null,
            };
            await onSave?.(formatted);
        } finally {
            setSubmitting(false);
        }
    };

    const toDayjs = (str) => (str ? dayjs(str, 'HH:mm') : null);

    return (
        <Form
            form={form}
            layout="vertical"
            initialValues={{
                eventType: initialValues?.eventType || undefined,
                channels: initialValues?.channels || [],
                quietStart: toDayjs(initialValues?.quietStart),
                quietEnd: toDayjs(initialValues?.quietEnd),
                enabled: initialValues?.enabled ?? true,
            }}
            onFinish={handleFinish}
            onValuesChange={onValuesChange}
        >
            <Form.Item
                label="Event Type"
                name="eventType"
                rules={[{ required: true, message: 'Please select event type' }]}
            >
                <Select placeholder="Select event type">
                    {Object.entries(EVENT_TYPES).map(([key, val]) => (
                        <Option key={key} value={key}>{val}</Option>
                    ))}
                </Select>
            </Form.Item>

            <Form.Item
                label="Channels"
                name="channels"
                rules={[{ required: true, message: 'Please select at least one channel' }]}
            >
                <Checkbox.Group>
                    {Object.entries(CHANNELS).map(([key, val]) => (
                        <Checkbox key={key} value={key}>{val}</Checkbox>
                    ))}
                </Checkbox.Group>
            </Form.Item>

            <Form.Item label="Quiet Start" name="quietStart">
                <TimePicker format="HH:mm" />
            </Form.Item>

            <Form.Item label="Quiet End" name="quietEnd">
                <TimePicker format="HH:mm" />
            </Form.Item>

            <Form.Item label="Enabled" name="enabled" valuePropName="checked">
                <Switch />
            </Form.Item>

            <Button type="primary" htmlType="submit" loading={submitting}>
                Save
            </Button>
        </Form>
    );
}
import React, { useEffect } from 'react';
import { Form, Select, Checkbox, TimePicker, Switch, Button, Tooltip } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import { EVENT_TYPES, CHANNELS } from '../utils/constants';
import { enumLabel } from '../utils/labels';
import dayjs from 'dayjs';

const { Option } = Select;

/**
 * 免打扰时段要么两端都填、要么都不填，且起止不能相同：
 * 只填一端时后端按「未设置」处理，start==end 则任何时刻都判不出「正在免打扰」——
 * 两种都是界面显示设好了、实际永远不生效（后端另有 QUIET_PERIOD_* 400 兜底）。
 */
const validateQuietPair = (start, end, intl) => {
    if (!start && !end) return Promise.resolve();
    if (!start || !end) {
        return Promise.reject(new Error(intl.formatMessage({ id: 'preferences.quietPeriodBothOrNone' })));
    }
    if (start.isSame(end, 'second')) {
        return Promise.reject(new Error(intl.formatMessage({ id: 'preferences.quietPeriodSameTime' })));
    }
    return Promise.resolve();
};

export default function PreferenceForm({ initialValues, onSave, form, onValuesChange }) {
    const intl = useIntl();
    const [submitting, setSubmitting] = React.useState(false);

    // 清除按钮只在至少填了一端时出现；useWatch 保证改动即时反映
    const quietStartField = Form.useWatch('quietStart', form);
    const quietEndField = Form.useWatch('quietEnd', form);

    const toDayjs = (value) => {
        if (!value) return null;
        if (dayjs.isDayjs(value)) return value;
        // 兼容后端返回的 HH:mm 与 HH:mm:ss 两种格式
        return dayjs(value, 'HH:mm:ss') || dayjs(value, 'HH:mm');
    };

    useEffect(() => {
        form.setFieldsValue({
            eventType: initialValues?.eventType || undefined,
            channels: initialValues?.channels || [],
            quietStart: toDayjs(initialValues?.quietStart),
            quietEnd: toDayjs(initialValues?.quietEnd),
            enabled: initialValues?.enabled ?? true,
        });
    }, [initialValues, form]);

    const handleFinish = async (values) => {
        setSubmitting(true);
        try {
            // 字段格式化（channels -> JSON 字符串、时间 -> HH:mm:ss）统一在调用方处理，
            // 此处只透传表单原始值，避免重复格式化导致的类型错误。
            await onSave?.(values);
        } finally {
            setSubmitting(false);
        }
    };

    const labeled = (id, tipId) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>{intl.formatMessage({ id })}</span>
            <Tooltip title={intl.formatMessage({ id: tipId })}>
                <InfoCircleOutlined style={{ fontSize: '14px', color: 'var(--color-text-tertiary)', cursor: 'help' }} />
            </Tooltip>
        </div>
    );

    return (
        <Form
            form={form}
            layout="vertical"
            onFinish={handleFinish}
            onValuesChange={onValuesChange}
            style={{ animation: 'fadeIn 0.3s ease' }}
        >
            <Form.Item
                label={labeled('preferences.eventType', 'preferences.eventTypeTip')}
                name="eventType"
                rules={[{ required: true, message: intl.formatMessage({ id: 'preferences.pleaseSelectEventType' }) }]}
                style={{ marginBottom: '20px' }}
            >
                <Select
                    style={{ width: '100%' }}
                    showSearch
                    optionFilterProp="children"
                >
                    {Object.keys(EVENT_TYPES).map((key) => (
                        <Option key={key} value={key}>{enumLabel(intl, 'eventType', key)}</Option>
                    ))}
                </Select>
            </Form.Item>

            <Form.Item
                label={labeled('preferences.channels', 'preferences.channelsTip')}
                name="channels"
                rules={[{ required: true, type: 'array', message: intl.formatMessage({ id: 'preferences.pleaseSelectChannels' }) }]}
                style={{ marginBottom: '20px' }}
            >
                <Checkbox.Group style={{ display: 'flex', flexWrap: 'wrap', gap: '12px' }}>
                    {Object.keys(CHANNELS).map((key) => (
                        <Checkbox
                            key={key}
                            value={key}
                            style={{
                                padding: '8px 16px',
                                borderRadius: 'var(--radius-md)',
                                background: 'var(--color-card-bg-light)',
                                borderColor: 'var(--color-border)',
                                transition: 'all var(--transition-fast)'
                            }}
                        >
                            {enumLabel(intl, 'channel', key)}
                        </Checkbox>
                    ))}
                </Checkbox.Group>
            </Form.Item>

            <div style={{
                background: 'var(--color-glass)',
                borderRadius: 'var(--radius-md)',
                padding: '16px',
                marginBottom: '20px',
                border: '1px solid var(--color-glass-border)'
            }}>
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    marginBottom: '16px'
                }}>
                    <span style={{ fontWeight: 500, color: 'var(--color-text-primary)' }}>
                        {intl.formatMessage({ id: 'preferences.quietPeriod' })}
                    </span>
                    <Tooltip title={intl.formatMessage({ id: 'preferences.quietPeriodTip' })}>
                        <InfoCircleOutlined style={{ fontSize: '14px', color: 'var(--color-text-tertiary)', cursor: 'help' }} />
                    </Tooltip>
                </div>
                <Form.Item
                    label={intl.formatMessage({ id: 'preferences.quietStart' })}
                    name="quietStart"
                    style={{ marginBottom: '12px' }}
                    dependencies={['quietEnd']}
                    rules={[({ getFieldValue }) => ({
                        validator: (_, value) =>
                            validateQuietPair(value, getFieldValue('quietEnd'), intl),
                    })]}
                >
                    <TimePicker format="HH:mm:ss" style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                    label={intl.formatMessage({ id: 'preferences.quietEnd' })}
                    name="quietEnd"
                    dependencies={['quietStart']}
                    rules={[({ getFieldValue }) => ({
                        validator: (_, value) =>
                            validateQuietPair(getFieldValue('quietStart'), value, intl),
                    })]}
                >
                    <TimePicker format="HH:mm:ss" style={{ width: '100%' }} />
                </Form.Item>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px' }}>
                    <span style={{ fontSize: '12px', color: 'var(--color-text-tertiary)', flex: 1 }}>
                        {intl.formatMessage({ id: 'preferences.quietPeriodHelp' })}
                    </span>
                    {(quietStartField || quietEndField) && (
                        <Button
                            size="small"
                            type="link"
                            onClick={() => form.setFieldsValue({ quietStart: null, quietEnd: null })}
                        >
                            {intl.formatMessage({ id: 'preferences.quietPeriodClear' })}
                        </Button>
                    )}
                </div>
            </div>

            <Form.Item
                label={labeled('preferences.enabled', 'preferences.enabledTip')}
                name="enabled"
                valuePropName="checked"
                style={{ marginBottom: '24px' }}
            >
                <Switch
                    checkedChildren={intl.formatMessage({ id: 'common.on' })}
                    unCheckedChildren={intl.formatMessage({ id: 'common.off' })}
                    style={{ background: 'var(--color-border)' }}
                />
            </Form.Item>

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
                <Button
                    type="default"
                    onClick={() => form.resetFields()}
                    style={{ padding: '10px 24px' }}
                >
                    {intl.formatMessage({ id: 'common.reset' })}
                </Button>
                <Button
                    type="primary"
                    htmlType="submit"
                    loading={submitting}
                    style={{ padding: '10px 24px' }}
                >
                    {submitting
                        ? intl.formatMessage({ id: 'common.saving' })
                        : intl.formatMessage({ id: 'common.save' })}
                </Button>
            </div>
        </Form>
    );
}

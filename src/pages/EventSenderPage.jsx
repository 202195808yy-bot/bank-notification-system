import React, { useState, useEffect } from 'react';
import { 
  Card, Button, Form, Input, Select, InputNumber, Typography, 
  Divider, Tag, Empty, message, Space, Progress, Alert, Row, Col,
  Tooltip, Spin
} from 'antd';
import { 
  SendOutlined, ReloadOutlined, RocketOutlined, TeamOutlined, 
  UserOutlined, ExclamationCircleOutlined, FileTextOutlined,
  LockOutlined, CheckCircleOutlined, CloseCircleOutlined,
  CreditCardOutlined
} from '@ant-design/icons';
import { useIntl } from 'react-intl';
import axios from '../api/axiosInstance';
import { EVENT_TYPE_COLORS } from '../utils/constants';

const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

const eventTemplates = {
  TRANSACTION: {
    account: '1234567890',
    amount: 1000.50,
    currency: 'RUB',
    transactionType: 'DEBIT',
    description: 'POS消费',
    merchant: '超市购物'
  },
  RISK_ALERT: {
    account: '1234567890',
    riskLevel: 'HIGH',
    riskType: '异地登录',
    ipAddress: '192.168.1.100',
    location: '北京市',
    device: 'iPhone 15'
  },
  BILL: {
    billType: '信用卡还款',
    amount: 5000.00,
    dueDate: '2024-02-15',
    account: '****6789'
  },
  LOGIN: {
    ipAddress: '192.168.1.1',
    device: 'Android',
    location: '上海市'
  },
  SECURITY: {
    alertType: '密码修改',
    timestamp: new Date().toISOString()
  }
};

const eventIcons = {
  TRANSACTION: CreditCardOutlined,
  RISK_ALERT: ExclamationCircleOutlined,
  BILL: FileTextOutlined,
  LOGIN: LockOutlined,
  SECURITY: CheckCircleOutlined
};

export default function EventSenderPage() {
  const intl = useIntl();
  const [form] = Form.useForm();
  const [eventType, setEventType] = useState('TRANSACTION');
  const [customData, setCustomData] = useState(JSON.stringify(eventTemplates.TRANSACTION, null, 2));
  const [sending, setSending] = useState(false);
  const [history, setHistory] = useState([]);
  const [response, setResponse] = useState(null);
  const [selectedCustomers, setSelectedCustomers] = useState([1]);
  const [batchProgress, setBatchProgress] = useState({ current: 0, total: 0, results: [] });
  const [isValidJson, setIsValidJson] = useState(true);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    return () => setMounted(false);
  }, []);

  useEffect(() => {
    try {
      JSON.parse(customData);
      setIsValidJson(true);
    } catch {
      setIsValidJson(false);
    }
  }, [customData]);

  const handleEventTypeChange = (value) => {
    setEventType(value);
    if (eventTemplates[value]) {
      setCustomData(JSON.stringify(eventTemplates[value], null, 2));
    } else {
      setCustomData('{}');
    }
  };

  const handleCustomerChange = (value) => {
    setSelectedCustomers(value);
  };

  const handleSelectAll = () => {
    setSelectedCustomers([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  };

  const handleClearSelection = () => {
    setSelectedCustomers([]);
  };

  const sendSingleEvent = async (customerId, eventData) => {
    try {
      await axios.post('/events', eventData, {
        headers: { 'Content-Type': 'application/json' }
      });
      return { customerId, success: true };
    } catch (error) {
      return { customerId, success: false, error: error.response?.data?.message || error.message };
    }
  };

  const handleBatchSubmit = async () => {
    if (selectedCustomers.length === 0) {
      message.error(intl.formatMessage({ id: 'event.selectAtLeastOneUser' }));
      return;
    }

    if (!isValidJson) {
      message.error(intl.formatMessage({ id: 'event.invalidJson' }));
      return;
    }

    setSending(true);
    setBatchProgress({ current: 0, total: selectedCustomers.length, results: [] });
    setResponse(null);

    const results = [];
    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < selectedCustomers.length; i++) {
      if (!mounted) break;
      
      const customerId = selectedCustomers[i];
      const payload = {
        eventId: 'evt-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9),
        eventType: eventType,
        customerId: customerId,
        ...JSON.parse(customData)
      };

      const result = await sendSingleEvent(customerId, payload);
      results.push(result);

      if (result.success) {
        successCount++;
      } else {
        failCount++;
      }

      setBatchProgress({
        current: i + 1,
        total: selectedCustomers.length,
        results: results
      });

      setHistory(prev => [{
        timestamp: new Date().toLocaleString(),
        eventType: eventType,
        customerId: customerId,
        eventId: payload.eventId,
        status: result.success ? 'SUCCESS' : 'FAILED'
      }, ...prev].slice(0, 50));
    }

    setSending(false);
    
    const newResponse = {
      success: failCount === 0,
      data: {
        total: selectedCustomers.length,
        success: successCount,
        failed: failCount
      },
      message: failCount === 0 
        ? intl.formatMessage({ id: 'event.allSentSuccessfully' }) 
        : intl.formatMessage({ id: 'event.successFailedCount' }, { success: successCount, failed: failCount })
    };
    setResponse(newResponse);

    if (failCount === 0) {
      message.success(`${intl.formatMessage({ id: 'event.sendSuccess' })} - ${successCount} ${intl.formatMessage({ id: 'event.user' })}`);
    } else {
      message.warning(intl.formatMessage({ id: 'event.successFailedCount' }, { success: successCount, failed: failCount }));
    }

    window.dispatchEvent(new CustomEvent('bank-event-sent', {
      detail: {
        eventType,
        customerId: selectedCustomers,
        successCount,
        failCount,
        timestamp: new Date()
      }
    }));
  };

  const handleQuickSend = async (type) => {
    if (selectedCustomers.length === 0) {
      message.warning(intl.formatMessage({ id: 'event.pleaseSelectUsersFirst' }));
      return;
    }

    const templateData = eventTemplates[type] || {};
    setEventType(type);
    setCustomData(JSON.stringify(templateData, null, 2));
    setSending(true);
    setBatchProgress({ current: 0, total: selectedCustomers.length, results: [] });
    setResponse(null);

    const results = [];
    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < selectedCustomers.length; i++) {
      if (!mounted) break;
      
      const customerId = selectedCustomers[i];
      const payload = {
        eventId: 'evt-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9),
        eventType: type,
        customerId: customerId,
        ...templateData
      };

      const result = await sendSingleEvent(customerId, payload);
      results.push(result);

      if (result.success) {
        successCount++;
      } else {
        failCount++;
      }

      setBatchProgress({
        current: i + 1,
        total: selectedCustomers.length,
        results: results
      });

      setHistory(prev => [{
        timestamp: new Date().toLocaleString(),
        eventType: type,
        customerId: customerId,
        eventId: payload.eventId,
        status: result.success ? 'SUCCESS' : 'FAILED'
      }, ...prev].slice(0, 50));
    }

    setSending(false);
    
    const newResponse = {
      success: failCount === 0,
      data: {
        total: selectedCustomers.length,
        success: successCount,
        failed: failCount
      },
      message: failCount === 0 
        ? intl.formatMessage({ id: 'event.allSentSuccessfully' }) 
        : intl.formatMessage({ id: 'event.successFailedCount' }, { success: successCount, failed: failCount })
    };
    setResponse(newResponse);

    if (failCount === 0) {
      message.success(`${intl.formatMessage({ id: 'event.sendSuccess' })} - ${successCount} ${intl.formatMessage({ id: 'event.user' })}`);
    } else {
      message.warning(intl.formatMessage({ id: 'event.successFailedCount' }, { success: successCount, failed: failCount }));
    }

    window.dispatchEvent(new CustomEvent('bank-event-sent', {
      detail: {
        eventType: type,
        customerId: selectedCustomers,
        successCount,
        failCount,
        timestamp: new Date()
      }
    }));
  };

  const handleReset = () => {
    form.resetFields();
    setEventType('TRANSACTION');
    setCustomData(JSON.stringify(eventTemplates.TRANSACTION, null, 2));
    setResponse(null);
    setSelectedCustomers([1]);
    setBatchProgress({ current: 0, total: 0, results: [] });
  };

  const quickButtons = [
    { type: 'TRANSACTION', label: intl.formatMessage({ id: 'event.transaction' }), color: 'blue' },
    { type: 'RISK_ALERT', label: intl.formatMessage({ id: 'event.riskAlert' }), color: 'red' },
    { type: 'BILL', label: intl.formatMessage({ id: 'event.bill' }), color: 'green' }
  ];

  const renderBatchProgress = () => {
    if (batchProgress.total === 0 || !sending) return null;
    const percent = Math.round((batchProgress.current / batchProgress.total) * 100);
    return (
      <Alert
        type="info"
        showIcon
        icon={<TeamOutlined />}
        message={intl.formatMessage({ id: 'event.sendingTo' }, { current: batchProgress.current, total: batchProgress.total })}
        description={<Progress percent={percent} status="active" size="small" />}
        style={{ marginBottom: '16px' }}
      />
    );
  };

  const getEventIcon = (type) => {
    const IconComponent = eventIcons[type];
    return IconComponent ? <IconComponent /> : <CreditCardOutlined />;
  };

  const getEventColor = (type) => {
    return EVENT_TYPE_COLORS[type]?.tag || 'blue';
  };

  return (
    <div style={{ animation: 'fadeIn 0.3s ease' }}>
      <div style={{ marginBottom: '24px' }}>
        <Title level={2} style={{ marginBottom: '8px' }}>
          <RocketOutlined style={{ marginRight: '12px', color: '#1677ff' }} />
          {intl.formatMessage({ id: 'event.title' })}
        </Title>
        <Paragraph style={{ color: 'rgba(0, 0, 0, 0.65)' }}>
          {intl.formatMessage({ id: 'event.hint' })}
        </Paragraph>
      </div>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={8}>
          <Card title={
            <span>
              <UserOutlined style={{ marginRight: '8px', color: '#1677ff' }} />
              {intl.formatMessage({ id: 'event.batchSelect' })}
            </span>
          }>
            <Space style={{ marginBottom: '16px', width: '100%' }} direction="vertical" size="middle">
              <div>
                <Text strong>{intl.formatMessage({ id: 'event.selectTargetUsers' })}</Text>
                <Space style={{ marginTop: '8px' }}>
                  <Button size="small" onClick={handleSelectAll} icon={<UserOutlined />}>
                    {intl.formatMessage({ id: 'event.selectAll' })}
                  </Button>
                  <Button size="small" onClick={handleClearSelection}>
                    {intl.formatMessage({ id: 'event.clearSelection' })}
                  </Button>
                </Space>
              </div>
              <Tag color={selectedCustomers.length > 0 ? 'blue' : 'default'}>
                {intl.formatMessage({ id: 'event.usersSelected' }, { count: selectedCustomers.length })}
              </Tag>
              <Select
                mode="multiple"
                value={selectedCustomers}
                onChange={handleCustomerChange}
                style={{ width: '100%' }}
                placeholder={intl.formatMessage({ id: 'event.selectUsersPlaceholder' })}
                maxTagCount={5}
                size="large"
              >
                {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(id => (
                  <Option key={id} value={id}>{intl.formatMessage({ id: 'event.user' })} {id}</Option>
                ))}
              </Select>
            </Space>
          </Card>

          <Card title={
            <span>
              <CreditCardOutlined style={{ marginRight: '8px', color: '#faad14' }} />
              {intl.formatMessage({ id: 'event.quickSend' })}
            </span>
          }>
            <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
              {quickButtons.map(item => (
                <Button
                  key={item.type}
                  type="primary"
                  danger={item.color === 'red'}
                  onClick={() => handleQuickSend(item.type)}
                  disabled={sending || selectedCustomers.length === 0}
                  icon={getEventIcon(item.type)}
                >
                  {item.label}
                </Button>
              ))}
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={16}>
          <Card title={
            <span>
              <FileTextOutlined style={{ marginRight: '8px', color: '#1677ff' }} />
              {intl.formatMessage({ id: 'event.customEvent' })}
            </span>
          }>
            <Form form={form} layout="vertical">
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    label={intl.formatMessage({ id: 'event.type' })}
                    name="eventType"
                    rules={[{ required: true, message: intl.formatMessage({ id: 'event.typeRequired' }) }]}
                  >
                    <Select
                      value={eventType}
                      onChange={handleEventTypeChange}
                      style={{ width: '100%' }}
                      size="large"
                    >
                      <Option value="TRANSACTION">
                        <Space>
                          <CreditCardOutlined style={{ color: EVENT_TYPE_COLORS.TRANSACTION?.text || '#1677ff' }} />
                          TRANSACTION - {intl.formatMessage({ id: 'event.transaction' })}
                        </Space>
                      </Option>
                      <Option value="RISK_ALERT">
                        <Space>
                          <ExclamationCircleOutlined style={{ color: EVENT_TYPE_COLORS.RISK_ALERT?.text || '#ff4d4f' }} />
                          RISK_ALERT - {intl.formatMessage({ id: 'event.riskAlert' })}
                        </Space>
                      </Option>
                      <Option value="BILL">
                        <Space>
                          <FileTextOutlined style={{ color: EVENT_TYPE_COLORS.BILL?.text || '#52c41a' }} />
                          BILL - {intl.formatMessage({ id: 'event.bill' })}
                        </Space>
                      </Option>
                      <Option value="LOGIN">
                        <Space>
                          <LockOutlined style={{ color: EVENT_TYPE_COLORS.LOGIN?.text || '#1677ff' }} />
                          LOGIN - {intl.formatMessage({ id: 'event.login' })}
                        </Space>
                      </Option>
                      <Option value="SECURITY">
                        <Space>
                          <CheckCircleOutlined style={{ color: EVENT_TYPE_COLORS.SECURITY?.text || '#52c41a' }} />
                          SECURITY - {intl.formatMessage({ id: 'event.security' })}
                        </Space>
                      </Option>
                    </Select>
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label={intl.formatMessage({ id: 'event.previewData' })}>
                    <div 
                      style={{ 
                        padding: '12px', 
                        background: EVENT_TYPE_COLORS[eventType]?.bg || '#f5f5f5',
                        borderRadius: '8px',
                        borderLeft: `3px solid ${EVENT_TYPE_COLORS[eventType]?.border || '#1890ff'}`,
                        fontSize: '12px',
                        fontFamily: 'monospace',
                        maxHeight: '60px',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis'
                      }}
                    >
                      {customData.substring(0, 100)}...
                    </div>
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item 
                label={intl.formatMessage({ id: 'event.data' })}
                validateStatus={isValidJson ? 'success' : 'error'}
                help={isValidJson ? '' : intl.formatMessage({ id: 'event.invalidJson' })}
              >
                <Input.TextArea
                  value={customData}
                  onChange={(e) => setCustomData(e.target.value)}
                  rows={8}
                  style={{ fontFamily: 'monospace' }}
                  placeholder={intl.formatMessage({ id: 'event.jsonPlaceholder' })}
                  autoSize={{ minRows: 6, maxRows: 12 }}
                />
              </Form.Item>

              {renderBatchProgress()}

              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                <Button
                  type="primary"
                  onClick={handleBatchSubmit}
                  loading={sending}
                  icon={<TeamOutlined />}
                  disabled={selectedCustomers.length === 0 || !isValidJson}
                  size="large"
                >
                  {sending 
                    ? intl.formatMessage({ id: 'event.sending' }, { current: batchProgress.current, total: batchProgress.total }) 
                    : intl.formatMessage({ id: 'event.sendToUsers' }, { count: selectedCustomers.length })}
                </Button>
                <Button onClick={handleReset} icon={<ReloadOutlined />} size="large">
                  {intl.formatMessage({ id: 'common.reset' })}
                </Button>
              </div>
            </Form>
          </Card>

          {response && (
            <Card title={
              <span>
                {response.success ? (
                  <CheckCircleOutlined style={{ marginRight: '8px', color: '#52c41a' }} />
                ) : (
                  <CloseCircleOutlined style={{ marginRight: '8px', color: '#ff4d4f' }} />
                )}
                {intl.formatMessage({ id: 'event.result' })}
              </span>
            }>
              <div
                style={{
                  padding: '16px',
                  borderRadius: '8px',
                  backgroundColor: response.success ? '#d4edda' : '#fff3cd',
                  border: `1px solid ${response.success ? '#c3e6cb' : '#ffeaa7'}`,
                  fontFamily: 'monospace'
                }}
              >
                {response.success ? (
                  <>
                    <Text type="success" strong style={{ fontSize: '16px' }}>{intl.formatMessage({ id: 'event.check' })} {response.message}</Text>
                    <div style={{ marginTop: '12px', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                      <Tag color="green">{intl.formatMessage({ id: 'common.success' })}: {response.data.success}</Tag>
                      <Tag color="red">{intl.formatMessage({ id: 'common.failed' })}: {response.data.failed}</Tag>
                      <Tag color="blue">{intl.formatMessage({ id: 'event.totalRecords' }, { count: response.data.total })}</Tag>
                    </div>
                  </>
                ) : (
                  <Text type="warning">{response.message}</Text>
                )}
              </div>
            </Card>
          )}
        </Col>
      </Row>

      <Card title={
        <span>
          <SendOutlined style={{ marginRight: '8px', color: '#1677ff' }} />
          {intl.formatMessage({ id: 'event.history' })}
        </span>
      } style={{ marginTop: '24px' }}>
        {history.length === 0 ? (
            <Empty description={intl.formatMessage({ id: 'event.noHistory' })} />
          ) : (
            <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
              <div style={{ marginBottom: '12px', color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px' }}>
                {intl.formatMessage({ id: 'event.totalRecords' }, { count: history.length })}
              </div>
              {history.map((item, index) => (
                <div
                  key={index}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '12px',
                    borderBottom: '1px solid #f0f0f0',
                    backgroundColor: index === 0 ? '#f0f9ff' : 'transparent',
                    transition: 'background-color 0.2s ease',
                    borderRadius: index === 0 ? '8px 8px 0 0' : '0',
                    animation: `fadeIn 0.3s ease ${index * 0.05}s both`
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#fafafa'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = index === 0 ? '#f0f9ff' : 'transparent'}
                >
                  <div>
                    <Tag color={getEventColor(item.eventType)}>
                      {item.eventType}
                    </Tag>
                    <Text strong style={{ marginLeft: '12px' }}>
                      {intl.formatMessage({ id: 'event.user' })} {item.customerId}
                    </Text>
                    <Text style={{ marginLeft: '12px', color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px' }}>
                      {item.eventId}
                    </Text>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <Tag color={item.status === 'SUCCESS' ? 'green' : 'red'}>
                      {item.status === 'SUCCESS' ? intl.formatMessage({ id: 'common.success' }) : intl.formatMessage({ id: 'common.failed' })}
                    </Tag>
                    <Text type="secondary" style={{ fontSize: '12px' }}>
                      {item.timestamp}
                    </Text>
                  </div>
                </div>
              ))}
            </div>
          )}
      </Card>

      <Card style={{ 
        background: '#e6f4ff',
        border: '1px solid #91caff',
        marginTop: '24px'
      }}>
        <Paragraph>
          {intl.formatMessage({ id: 'event.featureTip' })}
        </Paragraph>
      </Card>
    </div>
  );
}
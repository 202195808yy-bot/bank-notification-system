import { useState, useEffect, useMemo } from 'react';
import { 
  Card, Button, Form, Input, Select, Typography, 
  Divider, Tag, Empty, message, Space, Progress, Alert, Row, Col
} from 'antd';
import { 
  SendOutlined, ReloadOutlined, RocketOutlined, TeamOutlined, 
  UserOutlined, ExclamationCircleOutlined, FileTextOutlined,
  LockOutlined, CheckCircleOutlined, CloseCircleOutlined,
  CreditCardOutlined, GiftOutlined
} from '@ant-design/icons';
import { useIntl } from 'react-intl';
import axios from '../api/axiosInstance';
import { errorText } from '../i18n';
import { listCustomers, getEventReach } from '../api/customerApi';
import { getTemplates } from '../api/templateApi';
import { EVENT_TYPE_COLORS } from '../utils/constants';

const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

const eventTemplates = {
  TRANSACTION: {
    amount: 1000.50,
    // 库里的 TRANSACTION 模板正文用到 {{balance}}；样例载荷缺这个键，
    // 以前会把占位符原样渲染进通知发给客户（PRD-35）。
    balance: 25430.00,
    currency: 'RUB',
    transactionType: 'DEBIT',
    description: 'POS消费',
    merchant: '超市购物'
  },
  RISK_ALERT: {
    riskLevel: 'HIGH',
    riskType: '异地登录',
    ipAddress: '192.168.1.100',
    location: '北京市',
    device: 'iPhone 15'
  },
  BILL: {
    billType: '信用卡还款',
    amount: 5000.00,
    dueDate: '2024-02-15'
  },
  LOGIN: {
    ipAddress: '192.168.1.1',
    device: 'Android',
    location: '上海市'
  },
  SECURITY: {
    alertType: '密码修改',
    timestamp: new Date().toISOString()
  },
  // 库里 PROMOTION 的 9 行正文是无变量的固定营销文案，所以样例载荷就是空对象：
  // 填任何键都不会出现在通知里（此前这一类在界面上根本选不到）。
  PROMOTION: {}
};

const eventIcons = {
  TRANSACTION: CreditCardOutlined,
  RISK_ALERT: ExclamationCircleOutlined,
  BILL: FileTextOutlined,
  LOGIN: LockOutlined,
  SECURITY: CheckCircleOutlined,
  PROMOTION: GiftOutlined
};

// 与后端 TemplateRenderService.UNRESOLVED_PLACEHOLDER 同一套语法：载荷里缺哪个键，
// 派发时就会被 N19 拦成 FAILED_VALIDATION/VARIABLE_MISSING，所以录入时就先拦住（PRD-35 ①）。
const PLACEHOLDER = /\{\{\s*([\w.]+)\s*\}\}/g;

// 界面语言（react-intl 的短码）→ 模板语料里的 locale 三段
const UI_TO_TEMPLATE_LOCALE = { ru: 'ru_RU', en: 'en_US', zh: 'zh_CN' };
const TEMPLATE_LOCALES = ['ru_RU', 'en_US', 'zh_CN'];
const TEMPLATE_LOCALE_LABELS = { ru_RU: 'Русский', en_US: 'English', zh_CN: '中文' };
// 与后端 EventTransformer 的 EMAIL / PHONE 同一套判据，差别只在这里提前拦一次
const DIRECT_EMAIL = /^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]{2,}$/;
const DIRECT_PHONE = /^\+?[0-9][0-9 \-()]{6,18}$/;

// {{account}} 不在这里要：派发端按收件人自己解析（档案账号 → 载荷 → 收件地址掩码）。
// 让管理员在载荷里填一个账号，等于把同一个账号投给该事件命中的所有收件人（PRD-57/N60）。
const DISPATCH_RESOLVED_VARS = ['account'];

const templateVarsOf = (templates, eventType) => {
  const names = new Set();
  templates
    .filter((tpl) => tpl && tpl.eventType === eventType)
    .forEach((tpl) => {
      [tpl.titleTemplate, tpl.bodyTemplate].forEach((text) => {
        if (!text) return;
        String(text).replace(PLACEHOLDER, (_m, name) => {
          names.add(name);
          return _m;
        });
      });
    });
  return [...names];
};

const variablesOf = (templates, eventType) =>
  templateVarsOf(templates, eventType).filter((name) => !DISPATCH_RESOLVED_VARS.includes(name));

export default function EventSenderPage() {
  const intl = useIntl();
  const [form] = Form.useForm();
  const [eventType, setEventType] = useState('TRANSACTION');
  const [customData, setCustomData] = useState(JSON.stringify(eventTemplates.TRANSACTION, null, 2));
  const [sending, setSending] = useState(false);
  const [history, setHistory] = useState([]);
  const [response, setResponse] = useState(null);
  const [selectedCustomers, setSelectedCustomers] = useState([]);
  const [batchProgress, setBatchProgress] = useState({ current: 0, total: 0, results: [] });
  const [isValidJson, setIsValidJson] = useState(true);
  const [mounted, setMounted] = useState(false);

  // 真实用户目录：以前这里硬编码 [1..10]，其中大部分 id 库里根本不存在，
  // 发出去就是指向不存在客户的孤儿通知行（PRD-33），而且界面上只写着「用户 7」看不出是谁。
  const [customers, setCustomers] = useState([]);
  const [directoryLoading, setDirectoryLoading] = useState(true);
  const [directoryError, setDirectoryError] = useState(null);

  // 模板正文里用到的变量集，用来在录入时就拦住缺变量的事件（否则要到派发期才失败）
  const [templates, setTemplates] = useState([]);

  // 「输入谁的手机号/邮箱就发给谁」（PRD-51/N47）：填了就**只发这一个地址**，
  // 后端不看订阅偏好、不看客户档案里存没存这个号码、也不判免打扰时段。
  // 渠道由地址形状决定（含 @ 即邮件），语言必须显式选 —— 该地址未必属于任何客户，
  // 没有 locale 可推断，而模板语料是 ru_RU / en_US / zh_CN 三本。
  const [directRecipient, setDirectRecipient] = useState('');
  const [directLocale, setDirectLocale] = useState(
    () => UI_TO_TEMPLATE_LOCALE[intl.locale] || 'ru_RU'
  );

  // 「这个事件类型到底有几个人收得到」——派发完才能在历史页看到 NOT_SUBSCRIBED，
  // 用户的第一反应就是"消息丢了"。这里把同一份判断前移到点发送之前（PRD-41）。
  const [reach, setReach] = useState(null);

  useEffect(() => {
    setMounted(true);
    return () => setMounted(false);
  }, []);

  useEffect(() => {
    let cancelled = false;
    listCustomers()
      .then((data) => {
        if (cancelled) return;
        setCustomers(Array.isArray(data) ? data : []);
        setDirectoryError(null);
      })
      .catch((error) => {
        if (cancelled) return;
        setCustomers([]);
        setDirectoryError(error.response?.status === 403 ? 'ADMIN_REQUIRED' : 'DIRECTORY_UNAVAILABLE');
      })
      .finally(() => {
        if (!cancelled) setDirectoryLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    getTemplates()
      .then((data) => {
        if (!cancelled) setTemplates(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!cancelled) setTemplates([]);
      });
    return () => { cancelled = true; };
  }, []);

  const customerLabel = (customerId) => {
    const found = customers.find((c) => c.id === customerId);
    if (!found) return `${intl.formatMessage({ id: 'event.user' })} ${customerId}`;
    return found.name || found.email || `${intl.formatMessage({ id: 'event.user' })} ${customerId}`;
  };

  useEffect(() => {
    if (!eventType) {
      setReach(null);
      return undefined;
    }
    let cancelled = false;
    // 失败（含非 ADMIN 的 403）一律静默降级：这只是发送前的提示，不能挡住主流程
    getEventReach(eventType)
      .then((data) => { if (!cancelled) setReach(data); })
      .catch(() => { if (!cancelled) setReach(null); });
    return () => { cancelled = true; };
  }, [eventType]);

  useEffect(() => {
    try {
      JSON.parse(customData);
      setIsValidJson(true);
    } catch {
      setIsValidJson(false);
    }
  }, [customData]);

  const requiredVars = variablesOf(templates, eventType);
  // 模板用到但由派发端解析的变量（account）：不要求管理员填，但要说明值从哪来，
  // 否则"这条通知里的账号是谁的"没人答得上来（PRD-57）
  const autoResolvedVars = templateVarsOf(templates, eventType).filter((name) => DISPATCH_RESOLVED_VARS.includes(name));
  const templatesOf = (type) => templates.filter((tpl) => tpl && tpl.eventType === type);

  /**
   * 发送前摘要：一个事件真要落到客户手上，得同时满足「订阅了且开关打开 × 有该渠道的收件地址 ×
   * 该「事件类型+渠道+客户语言」有模板」。少任何一个分别就是历史页里的 NOT_SUBSCRIBED /
   * PREFERENCE_DISABLED / NO_PHONE·NO_EMAIL·NO_PUSH_TOKEN / TEMPLATE_MISSING ——
   * 这几行在界面上看着都像 bug，提前说出来比让用户去考古便宜（PRD-41）。
   */
  const reachSummary = useMemo(() => {
    if (!reach) return null;
    const withTemplate = new Set(
      templates.filter((t) => t && t.eventType === eventType && t.channel).map((t) => t.channel)
    );
    let deliverable = 0;
    let blocked = 0;
    let notSubscribed = 0;
    (reach.customers || []).forEach((c) => {
      if (!c.subscribed || !c.enabled) {
        notSubscribed += 1;
        return;
      }
      const usable = (c.channels || []).filter(
        (ch) => !(c.unreachableChannels || []).includes(ch) && withTemplate.has(ch)
      );
      if (usable.length > 0) deliverable += 1; else blocked += 1;
    });
    return {
      deliverable,
      blocked,
      notSubscribed,
      total: (reach.customers || []).length,
      templateChannels: Array.from(withTemplate),
    };
  }, [reach, templates, eventType]);

  /** 返回缺失的模板变量名；载荷 JSON 本身不合法时返回 null，交给既有的 invalidJson 分支处理 */
  const missingVars = (data) => {
    if (!isValidJson) return null;
    const payload = data ?? JSON.parse(customData || '{}');
    return requiredVars.filter((name) => !(name in payload));
  };

  const blockMissingVars = (vars) => {
    if (!vars || vars.length === 0) return false;
    message.error(intl.formatMessage({ id: 'event.missingTemplateVars' }, { vars: vars.join(', ') }));
    return true;
  };

  const pendingMissingVars = missingVars();

  // 直发模式的派生量：渠道按形状推断，格式不合法时按钮直接禁用并把原因写在必填星号旁
  const directTarget = directRecipient.trim();
  const directMode = directTarget.length > 0;
  const directChannel = directMode ? (directTarget.includes('@') ? 'EMAIL' : 'SMS') : null;
  const directInvalid = directMode
    && !(directChannel === 'EMAIL' ? DIRECT_EMAIL.test(directTarget) : DIRECT_PHONE.test(directTarget));

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
    setSelectedCustomers(customers.map((c) => c.id));
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
      // 后端业务错误现在是 {code: '…'}（400 校验失败 / 503 没能进队列）。
      // 译码这件事全站只有一处实现（i18n 的 errorText），此前这里另写了一份查表逻辑
      const data = error.response?.data;
      return { customerId, success: false, error: errorText(data?.code) || data?.message || error.message };
    }
  };

  /**
   * 直发（PRD-51/N47）：一次一个事件、一个手填地址。
   * 请求体里**故意不带 customerId** —— 后端拿网关注入的 X-User-Id 作为该通知行的归属人，
   * 于是这条记录在历史页里既看得见、又标得清是"谁发的、发去了哪个地址"。
   */
  const sendDirectEvent = async (dataOverride, typeOverride) => {
    if (!isValidJson) {
      message.error(intl.formatMessage({ id: 'event.invalidJson' }));
      return;
    }
    if (directInvalid) {
      message.error(intl.formatMessage(
        { id: 'event.directInvalid' },
        { channel: intl.formatMessage({ id: `enum.channel.${directChannel}` }) }
      ));
      return;
    }
    const vars = dataOverride ?? JSON.parse(customData || '{}');
    const type = typeOverride ?? eventType;
    const missing = typeOverride
      ? variablesOf(templates, typeOverride).filter((name) => !(name in vars))
      : missingVars(vars);
    if (blockMissingVars(missing)) return;

    const payload = {
      eventId: 'evt-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9),
      eventType: type,
      directRecipient: directTarget,
      directChannel: directChannel,
      directLocale: directLocale,
      ...vars
    };

    setSending(true);
    setResponse(null);
    const result = await sendSingleEvent(null, payload);
    if (!mounted) return;

    setHistory(prev => [{
      timestamp: new Date().toLocaleString(),
      eventType: type,
      customerId: null,
      target: `${directTarget} · ${directChannel}`,
      eventId: payload.eventId,
      status: result.success ? 'SUCCESS' : 'FAILED'
    }, ...prev].slice(0, 50));

    setResponse({
      success: result.success,
      data: { total: 1, success: result.success ? 1 : 0, failed: result.success ? 0 : 1 },
      message: result.success
        ? intl.formatMessage({ id: 'event.directSent' }, { recipient: directTarget })
        : `${intl.formatMessage({ id: 'event.directFailed' })}: ${result.error}`
    });
    setSending(false);
  };

  // 主表单的批量发送与「快速发送」按钮共用这一段：此前是两份逐字复制的循环，
  // 任何一处单独改动都会让两个入口的行为不一致（历史列表字段以前就是这么错开的）
  const sendToSelectedCustomers = async (type, data) => {
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
        ...data
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

  const handleBatchSubmit = async () => {
    if (directMode) {
      await sendDirectEvent();
      return;
    }

    if (selectedCustomers.length === 0) {
      message.error(intl.formatMessage({ id: 'event.selectAtLeastOneUser' }));
      return;
    }

    if (!isValidJson) {
      message.error(intl.formatMessage({ id: 'event.invalidJson' }));
      return;
    }

    if (blockMissingVars(missingVars())) return;

    await sendToSelectedCustomers(eventType, JSON.parse(customData));
  };

  const handleQuickSend = async (type) => {
    if (!directMode && selectedCustomers.length === 0) {
      message.warning(intl.formatMessage({ id: 'event.pleaseSelectUsersFirst' }));
      return;
    }

    const templateData = eventTemplates[type] || {};
    if (blockMissingVars(variablesOf(templates, type).filter((name) => !(name in templateData)))) return;

    setEventType(type);
    // 快速发送按钮绕过了下拉框，不同步表单值的话下拉会停在原类型上（显示与实际发送不一致）
    form.setFieldValue('eventType', type);
    setCustomData(JSON.stringify(templateData, null, 2));

    // 直发模式下"快速发送"同样发给手填地址（用户要的是"输入谁的号码就发给谁"，
    // 不是"只有大按钮才认这个地址"）
    if (directMode) {
      await sendDirectEvent(templateData, type);
      return;
    }

    await sendToSelectedCustomers(type, templateData);
  };

  const handleReset = () => {
    form.resetFields();
    setEventType('TRANSACTION');
    setCustomData(JSON.stringify(eventTemplates.TRANSACTION, null, 2));
    setResponse(null);
    setSelectedCustomers([]);
    setDirectRecipient('');
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
                  <Button
                    size="small"
                    onClick={handleSelectAll}
                    icon={<UserOutlined />}
                    disabled={customers.length === 0}
                  >
                    {intl.formatMessage({ id: 'event.selectAll' }, { count: customers.length })}
                  </Button>
                  <Button size="small" onClick={handleClearSelection}>
                    {intl.formatMessage({ id: 'event.clearSelection' })}
                  </Button>
                </Space>
              </div>
              <Tag color={selectedCustomers.length > 0 ? 'blue' : 'default'}>
                {intl.formatMessage({ id: 'event.usersSelected' }, { count: selectedCustomers.length })}
              </Tag>
              {directoryError && (
                <Alert
                  type="warning"
                  showIcon
                  message={intl.formatMessage({ id: 'event.userDirectoryEmpty' })}
                  description={directoryError}
                  style={{ marginTop: '8px' }}
                />
              )}
              <Select
                mode="multiple"
                value={selectedCustomers}
                onChange={handleCustomerChange}
                style={{ width: '100%' }}
                placeholder={intl.formatMessage({ id: 'event.selectUsersPlaceholder' })}
                maxTagCount={5}
                size="large"
                loading={directoryLoading}
                disabled={customers.length === 0}
                optionFilterProp="label"
                options={customers.map((c) => ({
                  value: c.id,
                  label: `${c.name || `#${c.id}`}${c.email ? ` · ${c.email}` : ''}`,
                }))}
                optionRender={(option) => {
                  const c = customers.find((x) => x.id === option.data.value);
                  return (
                    <Space direction="vertical" size={0}>
                      <Text strong>{c?.name || `#${c?.id}`}</Text>
                      <Text type="secondary" style={{ fontSize: '12px' }}>
                        {c?.email || '—'} · {c?.role}
                      </Text>
                      <Space size={4}>
                        {[
                          ['SMS', c?.hasPhone],
                          ['EMAIL', c?.hasEmail],
                          ['PUSH', c?.hasPushToken],
                        ].map(([channel, ready]) => (
                          <Tag
                            key={channel}
                            color={ready ? 'green' : 'default'}
                            style={{ margin: 0, opacity: ready ? 1 : 0.55 }}
                          >
                            {channel}
                          </Tag>
                        ))}
                      </Space>
                    </Space>
                  );
                }}
              />
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
                  // handleQuickSend 在直发模式下走 sendDirectEvent，压根不读勾选；
                  // 这里的条件必须与它一致，否则同一屏会出现"大按钮可点、快速按钮全灰"的自相矛盾
                  disabled={sending || (!directMode && selectedCustomers.length === 0)}
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
                  {/* Form.Item 会用表单 store 的值覆盖子元素的 value，而 store 初始是 undefined
                      → 界面显示空，而 eventType 状态其实是 TRANSACTION（发到后端的也是 TRANSACTION）。
                      不写 initialValue 就是"看不见但照发"，必须在界面上如实显示出来。 */}
                  <Form.Item
                    label={intl.formatMessage({ id: 'event.type' })}
                    name="eventType"
                    initialValue="TRANSACTION"
                    rules={[{ required: true, message: intl.formatMessage({ id: 'event.typeRequired' }) }]}
                  >
                    <Select
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
                      <Option value="PROMOTION">
                        <Space>
                          <GiftOutlined style={{ color: EVENT_TYPE_COLORS.PROMOTION?.text || '#722ed1' }} />
                          PROMOTION - {intl.formatMessage({ id: 'event.promotion' })}
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

              {templatesOf(eventType).length === 0 ? (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={intl.formatMessage({ id: 'event.noTemplateForType' }, { type: eventType })}
                />
              ) : (
                <Alert
                  type={pendingMissingVars && pendingMissingVars.length > 0 ? 'error' : 'info'}
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={intl.formatMessage({ id: 'event.templateVars' }, { vars: requiredVars.join(', ') })}
                  description={
                    pendingMissingVars && pendingMissingVars.length > 0
                      ? intl.formatMessage({ id: 'event.missingTemplateVars' }, { vars: pendingMissingVars.join(', ') })
                      : undefined
                  }
                />
              )}

              {autoResolvedVars.length > 0 && (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={intl.formatMessage({ id: 'event.autoResolvedVars' }, { vars: autoResolvedVars.join(', ') })}
                />
              )}

              {reachSummary && (
                <Alert
                  type={reachSummary.deliverable > 0 ? 'info' : 'warning'}
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={intl.formatMessage(
                    { id: 'event.reachSummary' },
                    {
                      deliverable: reachSummary.deliverable,
                      blocked: reachSummary.blocked,
                      notSubscribed: reachSummary.notSubscribed,
                    }
                  )}
                  description={
                    reachSummary.deliverable === 0
                      ? intl.formatMessage({ id: 'event.reachNothing' })
                      : undefined
                  }
                />
              )}

              <Divider orientation="left" style={{ marginTop: 24 }}>
                {intl.formatMessage({ id: 'event.directDivider' })}
              </Divider>
              <Text type="secondary" style={{ display: 'block', marginBottom: 8, fontSize: 12 }}>
                {intl.formatMessage({ id: 'event.directHint' })}
              </Text>
              <Space style={{ width: '100%' }} direction="vertical" size="small">
                <Input
                  value={directRecipient}
                  onChange={(e) => setDirectRecipient(e.target.value)}
                  placeholder={intl.formatMessage({ id: 'event.directPlaceholder' })}
                  size="large"
                  style={{ width: '100%', maxWidth: 420 }}
                  status={directInvalid ? 'error' : undefined}
                  allowClear
                />
                {directMode && (
                  <Space size="small" wrap>
                    <Tag color={directInvalid ? 'red' : 'blue'}>
                      {intl.formatMessage({ id: `enum.channel.${directChannel}` })}
                    </Tag>
                    <Select
                      value={directLocale}
                      onChange={setDirectLocale}
                      style={{ width: 160 }}
                      options={TEMPLATE_LOCALES.map((l) => ({
                        value: l,
                        // 语言名按本地语言的惯例显示（与顶栏语言切换器同一口径），不翻译
                        label: TEMPLATE_LOCALE_LABELS[l]
                      }))}
                    />
                  </Space>
                )}
              </Space>

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
                  icon={directMode ? <SendOutlined /> : <TeamOutlined />}
                  disabled={(directMode ? directInvalid : selectedCustomers.length === 0) || !isValidJson}
                  size="large"
                >
                  {sending
                    ? intl.formatMessage({ id: 'event.sending' }, { current: batchProgress.current, total: batchProgress.total })
                    : directMode
                      ? intl.formatMessage({ id: 'event.directSendButton' }, { recipient: directTarget })
                      : intl.formatMessage({ id: 'event.sendToUsers' }, { count: selectedCustomers.length })}
                </Button>
                <Button onClick={handleReset} icon={<ReloadOutlined />} size="large">
                  {intl.formatMessage({ id: 'common.reset' })}
                </Button>
              </div>

              <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
                {intl.formatMessage({ id: 'event.deliveryTip' })}
              </Text>
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
                      {item.target || customerLabel(item.customerId)}
                    </Text>
                    <Text style={{ marginLeft: '12px', color: 'rgba(0, 0, 0, 0.65)', fontSize: '12px' }}>
                      {item.eventId}
                    </Text>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    {/* 这一列记的是"事件有没有进队列"（HTTP 2xx），不是"客户有没有收到"：
                        后者要等派发端写终态，在历史页与铃铛里看。标成"成功"会与客户实际没收到同屏矛盾。 */}
                    <Tag color={item.status === 'SUCCESS' ? 'green' : 'red'}>
                      {item.status === 'SUCCESS'
                        ? intl.formatMessage({ id: 'event.accepted' })
                        : intl.formatMessage({ id: 'event.acceptFailed' })}
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
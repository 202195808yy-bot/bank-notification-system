import { useState } from 'react';
import { Form, Input, Button, Alert, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useIntl } from 'react-intl';
import axios from '../api/axiosInstance';
import { errorText } from '../i18n';
import LocaleSwitcher from '../components/LocaleSwitcher';

const { Title } = Typography;

export default function RegisterPage() {
  const intl = useIntl();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values) => {
    // 显式构造请求体，确保密码字段存在
    const payload = {
      name: values.name?.trim() || '',
      email: values.email?.trim() || '',
      phone: values.phone?.trim() || '',
      password: values.password || '',
      // 免打扰时段要按客户自己的钟点判定；注册时把浏览器报告的 IANA 时区一并存下来
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      // 正文语言同样采一次（PRD-56）：优先客户在这个页面上实际选的界面语言，退回浏览器语言。
      // 后端认不出来就不写，所以这里不需要做映射
      locale: localStorage.getItem('locale') || navigator.language,
    };

    if (!payload.password) {
      setError(intl.formatMessage({ id: 'register.passwordEmpty' }));
      return;
    }

    setLoading(true);
    try {
      await axios.post('/auth/register', payload);
      navigate('/login');
    } catch (e) {
      // 优先按后端 {code} 译成当前语言；没有 code 才退后端 message，再退通用文案
      const data = e.response?.data;
      setError(errorText(data?.code) || data?.message
        || intl.formatMessage({ id: 'register.registrationFailed' }));
    } finally {
      setLoading(false);
    }
  };

  return (
      <div style={{ maxWidth: 400, margin: '200px auto', padding: 24, position: 'relative' }}>
        <div style={{ position: 'absolute', top: -80, right: 0 }}>
          <LocaleSwitcher />
        </div>
        <Title level={3} style={{ textAlign: 'center' }}>
          {intl.formatMessage({ id: 'register.title' })}
        </Title>
        {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} showIcon />}
        <Form onFinish={handleSubmit} layout="vertical" autoComplete="off">
          <Form.Item
              name="name"
              rules={[{ required: true, message: intl.formatMessage({ id: 'register.name' }) }]}
          >
            <Input placeholder={intl.formatMessage({ id: 'register.name' })} />
          </Form.Item>
          <Form.Item
              name="email"
              rules={[
                { required: true, message: intl.formatMessage({ id: 'login.email' }) },
                { type: 'email', message: intl.formatMessage({ id: 'register.invalidEmail' }) },
              ]}
          >
            <Input placeholder={intl.formatMessage({ id: 'login.email' })} />
          </Form.Item>
          <Form.Item
              name="phone"
              rules={[{ required: true, message: intl.formatMessage({ id: 'register.phone' }) }]}
          >
            <Input placeholder={intl.formatMessage({ id: 'register.phone' })} />
          </Form.Item>
          <Form.Item
              name="password"
              rules={[{ required: true, message: intl.formatMessage({ id: 'login.password' }) }]}
          >
            <Input.Password
                placeholder={intl.formatMessage({ id: 'login.password' })}
                autoComplete="new-password"
            />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            {intl.formatMessage({ id: 'register.submit' })}
          </Button>
        </Form>
        <div style={{ marginTop: 16, textAlign: 'center' }}>
          {intl.formatMessage({ id: 'register.alreadyHaveAccount' })} <Link to="/login">{intl.formatMessage({ id: 'register.signIn' })}</Link>
        </div>
      </div>
  );
}
import React, { useState } from 'react';
import { Form, Input, Button, Alert, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useIntl } from 'react-intl';
import axios from '../api/axiosInstance';

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
    };

    if (!payload.password) {
      setError(intl.formatMessage({ id: 'register.passwordEmpty' }));
      return;
    }

    setLoading(true);
    try {
      await axios.post('/auth/register', payload);
      navigate('/login', { state: { registered: true } });
    } catch (e) {
      setError(e.response?.data?.message || intl.formatMessage({ id: 'register.registrationFailed' }));
    } finally {
      setLoading(false);
    }
  };

  return (
      <div style={{ maxWidth: 400, margin: '200px auto', padding: 24 }}>
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
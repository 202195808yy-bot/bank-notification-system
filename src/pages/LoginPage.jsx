import { useState, useEffect } from 'react';
import { Form, Input, Button, Alert, Card } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { BellOutlined, MailOutlined, LockOutlined, EyeOutlined, EyeTwoTone } from '@ant-design/icons';
import { useIntl } from 'react-intl';
import useAuthStore from '../store/useAuthStore';
import { errorText } from '../i18n';
import LocaleSwitcher from '../components/LocaleSwitcher';

export default function LoginPage() {
  const intl = useIntl();
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);
  const user = useAuthStore((state) => state.user);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (user) {
      navigate('/dashboard', { replace: true });
    }
  }, [user, navigate]);

  const handleSubmit = async ({ email, password }) => {
    setLoading(true);
    try {
      await login({ email, password });
      navigate('/dashboard');
    } catch (e) {
      // 优先按后端 {code} 译成当前语言；只有没有 code 时才退回后端 message，再退通用文案
      const data = e.response?.data;
      setError(errorText(data?.code) || data?.message
        || intl.formatMessage({ id: 'login.loginFailed' }));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ 
      minHeight: '100vh', 
      display: 'flex', 
      alignItems: 'center', 
      justifyContent: 'center',
      background: '#f5f5f5',
      padding: '20px',
      position: 'relative',
    }}>
      <div style={{ position: 'absolute', top: '20px', right: '24px' }}>
        <LocaleSwitcher />
      </div>
      <Card 
        style={{ 
          width: '100%', 
          maxWidth: '420px',
          background: '#ffffff',
          borderRadius: '12px',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.08)',
          border: '1px solid #f0f0f0',
        }}
        bodyStyle={{ padding: '32px' }}
      >
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <div style={{ 
            width: '72px', 
            height: '72px', 
            margin: '0 auto 16px',
            background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
            borderRadius: '20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 8px 24px rgba(22, 119, 255, 0.3)',
          }}>
            <BellOutlined style={{ color: '#fff', fontSize: '32px' }} />
          </div>
          <h1 style={{ 
            fontSize: '24px', 
            fontWeight: 700, 
            color: 'rgba(0, 0, 0, 0.88)',
            marginBottom: '8px'
          }}>
            {intl.formatMessage({ id: 'app.title' })}
          </h1>
          <p style={{ color: 'rgba(0, 0, 0, 0.65)', fontSize: '14px' }}>
            {intl.formatMessage({ id: 'login.title' })}
          </p>
        </div>

        {error && (
          <Alert 
            message={error} 
            type="error" 
            style={{ 
              marginBottom: '20px',
              background: 'rgba(239, 68, 68, 0.1)',
              borderColor: 'rgba(239, 68, 68, 0.3)',
              borderRadius: '8px'
            }} 
            showIcon 
          />
        )}

        <Form 
          onFinish={handleSubmit} 
          layout="vertical"
        >
          <Form.Item
            label={intl.formatMessage({ id: 'login.email' })}
            name="email"
            rules={[{
              required: true,
              message: intl.formatMessage({ id: 'login.email' }) + '...',
            }, {
              type: 'email',
              message: intl.formatMessage({ id: 'register.invalidEmail' }),
            }]}
            style={{ marginBottom: '20px' }}
          >
            <Input
              placeholder={intl.formatMessage({ id: 'login.email' })}
              autoComplete="username"
              prefix={<MailOutlined style={{ color: 'rgba(0, 0, 0, 0.45)' }} />}
              style={{ height: '48px' }}
            />
          </Form.Item>

          <Form.Item
            label={intl.formatMessage({ id: 'login.password' })}
            name="password"
            rules={[{
              required: true,
              message: intl.formatMessage({ id: 'login.password' }) + '...',
            }]}
            style={{ marginBottom: '24px' }}
          >
            <Input.Password
              placeholder={intl.formatMessage({ id: 'login.password' })}
              autoComplete="current-password"
              prefix={<LockOutlined style={{ color: 'rgba(0, 0, 0, 0.45)' }} />}
              iconRender={(visible) => (
                <Button
                  type="text"
                  icon={visible ? <EyeOutlined /> : <EyeTwoTone />}
                  onClick={() => {}}
                  style={{ padding: '0 12px', color: 'rgba(0, 0, 0, 0.45)' }}
                />
              )}
              style={{ height: '48px' }}
            />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            block
            loading={loading}
            size="large"
            style={{ height: '48px', fontSize: '15px', fontWeight: 600 }}
          >
            {loading ? intl.formatMessage({ id: 'common.loading' }) : intl.formatMessage({ id: 'login.submit' })}
          </Button>
        </Form>

        <div style={{ marginTop: '24px', textAlign: 'center' }}>
          <span style={{ color: 'rgba(0, 0, 0, 0.65)', fontSize: '14px' }}>
            {intl.formatMessage({ id: 'login.createAccountQuestion' })}
          </span>
          <Link
            to="/register"
            style={{
              marginLeft: '8px',
              color: '#4096ff',
              fontWeight: 500,
            }}
          >
            {intl.formatMessage({ id: 'login.createAccount' })}
          </Link>
        </div>
      </Card>
    </div>
  );
}
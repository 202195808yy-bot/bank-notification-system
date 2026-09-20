-- =============================================
-- 清空所有业务表并重置序列
-- =============================================
TRUNCATE sent_logs, notifications, notification_preferences, notification_templates, customers RESTART IDENTITY CASCADE;

-- =============================================
-- 1. 插入 10 位客户（密码统一为 : $2a$10$.5G.GxQ3/x2upJ0oE.wopO80eOSN.FQgwLza3fcAO.oJ7o4sAJHKe）
-- =============================================
INSERT INTO customers (name, email, phone, password, role) VALUES
                                                               ('Alice Johnson',    'alice@bank.com',   '+79160000001', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Bob Smith',        'bob@bank.com',     '+79160000002', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Charlie Brown',    'charlie@bank.com', '+79160000003', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Diana Prince',     'diana@bank.com',   '+79160000004', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Ethan Hunt',       'ethan@bank.com',   '+79160000005', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Fiona Gallagher',  'fiona@bank.com',   '+79160000006', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('George Lucas',     'george@bank.com',  '+79160000007', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Hannah Montana',   'hannah@bank.com',  '+79160000008', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Ivan Petrov',      'ivan@bank.com',    '+79160000009', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'USER'),
                                                               ('Administrator',    'admin@bank.com',   '+79160000000', '$2a$10$NKHQ5j15nHIqzHm8UPhTRuxo/4FDJ/Z/6/cCM8xPh5abX3O9smnk.', 'ADMIN');

-- =============================================
-- 2. 插入通知偏好
-- =============================================
INSERT INTO notification_preferences (customer_id, event_type, channels, quiet_start, quiet_end, enabled) VALUES
                                                                                                              (1, 'TRANSACTION', '["SMS","EMAIL"]', '22:00', '07:00', true),
                                                                                                              (1, 'RISK_ALERT',  '["EMAIL"]',       NULL,    NULL,    true),
                                                                                                              (2, 'TRANSACTION', '["PUSH"]',         NULL,    NULL,    true),
                                                                                                              (3, 'BILL',        '["SMS","PUSH"]',   NULL,    NULL,    true),
                                                                                                              (4, 'TRANSACTION', '["SMS"]',          NULL,    NULL,    true),
                                                                                                              (5, 'RISK_ALERT',  '["EMAIL","PUSH"]','23:00', '06:00', true);

-- =============================================
-- 3. 插入通知模板
-- =============================================
INSERT INTO notification_templates (event_type, channel, locale, title_template, body_template) VALUES
                                                                                                    ('TRANSACTION', 'SMS',   'zh_CN', 'Transaction Alert', 'A transaction of {{amount}} RUB has been made on your account {{account}}.'),
                                                                                                    ('TRANSACTION', 'EMAIL', 'zh_CN', 'Transaction Notification', 'Dear customer, a transaction of {{amount}} RUB occurred on your account {{account}} at {{time}}. Balance: {{balance}} RUB.'),
                                                                                                    ('RISK_ALERT',  'EMAIL', 'zh_CN', 'Risk Alert', 'Suspicious login detected for account {{account}}. Please verify immediately.'),
                                                                                                    ('BILL',        'SMS',   'zh_CN', 'Bill Reminder', 'Your {{bill_type}} bill of {{amount}} RUB is due soon. Please make the payment.');

-- =============================================
-- 4. 批量生成 2000 条通知记录
-- =============================================
DO $$
    DECLARE
    i INT := 1;
cust_id BIGINT;
event_type TEXT;
channel TEXT;
template_id INT;
content TEXT;
stat TEXT;
created TIMESTAMP;
BEGIN
    WHILE i <= 2000 LOOP
        -- 随机客户 1~10
        cust_id := floor(random()*10)+1;
-- 随机事件类型
event_type := CASE floor(random()*3)::int
            WHEN 0 THEN 'TRANSACTION'
            WHEN 1 THEN 'RISK_ALERT'
            ELSE 'BILL'
        END;

        -- 根据事件类型确定渠道和模板
IF event_type = 'TRANSACTION' THEN
            IF random() < 0.5 THEN
                channel := 'SMS'; template_id := 1;
ELSE
                channel := 'EMAIL'; template_id := 2;
END IF;
ELSIF event_type = 'RISK_ALERT' THEN
            channel := 'EMAIL'; template_id := 3;
ELSE -- BILL
            channel := 'SMS'; template_id := 4;
END IF;

        -- 随机状态
stat := CASE floor(random()*3)::int
            WHEN 0 THEN 'PENDING'
            WHEN 1 THEN 'SENT'
            ELSE 'FAILED'
        END;

        -- 构造内容
content := CASE event_type
            WHEN 'TRANSACTION' THEN 'A transaction of ' || round((random()*9999+1)::numeric,2) || ' RUB has been made on your account ****' || i || '.'
            WHEN 'RISK_ALERT' THEN 'Suspicious login detected for account ****' || i || '. Please verify immediately.'
            ELSE 'Your bill of ' || round((random()*5000+500)::numeric,2) || ' RUB is due soon. Please make the payment.'
        END;

        -- 随机时间（过去60天内）
created := NOW() - (random()*60)::int * INTERVAL '1 day' - (random()*24)::int * INTERVAL '1 hour';

INSERT INTO notifications (customer_id, event_id, template_id, channel, content, status, retry_count, created_at)
VALUES (cust_id, 'evt-' || i, template_id, channel, content, stat, 0, created);

i := i + 1;
END LOOP;
END $$;
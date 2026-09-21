-- 通知模板基础数据（PRD-46 / 文档 N40）
--
-- 为什么需要这个脚本：库里原先只有 3 行模板（TRANSACTION 的 PUSH/SMS + 一行占位垃圾），
-- 于是任何别的事件类型/渠道/语言组合都必然记成 SKIPPED / TEMPLATE_MISSING，
-- 界面上看到的就是「除了交易通知，其他都是已跳过、未生成正文」——内容没铺，不是派发逻辑坏了。
--
-- 范围：6 个事件类型 × 3 个渠道 × 3 个语言 = 54 行。事件类型与 web/src/utils/constants.js
-- 的 EVENT_TYPES、后端 common/enums/EventType 逐项一致；语言与 AppConstants.SUPPORTED_LOCALES 一致。
--
-- ⚠️ 文案属演示内容（金额、活动、落款都是编的），交付前需产品/法务复核，不能当真实话术用。
-- ⚠️ 模板里的每个 {{变量}} 都必须在事件载荷里存在，否则派发端按 PRD-35 记
--    FAILED_VALIDATION / VARIABLE_MISSING 而不发送。上面的取值范围与
--    EventSenderPage 的 eventTemplates 预置载荷一一对应，PROMOTION 因此不含任何变量。
--
-- 幂等：只插缺失的三元组，已存在的行一律不动（客户手工改过的文案不会被覆盖）。
-- 用法：
--   docker compose exec -T postgres psql -U postgres -d bank_notifications \
--     -v ON_ERROR_STOP=1 -f - < bank-notification-backend/scripts/seed-templates.sql

BEGIN;

-- 历史遗留的占位垃圾行：正文就是字面量「内容模板」，投出去是给客户提供器文本。
-- 删掉后由下面的 INSERT 补回同三元组的正常文案（notifications 里没有行引用它，删除安全）。
DELETE FROM notification_templates
 WHERE body_template = '内容模板' AND title_template = '安全模板';

WITH base(event_type, locale, title, body, email_suffix) AS (VALUES
    ('TRANSACTION', 'zh_CN', '交易提醒',
     '账户 {{account}} 发生交易 {{amount}} RUB，余额 {{balance}} RUB。',
     E'\n\n—— 银行客服中心'),
    ('TRANSACTION', 'ru_RU', 'Операция',
     'Операция {{amount}} RUB по счёту {{account}}. Доступный остаток: {{balance}} RUB.',
     E'\n\nС уважением, банковский сервис.'),
    ('TRANSACTION', 'en_US', 'Transaction',
     'Card {{account}} charged {{amount}} RUB. Available balance: {{balance}} RUB.',
     E'\n\nBest regards, your bank.'),

    ('RISK_ALERT', 'zh_CN', '风险提醒',
     '账户 {{account}} 检测到风险事件：{{riskType}}（地点 {{location}}，设备 {{device}}）。如非本人操作请立即联系银行。',
     E'\n\n—— 银行风控中心'),
    ('RISK_ALERT', 'ru_RU', 'Предупреждение о риске',
     'По счёту {{account}} зафиксировано подозрительное событие: {{riskType}} ({{location}}, {{device}}). Если это были не вы — сразу свяжитесь с банком.',
     E'\n\nСлужба финансового мониторинга.'),
    ('RISK_ALERT', 'en_US', 'Risk alert',
     'Suspicious activity on account {{account}}: {{riskType}} ({{location}}, {{device}}). If this was not you, contact the bank immediately.',
     E'\n\nBank fraud prevention team.'),

    ('BILL', 'zh_CN', '账单提醒',
     '{{billType}}：应还 {{amount}} RUB，最后还款日 {{dueDate}}，账户 {{account}}。',
     E'\n\n—— 银行账单服务'),
    ('BILL', 'ru_RU', 'Напоминание о счёте',
     '{{billType}}: к оплате {{amount}} RUB, срок до {{dueDate}}, счёт {{account}}.',
     E'\n\nСервис банковских счетов.'),
    ('BILL', 'en_US', 'Bill reminder',
     '{{billType}}: {{amount}} RUB due by {{dueDate}} on account {{account}}.',
     E'\n\nBank billing service.'),

    ('LOGIN', 'zh_CN', '登录提醒',
     '您的账户在新设备登录：{{device}}，IP {{ipAddress}}，地点 {{location}}。',
     E'\n\n—— 银行安全服务'),
    ('LOGIN', 'ru_RU', 'Вход в аккаунт',
     'Новый вход в ваш кабинет: {{device}}, IP {{ipAddress}}, {{location}}.',
     E'\n\nСервис безопасности банка.'),
    ('LOGIN', 'en_US', 'Sign-in alert',
     'New sign-in to your account: {{device}}, IP {{ipAddress}}, {{location}}.',
     E'\n\nBank security service.'),

    ('SECURITY', 'zh_CN', '安全提醒',
     '安全事件：{{alertType}}。如非本人操作，请立即修改密码。',
     E'\n\n—— 银行安全服务'),
    ('SECURITY', 'ru_RU', 'Безопасность',
     'Событие безопасности: {{alertType}}. Если это были не вы — немедленно смените пароль.',
     E'\n\nСервис безопасности банка.'),
    ('SECURITY', 'en_US', 'Security alert',
     'Security event: {{alertType}}. If this was not you, change your password right away.',
     E'\n\nBank security service.'),

    ('PROMOTION', 'zh_CN', '优惠活动',
     '本月信用卡消费返现 5%，最高 3000 RUB。详情见手机银行。',
     E'\n\n—— 银行营销服务'),
    ('PROMOTION', 'ru_RU', 'Акция',
     'Кэшбэк 5% на покупки по карте в этом месяце, до 3000 RUB. Подробности в мобильном банке.',
     E'\n\nМаркетинговая служба банка.'),
    ('PROMOTION', 'en_US', 'Promotion',
     '5% cashback on card purchases this month, up to 3000 RUB. Details in the mobile banking app.',
     E'\n\nBank promotions team.')
),
channel_variant(channel, is_email) AS (VALUES
    ('SMS', false),
    ('EMAIL', true),
    ('PUSH', false)
)
INSERT INTO notification_templates(event_type, channel, locale, title_template, body_template, created_at, updated_at)
SELECT b.event_type,
       c.channel,
       b.locale,
       b.title,
       -- 邮件比短信/推送多一行落款；其余渠道用同一段正文（差异到此为止，不虚构渠道专属话术）
       b.body || CASE WHEN c.is_email THEN b.email_suffix ELSE '' END,
       NOW(),
       NOW()
FROM base b
         CROSS JOIN channel_variant c
WHERE NOT EXISTS (SELECT 1
                  FROM notification_templates t
                  WHERE t.event_type = b.event_type
                    AND t.channel = c.channel
                    AND t.locale = b.locale);

COMMIT;

-- 复核：应为 6 × 3 × 3 = 54，且没有一行正文里还留着占位器字面量
SELECT count(*)                                  AS templates_total,
       count(*) FILTER (WHERE body_template LIKE '%内容模板%') AS junk_left
FROM notification_templates;

-- 清理所有业务数据，保留表结构

DELETE FROM sent_logs;
DELETE FROM notifications;
DELETE FROM notification_preferences;
DELETE FROM notification_templates;
DELETE FROM customers;

-- 如果需要，可重置自增序列（PostgreSQL）
ALTER SEQUENCE IF EXISTS customers_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS notification_preferences_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS notification_templates_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS notifications_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS sent_logs_id_seq RESTART WITH 1;
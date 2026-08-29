UPDATE notification_settings
SET sms_enabled = FALSE
WHERE sms_enabled = TRUE;

ALTER TABLE notification_settings DROP COLUMN smtp_password;
ALTER TABLE notification_settings DROP COLUMN sms_api_key;

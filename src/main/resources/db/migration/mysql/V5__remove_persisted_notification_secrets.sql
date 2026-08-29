UPDATE notification_settings
SET sms_enabled = 0
WHERE sms_enabled <> 0;

ALTER TABLE notification_settings
    DROP COLUMN smtp_password,
    DROP COLUMN sms_api_key;

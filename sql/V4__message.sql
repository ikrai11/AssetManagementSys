CREATE TABLE site_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    msg_type VARCHAR(32) NOT NULL,
    borrow_id BIGINT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_message_receiver (receiver_id, read_flag, created_at)
);

CREATE TABLE mail_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    email VARCHAR(128) NULL,
    subject VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    borrow_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    fail_reason VARCHAR(500) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mail_record_receiver (receiver_id, created_at),
    KEY idx_mail_record_status (status, created_at)
);

CREATE TABLE reminder_mark (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_date DATE NOT NULL,
    remind_type VARCHAR(32) NOT NULL,
    borrow_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_mark (biz_date, remind_type, borrow_id, receiver_id)
);

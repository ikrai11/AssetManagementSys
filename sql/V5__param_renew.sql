CREATE TABLE sys_param (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    param_key VARCHAR(64) NOT NULL,
    param_value VARCHAR(200) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_param_key (param_key)
);

INSERT INTO sys_param (param_key, param_value) VALUES
    ('remind.lead.days', '7,3,1'),
    ('borrow.max.days', '90'),
    ('renew.max.days.from.issue', '180'),
    ('mail.channel.enabled', 'true'),
    ('login.max.failures', '5'),
    ('login.lock.minutes', '15');

CREATE TABLE borrow_renew (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    borrow_id BIGINT NOT NULL,
    old_return_date DATE NOT NULL,
    new_return_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    approver_id BIGINT NULL,
    approved_at DATETIME NULL,
    approve_comment VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_renew_borrow_status (borrow_id, status)
);

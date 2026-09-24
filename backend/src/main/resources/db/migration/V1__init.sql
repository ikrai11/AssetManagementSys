CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    email VARCHAR(128) NULL,
    mobile VARCHAR(20) NULL,
    dept_id BIGINT NULL,
    role VARCHAR(16) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    must_change_password TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username)
);

CREATE TABLE sys_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    parent_id BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE sys_location (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    parent_id BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE asset_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_asset_category_name (name)
);

CREATE TABLE asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_no VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_id BIGINT NOT NULL,
    brand VARCHAR(64) NULL,
    model VARCHAR(64) NULL,
    serial_no VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    purchase_date DATE NULL,
    purchase_price DECIMAL(12, 2) NULL,
    supplier VARCHAR(128) NULL,
    warranty_until DATE NULL,
    dept_id BIGINT NULL,
    location_id BIGINT NULL,
    holder_user_id BIGINT NULL,
    borrow_start_date DATE NULL,
    expected_return_date DATE NULL,
    current_borrow_id BIGINT NULL,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_no (asset_no),
    UNIQUE KEY uk_asset_serial_no (serial_no),
    KEY idx_asset_status_category (status, category_id),
    KEY idx_asset_holder (holder_user_id),
    KEY idx_asset_dept (dept_id),
    KEY idx_asset_location (location_id),
    KEY idx_asset_due (expected_return_date),
    KEY idx_asset_borrow_start (borrow_start_date)
);

CREATE TABLE borrow_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    asset_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    purpose VARCHAR(500) NULL,
    expected_return_date DATE NULL,
    remark VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    approver_id BIGINT NULL,
    approved_at DATETIME NULL,
    approve_comment VARCHAR(500) NULL,
    issued_at DATETIME NULL,
    return_requested_at DATETIME NULL,
    returned_at DATETIME NULL,
    return_comment VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_borrow_order_no (order_no),
    KEY idx_borrow_asset_status (asset_id, status),
    KEY idx_borrow_applicant_status (applicant_id, status),
    KEY idx_borrow_status_due (status, expected_return_date)
);

CREATE TABLE borrow_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    borrow_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_borrow_log_borrow (borrow_id)
);

INSERT INTO asset_category (name, enabled) VALUES
    ('笔记本', 1),
    ('台式机', 1),
    ('显示器', 1),
    ('鼠标', 1),
    ('键盘', 1),
    ('转接头', 1),
    ('投影仪', 1);

CREATE TABLE asset_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_asset_log_asset (asset_id)
);

CREATE TABLE asset_repair (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_id BIGINT NOT NULL,
    fault VARCHAR(500) NOT NULL,
    sent_date DATE NOT NULL,
    finished_date DATE NULL,
    result_text VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_asset_repair_asset (asset_id, status)
);

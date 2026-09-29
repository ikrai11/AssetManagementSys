CREATE TABLE stocktake (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    dept_id BIGINT NULL,
    location_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    operator_id BIGINT NOT NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_stocktake_status (status, created_at)
);

CREATE TABLE stocktake_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stocktake_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_no VARCHAR(64) NOT NULL,
    asset_name VARCHAR(128) NOT NULL,
    asset_status VARCHAR(16) NOT NULL,
    dept_id BIGINT NULL,
    location_id BIGINT NULL,
    result VARCHAR(16) NOT NULL,
    comment VARCHAR(500) NULL,
    UNIQUE KEY uk_stocktake_asset (stocktake_id, asset_id),
    KEY idx_stocktake_item_task (stocktake_id, result)
);

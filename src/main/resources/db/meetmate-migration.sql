SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_meet_room` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `creator_id` BIGINT UNSIGNED NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `invite_code` CHAR(6) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `center_x` DECIMAL(10, 6) NOT NULL,
    `center_y` DECIMAL(10, 6) NOT NULL,
    `search_radius_meter` INT NOT NULL DEFAULT 5000,
    `max_members` INT NOT NULL DEFAULT 6,
    `min_submitted_members` INT NOT NULL DEFAULT 2,
    `version` INT NOT NULL DEFAULT 0,
    `locked_at` DATETIME NULL,
    `confirmed_proposal_id` BIGINT UNSIGNED NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_code` (`invite_code`),
    KEY `idx_creator_status` (`creator_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_meet_room'
       AND COLUMN_NAME = 'locked_at') = 0,
    'ALTER TABLE tb_meet_room ADD COLUMN locked_at DATETIME NULL AFTER version',
    'SELECT 1');
PREPARE meetmate_stmt FROM @ddl;
EXECUTE meetmate_stmt;
DEALLOCATE PREPARE meetmate_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_meet_room'
       AND COLUMN_NAME = 'confirmed_proposal_id') = 0,
    'ALTER TABLE tb_meet_room ADD COLUMN confirmed_proposal_id BIGINT UNSIGNED NULL AFTER locked_at',
    'SELECT 1');
PREPARE meetmate_stmt FROM @ddl;
EXECUTE meetmate_stmt;
DEALLOCATE PREPARE meetmate_stmt;

CREATE TABLE IF NOT EXISTS `tb_meet_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `room_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `role` VARCHAR(16) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `preference_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_user` (`room_id`, `user_id`),
    KEY `idx_room_status` (`room_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_meet_message` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `room_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `content` VARCHAR(500) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_meet_message_room_id` (`room_id`, `id`),
    KEY `idx_meet_message_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_meet_preference` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `room_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `raw_text` TEXT NOT NULL,
    `draft_json` JSON NULL,
    `confirmed_json` JSON NULL,
    `status` VARCHAR(16) NOT NULL,
    `parser_version` VARCHAR(32) NOT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `confirmed_at` DATETIME NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_preference_room_user` (`room_id`, `user_id`),
    KEY `idx_preference_room_status` (`room_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_meet_plan_run` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `room_id` BIGINT UNSIGNED NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `current_attempt` INT NOT NULL DEFAULT 1,
    `clarification_count` INT NOT NULL DEFAULT 0,
    `selected_proposal_id` BIGINT UNSIGNED NULL,
    `error_code` VARCHAR(64) NULL,
    `error_message` VARCHAR(500) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `finished_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_plan_run_room_status` (`room_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_meet_plan_attempt` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id` BIGINT UNSIGNED NOT NULL,
    `attempt_no` INT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `dispatch_status` VARCHAR(32) NOT NULL,
    `dispatch_attempts` INT NOT NULL DEFAULT 0,
    `model` VARCHAR(128) NULL,
    `prompt_version` VARCHAR(32) NOT NULL,
    `error_code` VARCHAR(64) NULL,
    `error_message` VARCHAR(500) NULL,
    `next_dispatch_at` DATETIME NULL,
    `started_at` DATETIME NULL,
    `finished_at` DATETIME NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_attempt_run_no` (`run_id`, `attempt_no`),
    KEY `idx_plan_attempt_dispatch` (`dispatch_status`, `next_dispatch_at`),
    KEY `idx_plan_attempt_status_update` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'tb_meet_plan_attempt'
       AND INDEX_NAME = 'idx_plan_attempt_status_update') = 0,
    'ALTER TABLE tb_meet_plan_attempt ADD KEY idx_plan_attempt_status_update (status, update_time)',
    'SELECT 1');
PREPARE meetmate_stmt FROM @ddl;
EXECUTE meetmate_stmt;
DEALLOCATE PREPARE meetmate_stmt;

CREATE TABLE IF NOT EXISTS `tb_meet_plan_event` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id` BIGINT UNSIGNED NOT NULL,
    `attempt_id` BIGINT UNSIGNED NULL,
    `sequence` BIGINT UNSIGNED NOT NULL,
    `event_type` VARCHAR(64) NOT NULL,
    `summary` VARCHAR(500) NOT NULL,
    `payload_json` JSON NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_event_run_sequence` (`run_id`, `sequence`),
    KEY `idx_plan_event_attempt` (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_meet_clarification` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id` BIGINT UNSIGNED NOT NULL,
    `target_user_id` BIGINT UNSIGNED NOT NULL,
    `constraint_key` VARCHAR(64) NOT NULL,
    `question` VARCHAR(500) NOT NULL,
    `options_json` JSON NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `answer` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `answered_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_clarification_run_status` (`run_id`, `status`),
    KEY `idx_clarification_target` (`target_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_meet_proposal` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id` BIGINT UNSIGNED NOT NULL,
    `attempt_id` BIGINT UNSIGNED NOT NULL,
    `proposal_rank` INT NOT NULL,
    `recommended` TINYINT(1) NOT NULL DEFAULT 0,
    `shop_id` BIGINT UNSIGNED NOT NULL,
    `suggested_time` VARCHAR(100) NOT NULL,
    `meeting_point` VARCHAR(255) NOT NULL,
    `estimated_per_capita` BIGINT NULL,
    `reasoning` VARCHAR(1000) NULL,
    `satisfied_json` JSON NOT NULL,
    `tradeoffs_json` JSON NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proposal_run_rank` (`run_id`, `proposal_rank`),
    KEY `idx_proposal_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tb_shop_meet_meta` (
    `shop_id` BIGINT UNSIGNED NOT NULL,
    `cuisine` VARCHAR(64) NOT NULL,
    `tags_json` JSON NOT NULL,
    `spicy_level` INT NULL,
    `allergen_tags_json` JSON NOT NULL,
    `source` VARCHAR(32) NOT NULL,
    `confidence` INT NOT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tb_shop_meet_meta`
(`shop_id`, `cuisine`, `tags_json`, `spicy_level`, `allergen_tags_json`, `source`, `confidence`)
VALUES
    (1, '粤式茶餐厅', JSON_ARRAY('粤菜', '茶餐厅', '聚餐'), 1, JSON_ARRAY('花生', '坚果'), 'SEED', 80),
    (2, '烤肉火锅', JSON_ARRAY('烤肉', '火锅', '羊肉'), 2, JSON_ARRAY('芝麻'), 'SEED', 80),
    (3, '杭帮菜', JSON_ARRAY('中餐', '杭帮菜', '家庭聚餐'), 1, JSON_ARRAY('花生'), 'SEED', 85),
    (4, '西餐', JSON_ARRAY('西餐', '约会', '环境'), 0, JSON_ARRAY('乳制品', '麸质'), 'SEED', 85),
    (5, '火锅', JSON_ARRAY('火锅', '聚餐', '夜宵'), 3, JSON_ARRAY('芝麻', '花生'), 'SEED', 90),
    (6, '涮锅', JSON_ARRAY('火锅', '涮羊肉'), 2, JSON_ARRAY('芝麻'), 'SEED', 80),
    (7, '烤鱼', JSON_ARRAY('烤鱼', '川菜'), 3, JSON_ARRAY('鱼类', '花生'), 'SEED', 85),
    (8, '日料寿司', JSON_ARRAY('日料', '寿司'), 0, JSON_ARRAY('鱼类', '贝类'), 'SEED', 90),
    (9, '炭火锅', JSON_ARRAY('火锅', '羊蝎子'), 2, JSON_ARRAY('芝麻'), 'SEED', 80)
ON DUPLICATE KEY UPDATE
    `cuisine` = VALUES(`cuisine`),
    `tags_json` = VALUES(`tags_json`),
    `spicy_level` = VALUES(`spicy_level`),
    `allergen_tags_json` = VALUES(`allergen_tags_json`),
    `source` = VALUES(`source`),
    `confidence` = VALUES(`confidence`);

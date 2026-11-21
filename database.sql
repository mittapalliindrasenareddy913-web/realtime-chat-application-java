-- ═══════════════════════════════════════════════════════════════
--  ChatApp - MySQL Database Schema
--  Run this script once to set up the database
--  mysql -u root -p < database.sql
-- ═══════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS chatapp
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chatapp;

-- ───────────────────────────────────────────────────────────────
--  Table: users
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id       INT          AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(64)  NOT NULL,         -- SHA-256 hex (64 chars)
    email         VARCHAR(100) NOT NULL UNIQUE,
    is_online     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen     DATETIME     NULL
);

-- ───────────────────────────────────────────────────────────────
--  Table: private_messages
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS private_messages (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT          NOT NULL,
    receiver_id INT          NOT NULL,
    message     TEXT         NOT NULL,
    sent_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT fk_pm_sender   FOREIGN KEY (sender_id)   REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_receiver FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_pm_pair (sender_id, receiver_id)
);

-- ───────────────────────────────────────────────────────────────
--  Table: groups
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `groups` (
    group_id    INT          AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(100) NOT NULL,
    creator_id  INT          NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_grp_creator FOREIGN KEY (creator_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ───────────────────────────────────────────────────────────────
--  Table: group_members
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS group_members (
    group_id    INT          NOT NULL,
    user_id     INT          NOT NULL,
    joined_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES `groups`(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES users(user_id)     ON DELETE CASCADE
);

-- ───────────────────────────────────────────────────────────────
--  Table: group_messages
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS group_messages (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    group_id    INT          NOT NULL,
    sender_id   INT          NOT NULL,
    message     TEXT         NOT NULL,
    sent_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gmsg_group  FOREIGN KEY (group_id)  REFERENCES `groups`(group_id)   ON DELETE CASCADE,
    CONSTRAINT fk_gmsg_sender FOREIGN KEY (sender_id) REFERENCES users(user_id)        ON DELETE CASCADE,
    INDEX idx_gm_group (group_id)
);

-- ───────────────────────────────────────────────────────────────
--  Seed Data (for quick testing)
-- ───────────────────────────────────────────────────────────────
-- Password for 'alice' and 'bob' is 'password123'
-- SHA-256('password123') = ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f
INSERT IGNORE INTO users (username, password_hash, email) VALUES
  ('alice', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'alice@test.com'),
  ('bob',   'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'bob@test.com');

-- General chat group
INSERT IGNORE INTO `groups` (group_name, creator_id) VALUES ('General', 1);
INSERT IGNORE INTO group_members (group_id, user_id) VALUES (1, 1), (1, 2);

-- ═══════════════════════════════════════════════════════════════
--  DONE! Database is ready.
-- ═══════════════════════════════════════════════════════════════

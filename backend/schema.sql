-- ============================================================
--  Kaam Chor v2 — MariaDB schema
--  (Alternative to `python init_db.py` which uses db.create_all())
-- ============================================================

CREATE DATABASE IF NOT EXISTS kaamchor
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE kaamchor;

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    bio           TEXT,
    avatar_url    VARCHAR(500),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_seen     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    jti        VARCHAR(64) NOT NULL UNIQUE,
    user_id    INT NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rt_jti (jti),
    INDEX idx_rt_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS posts (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL,
    caption    TEXT,
    photo_url  VARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_posts_user (user_id),
    INDEX idx_posts_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS likes (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    post_id    INT NOT NULL,
    user_id    INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_post_user_like (post_id, user_id),
    INDEX idx_likes_post (post_id),
    INDEX idx_likes_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comments (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    post_id    INT NOT NULL,
    user_id    INT NOT NULL,
    text       TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_comments_post (post_id),
    INDEX idx_comments_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS chat_groups (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100),
    is_group      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    INT NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_activity DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_groups_activity (last_activity)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS group_members (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    group_id  INT NOT NULL,
    user_id   INT NOT NULL,
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_group_user_member (group_id, user_id),
    INDEX idx_gm_group (group_id),
    INDEX idx_gm_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS messages (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    group_id   INT NOT NULL,
    sender_id  INT NOT NULL,
    text       TEXT,
    media_url  VARCHAR(500),
    media_type VARCHAR(20) DEFAULT 'text',
    duration   FLOAT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_messages_group (group_id),
    INDEX idx_messages_sender (sender_id),
    INDEX idx_messages_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS message_status (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    message_id INT NOT NULL,
    user_id    INT NOT NULL,
    status     VARCHAR(20) DEFAULT 'sent',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_message_user_status (message_id, user_id),
    INDEX idx_ms_message (message_id),
    INDEX idx_ms_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS call_records (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    group_id    INT,
    caller_id   INT NOT NULL,
    call_type   VARCHAR(10) DEFAULT 'audio',
    is_group    BOOLEAN NOT NULL DEFAULT FALSE,
    status      VARCHAR(20) DEFAULT 'initiated',
    started_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    answered_at DATETIME,
    ended_at    DATETIME,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE SET NULL,
    FOREIGN KEY (caller_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_calls_group (group_id),
    INDEX idx_calls_caller (caller_id)
) ENGINE=InnoDB;

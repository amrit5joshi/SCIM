CREATE TABLE user_emails (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       VARCHAR(36)  NOT NULL,
    email_value   VARCHAR(255) NOT NULL,
    type          VARCHAR(50),
    primary_email TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_emails PRIMARY KEY (id),
    CONSTRAINT fk_user_emails_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

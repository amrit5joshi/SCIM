CREATE TABLE users (
    id            VARCHAR(36)  NOT NULL,
    user_name     VARCHAR(255) NOT NULL,
    external_id   VARCHAR(255),
    name_given    VARCHAR(255),
    name_family   VARCHAR(255),
    name_formatted VARCHAR(255),
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created       DATETIME(6)  NOT NULL,
    last_modified DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_user_name UNIQUE (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

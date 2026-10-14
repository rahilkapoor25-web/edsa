-- EDSA schema. Applied by Spring Boot on start-up (spring.sql.init.mode=always).

CREATE TABLE IF NOT EXISTS app_user (
    id            IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Imported reference data. An import replaces these wholesale inside one transaction.
CREATE TABLE IF NOT EXISTS student (
    id         VARCHAR(40) PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(254),
    department VARCHAR(60)  NOT NULL,
    year_of_study INT,
    section    VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS faculty (
    id         VARCHAR(40) PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(254),
    department VARCHAR(60)  NOT NULL,
    available  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS room (
    id        VARCHAR(40) PRIMARY KEY,
    name      VARCHAR(120) NOT NULL,
    building  VARCHAR(80)  NOT NULL,
    capacity  INT          NOT NULL,
    available BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS subject (
    code       VARCHAR(40) PRIMARY KEY,
    name       VARCHAR(160) NOT NULL,
    department VARCHAR(60)  NOT NULL,
    semester   INT
);

CREATE TABLE IF NOT EXISTS enrollment (
    student_id   VARCHAR(40) NOT NULL,
    subject_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (student_id, subject_code)
);

CREATE TABLE IF NOT EXISTS exam (
    id               VARCHAR(40) PRIMARY KEY,
    subject_code     VARCHAR(40) NOT NULL,
    exam_date        DATE        NOT NULL,
    permitted_start  TIME,
    duration_minutes INT
);

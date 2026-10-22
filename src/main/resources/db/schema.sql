-- EDSA schema. Applied by Spring Boot on start-up (spring.sql.init.mode=always).

CREATE TABLE IF NOT EXISTS app_user (
    id            IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- The imported faculty record this account belongs to, when it was provisioned from one.
    -- An id is stable where a name is not, so the mapping is by id and never by name.
    faculty_id    VARCHAR(40),
    -- A provisioned account holds no usable password until its owner sets one. Only the hash
    -- of the single-use activation secret is kept, so the secret cannot be read back out.
    activation_hash       VARCHAR(100),
    activation_expires_at TIMESTAMP
);

-- Added after the first release: applied to a database that already holds accounts.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS faculty_id VARCHAR(40);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS activation_hash VARCHAR(100);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS activation_expires_at TIMESTAMP;

-- One account per faculty record. Several accounts may have no faculty record at all.
ALTER TABLE app_user ADD CONSTRAINT IF NOT EXISTS uq_app_user_faculty_id UNIQUE (faculty_id);

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

-- Recorded absences, awaiting review or already decided. Kept here rather than in memory so a
-- request outlives a restart: a pending one is still pending tomorrow.
CREATE TABLE IF NOT EXISTS leave_request (
    id           IDENTITY PRIMARY KEY,
    faculty_id   VARCHAR(40)  NOT NULL,
    faculty_name VARCHAR(120) NOT NULL,
    absent_from  TIMESTAMP    NOT NULL,
    absent_to    TIMESTAMP    NOT NULL,
    reason       VARCHAR(300),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_by   TIMESTAMP    NOT NULL,
    decided_at   TIMESTAMP,
    -- Who took the decision, for the audit trail. Nothing in edsa.core reads this column.
    decided_by   VARCHAR(254),
    outcome      VARCHAR(600)
);

CREATE INDEX IF NOT EXISTS idx_leave_request_status ON leave_request (status);

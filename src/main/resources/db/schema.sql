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

-- ---------------------------------------------------------------------------
-- Duty decisions: which recommended duties a person has stood behind.
--
-- The allocator's output is an opinion and is recomputed whenever the data or the mode
-- changes. A coordinator's decision is not, so it is kept here and re-applied after every
-- compute; otherwise approving a roster and then reloading the workbook would silently throw
-- the approvals away. One decision per room-sitting, which is what the unique key says.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS duty_decision (
    id         IDENTITY PRIMARY KEY,
    slot_id    VARCHAR(40)  NOT NULL,
    room_id    VARCHAR(40)  NOT NULL,
    faculty_id VARCHAR(40)  NOT NULL,
    -- APPROVED (the recommendation, confirmed) or MANUAL (somebody else, chosen by hand).
    origin     VARCHAR(20)  NOT NULL,
    decided_by VARCHAR(254) NOT NULL,
    decided_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note       VARCHAR(300),
    CONSTRAINT uq_duty_decision_sitting UNIQUE (slot_id, room_id)
);

-- ---------------------------------------------------------------------------
-- Examination timing, as the office has overridden it.
--
-- The workbook declares when a paper may run; this table records a change made afterwards,
-- for one paper. The scheduler still places everything else around it, so an override is an
-- instruction to the scheduler rather than a finished sitting.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_time_override (
    exam_id          VARCHAR(40) PRIMARY KEY,
    exam_date        DATE,
    start_time       TIME,
    duration_minutes INT,
    changed_by       VARCHAR(254) NOT NULL,
    changed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- Every timing change ever made, and who made it. Append-only: a row is never updated or
-- deleted, because an audit history that can be edited answers no question worth asking.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_time_change (
    id          IDENTITY PRIMARY KEY,
    exam_id     VARCHAR(40)  NOT NULL,
    paper_code  VARCHAR(40)  NOT NULL,
    -- What it was and what it became, each as a readable summary rather than parsed columns:
    -- this is a record for people to read, and the live value lives in exam_time_override.
    was         VARCHAR(120) NOT NULL,
    became      VARCHAR(120) NOT NULL,
    scope       VARCHAR(20)  NOT NULL,
    changed_by  VARCHAR(254) NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- The warnings that were on screen when it was saved, so an override made in the knowledge
    -- of a clash is distinguishable afterwards from one made blind.
    warnings    VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_exam_time_change_exam ON exam_time_change (exam_id);

-- ---------------------------------------------------------------------------
-- Substitute duties: one professor offers a duty, another covers it.
--
-- The lifecycle is OFFERED, CLAIMED, APPROVED, COMPLETED, with REJECTED, WITHDRAWN and
-- EXPIRED as the ways it can end early. Points are awarded at COMPLETED and nowhere else.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS substitution_request (
    id                  IDENTITY PRIMARY KEY,
    slot_id             VARCHAR(40)  NOT NULL,
    room_id             VARCHAR(40)  NOT NULL,
    paper_code          VARCHAR(40)  NOT NULL,
    starts_at           TIMESTAMP    NOT NULL,
    ends_at             TIMESTAMP    NOT NULL,
    original_faculty_id VARCHAR(40)  NOT NULL,
    reason              VARCHAR(300),
    status              VARCHAR(20)  NOT NULL DEFAULT 'OFFERED',
    offered_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    offered_by          VARCHAR(254) NOT NULL,
    volunteer_faculty_id VARCHAR(40),
    volunteered_at      TIMESTAMP,
    decided_by          VARCHAR(254),
    decided_at          TIMESTAMP,
    completed_by        VARCHAR(254),
    completed_at        TIMESTAMP,
    outcome             VARCHAR(600),
    -- One live offer per room-sitting, as a key that is only set while the offer is open. A
    -- finished offer (completed, rejected, withdrawn, expired) has NULL here and so does not
    -- occupy the sitting, which leaves it free to be offered again; a unique constraint ignores
    -- NULLs, which is exactly the "only while open" that H2 cannot express as a partial index.
    open_sitting        VARCHAR(90)  AS (CASE WHEN status IN ('OFFERED', 'CLAIMED', 'APPROVED')
                                              THEN slot_id || ':' || room_id END),
    -- A volunteer may never be the person giving the duty up: covering your own duty and
    -- claiming points for it is the obvious way to cheat this, so the database refuses it too
    -- and not only the code that writes here.
    CONSTRAINT ck_substitution_not_self CHECK (volunteer_faculty_id IS NULL
            OR volunteer_faculty_id <> original_faculty_id),
    CONSTRAINT uq_substitution_open_sitting UNIQUE (open_sitting)
);

CREATE INDEX IF NOT EXISTS idx_substitution_status ON substitution_request (status);

-- ---------------------------------------------------------------------------
-- The points ledger. Append-only, like any ledger: a balance is the sum of its rows, and a
-- correction is another row rather than an edit, so the history always explains the balance.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS points_transaction (
    id              IDENTITY PRIMARY KEY,
    faculty_id      VARCHAR(40)  NOT NULL,
    -- EARNED, REDEEMED, EXPIRED or ADJUSTED. Points are signed: earned is positive, redeemed
    -- and expired are negative, and an adjustment may be either.
    kind            VARCHAR(20)  NOT NULL,
    points          INT          NOT NULL,
    substitution_id BIGINT,
    redemption_id   BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(254) NOT NULL,
    expires_at      TIMESTAMP,
    note            VARCHAR(300),
    -- One row per substitution per kind, enforced here and not only in the service that writes
    -- it. Two concurrent completions of the same substitution would otherwise each see no award
    -- and each write one; this is what actually stops the second. A unique constraint treats
    -- NULLs as distinct, so the rows that belong to no substitution — redemptions, expiries,
    -- corrections — are left unconstrained by it.
    CONSTRAINT uq_points_per_substitution UNIQUE (substitution_id, kind)
);

CREATE INDEX IF NOT EXISTS idx_points_faculty ON points_transaction (faculty_id);

-- ---------------------------------------------------------------------------
-- Redemptions: spending earned points to be excused a future duty.
--
-- A request, not a withdrawal. Points leave the ledger when an administrator approves, and the
-- duty is only given up if the room can still be staffed without the person — which is checked
-- at approval, because the answer can change between asking and deciding.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS duty_redemption (
    id           IDENTITY PRIMARY KEY,
    faculty_id   VARCHAR(40)  NOT NULL,
    slot_id      VARCHAR(40)  NOT NULL,
    room_id      VARCHAR(40)  NOT NULL,
    paper_code   VARCHAR(40)  NOT NULL,
    starts_at    TIMESTAMP    NOT NULL,
    points_spent INT          NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason       VARCHAR(300),
    requested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by   VARCHAR(254),
    decided_at   TIMESTAMP,
    outcome      VARCHAR(600),
    CONSTRAINT uq_duty_redemption_once UNIQUE (faculty_id, slot_id, room_id)
);

CREATE INDEX IF NOT EXISTS idx_duty_redemption_status ON duty_redemption (status);

-- The designation and explicit seniority of an imported faculty member. Added after the first
-- release: a database that already holds faculty keeps its rows and gains empty columns.
ALTER TABLE faculty ADD COLUMN IF NOT EXISTS designation VARCHAR(60);
ALTER TABLE faculty ADD COLUMN IF NOT EXISTS seniority_value INT;

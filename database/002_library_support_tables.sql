-- =============================================================================
-- Phase 3 — Step 2: Library app support tables (shared login via public.users)
-- Does NOT create library.users. Authentication uses public.users (Attendance).
-- Run after 001_create_library_schema.sql and Attendance public.users exists.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- student_profiles (Library student records; FK to public.users)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.student_profiles (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    version     BIGINT,
    student_id  VARCHAR(32)  NOT NULL,
    full_name   VARCHAR(160) NOT NULL,
    first_name  VARCHAR(80),
    last_name   VARCHAR(80),
    age         INTEGER,
    phone       VARCHAR(32),
    course      VARCHAR(120),
    user_id     BIGINT       NOT NULL,
    CONSTRAINT fk_student_profiles_user
        FOREIGN KEY (user_id) REFERENCES public.users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_library_student_profiles_student_id
    ON library.student_profiles (student_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_library_student_profiles_user_id
    ON library.student_profiles (user_id);

CREATE INDEX IF NOT EXISTS idx_student_profiles_student_id
    ON library.student_profiles (student_id);

CREATE INDEX IF NOT EXISTS idx_student_profiles_last_name
    ON library.student_profiles (last_name);

-- -----------------------------------------------------------------------------
-- otp_codes (registration / password-reset OTP — Library-specific)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.otp_codes (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    version     BIGINT,
    email       VARCHAR(255) NOT NULL,
    code        VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_otp_email_verified
    ON library.otp_codes (email, verified);

CREATE INDEX IF NOT EXISTS idx_otp_expires_at
    ON library.otp_codes (expires_at);

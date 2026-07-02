-- =============================================================================
-- Phase 3 — Shared authentication (Library + Attendance)
-- Single user store: public.users
-- Run once in Supabase SQL Editor (idempotent where possible).
-- =============================================================================

-- 1) Extend public.users for shared auth fields
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT true;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS full_name VARCHAR(160);
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

UPDATE public.users SET enabled = true WHERE enabled IS NULL;
UPDATE public.users SET version = 0 WHERE version IS NULL;
UPDATE public.users SET created_at = COALESCE(created_at, NOW()) WHERE created_at IS NULL;

-- Backfill email/full_name from Attendance domain profiles
UPDATE public.users u
SET email = s.email
FROM public.students s
WHERE s.user_id = u.id
  AND (u.email IS NULL OR TRIM(u.email) = '')
  AND s.email IS NOT NULL
  AND TRIM(s.email) <> '';

UPDATE public.users u
SET email = t.email
FROM public.teachers t
WHERE t.user_id = u.id
  AND (u.email IS NULL OR TRIM(u.email) = '')
  AND t.email IS NOT NULL
  AND TRIM(t.email) <> '';

UPDATE public.users u
SET full_name = s.full_name
FROM public.students s
WHERE s.user_id = u.id
  AND (u.full_name IS NULL OR TRIM(u.full_name) = '')
  AND s.full_name IS NOT NULL;

UPDATE public.users u
SET full_name = t.full_name
FROM public.teachers t
WHERE t.user_id = u.id
  AND (u.full_name IS NULL OR TRIM(u.full_name) = '')
  AND t.full_name IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_public_users_email
    ON public.users (LOWER(email))
    WHERE email IS NOT NULL AND TRIM(email) <> '';

-- 2) Migrate library.users -> public.users (only if legacy table exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'library' AND table_name = 'users'
    ) THEN
        -- Merge rows that share username (prefer BCrypt password from library)
        UPDATE public.users pu
        SET password = CASE
                WHEN lu.password LIKE '$2a$%' OR lu.password LIKE '$2b$%' THEN lu.password
                ELSE pu.password
            END,
            email = COALESCE(NULLIF(TRIM(pu.email), ''), NULLIF(TRIM(lu.email), '')),
            full_name = COALESCE(NULLIF(TRIM(pu.full_name), ''), NULLIF(TRIM(lu.full_name), '')),
            enabled = COALESCE(pu.enabled, true) OR COALESCE(lu.enabled, false),
            version = COALESCE(pu.version, lu.version, 0),
            updated_at = COALESCE(pu.updated_at, lu.updated_at),
            created_at = COALESCE(pu.created_at, lu.created_at, NOW())
        FROM library.users lu
        WHERE LOWER(pu.username) = LOWER(lu.username);

        -- Insert library-only accounts
        INSERT INTO public.users (
            username, password, role, email, full_name, enabled, version, created_at, updated_at
        )
        SELECT
            lu.username,
            lu.password,
            lu.role,
            lu.email,
            lu.full_name,
            COALESCE(lu.enabled, true),
            COALESCE(lu.version, 0),
            COALESCE(lu.created_at, NOW()),
            lu.updated_at
        FROM library.users lu
        WHERE NOT EXISTS (
            SELECT 1 FROM public.users pu WHERE LOWER(pu.username) = LOWER(lu.username)
        );

        -- Repoint library.student_profiles.user_id to public.users ids
        UPDATE library.student_profiles sp
        SET user_id = pu.id
        FROM library.users lu
        JOIN public.users pu ON LOWER(pu.username) = LOWER(lu.username)
        WHERE sp.user_id = lu.id;

        -- Replace FK to public.users
        ALTER TABLE library.student_profiles DROP CONSTRAINT IF EXISTS fk_student_profiles_user;
        ALTER TABLE library.student_profiles
            ADD CONSTRAINT fk_student_profiles_user
            FOREIGN KEY (user_id) REFERENCES public.users (id);

        DROP TABLE library.users;
    END IF;
END $$;

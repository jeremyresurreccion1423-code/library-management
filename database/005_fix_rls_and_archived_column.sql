-- Fix: app won't start if RLS is enabled without policies, or if archived column is missing.
-- Run in Supabase SQL Editor (Dashboard → SQL → New query).

-- 1) Required column for student archive feature
ALTER TABLE library.student_profiles
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT false;

-- 2) Disable RLS on Spring Boot backend tables (public + library schemas)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT n.nspname AS schema_name, c.relname AS table_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind = 'r'
          AND n.nspname IN ('public', 'library')
          AND c.relrowsecurity = true
    LOOP
        EXECUTE format('ALTER TABLE %I.%I DISABLE ROW LEVEL SECURITY', r.schema_name, r.table_name);
        RAISE NOTICE 'Disabled RLS on %.%', r.schema_name, r.table_name;
    END LOOP;
END $$;

-- 3) Check which tables still have RLS (should return 0 rows)
SELECT n.nspname AS schema_name, c.relname AS table_name, c.relrowsecurity AS rls_enabled
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'r'
  AND n.nspname IN ('public', 'library')
  AND c.relrowsecurity = true
ORDER BY 1, 2;

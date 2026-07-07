-- Runs before Hibernate schema validation (SharedAuthDataSourceMigrationProcessor)

ALTER TABLE library.student_profiles
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT false;

-- Spring Boot apps connect as postgres via JDBC (not Supabase anon/authenticated).
-- If RLS was enabled in the Supabase dashboard, disable it on app schemas so queries are not blocked.
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

# Library Database Migrations (Supabase PostgreSQL)

The Library app shares the **same Supabase PostgreSQL project** as the Attendance Management System.

| Schema | Owner app | Purpose |
|--------|-----------|---------|
| `public` | Attendance | Users, students, teachers, attendance (do not modify from Library scripts) |
| `library` | Library | All EduLibrary tables |

## Run order (Supabase SQL Editor)

1. `001_create_library_schema.sql` — creates `library` schema
2. `002_library_support_tables.sql` — `student_profiles`, `otp_codes` (FK to `public.users`)
3. `003_library_domain_tables.sql` — books, authors, categories, issues, reservations, ebooks, revenue

All scripts are **idempotent** (`IF NOT EXISTS`). Safe to re-run.

## Application config

After running the scripts, start the Library app with:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.default_schema=library
```

Set credentials in `src/main/resources/application-local.properties` (copy from `application-local.properties.example`).

Use the **same Supabase connection string** as Attendance (Session Pooler URL + `postgres.<project-ref>` username).

## Security features migration

Run `security_features.sql` in Supabase SQL Editor (shared `public.users` lockout columns + `library.audit_logs`):

```
security_features.sql
```

Adds:
- `public.users.failed_login_attempts`
- `public.users.locked_until`
- `library.audit_logs` (Audit Trail for Library)

Safe to re-run (`IF NOT EXISTS`).


| Phase | Scope |
|-------|-------|
| **Phase 2** | Library → Supabase; `library` schema SQL; isolated auth |
| **Phase 3** (current) | Shared login via `public.users`; `library.users` removed |

## Phase 3 migration

Run `004_shared_auth_phase3.sql` in Supabase SQL Editor **or** start either app once (auto-migration runs on startup via `SharedAuthDataSourceMigrationProcessor`).

If the app fails to start after enabling **RLS** in Supabase, run `005_fix_rls_and_archived_column.sql` in the SQL Editor.

After migration:
- All accounts live in `public.users`
- `library.student_profiles.user_id` references `public.users(id)`
- Legacy `library.users` is dropped if it existed

# Smart Library (EduLibrary)

Spring Boot library system for Laguna University.

## Stack

- Java 17, Spring Boot 3.3
- PostgreSQL (Supabase — **shared database with Attendance**)
- Thymeleaf, Spring Security, BCrypt
- Barcode/QR borrowing, e-books, analytics, email reminders

## Database (Phase 3 — Shared Auth)

Both apps use **one Supabase PostgreSQL database**:

| Schema | Application |
|--------|-------------|
| `public` | Attendance Management System (shared `users` table) |
| `library` | Library Management System |

### First-time setup

1. Copy `application-local.properties.example` to `src/main/resources/application-local.properties`
2. Set the **same Supabase credentials** as Attendance
3. Run SQL scripts in order (see `database/README.md`):
   - `001_create_library_schema.sql`
   - `002_library_support_tables.sql`
   - `003_library_domain_tables.sql`
   - `004_shared_auth_phase3.sql`
4. Start the app: `.\run.ps1`

## Demo logins

| Role | Username | Password | Login |
|------|----------|----------|-------|
| Library Admin | `admin` | `admin123` | `/admin/login` |
| Student | `student1` | `student123` | `/login` (shared with Attendance) |

New students can register at `/register`. Student credentials work in both Library and Attendance (shared `public.users`).

## Phase roadmap

| Phase | Status |
|-------|--------|
| Phase 2 | Library connected to Supabase; `library` schema migrations |
| Phase 3 | **Complete** — Shared login via `public.users`; `library.users` removed |

## System integration

Library and Attendance share one Supabase database, unified authentication, and bidirectional student profile sync between the `public` and `library` schemas.

## Documentation

- [Library database migrations](database/README.md)

-- =============================================================================
-- Phase 2 — Step 3: Library domain tables
-- books, authors, categories, book_issues, reservations, ebooks, admin_revenue
-- Does NOT modify public (Attendance) tables.
-- Run after 002_library_support_tables.sql (FKs reference student_profiles)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- authors
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.authors (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    version     BIGINT,
    name        VARCHAR(255) NOT NULL
);

-- -----------------------------------------------------------------------------
-- categories
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.categories (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    version     BIGINT,
    name        VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_library_categories_name
    ON library.categories (name);

-- -----------------------------------------------------------------------------
-- books
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.books (
    id               BIGSERIAL PRIMARY KEY,
    created_at       TIMESTAMP(6),
    updated_at       TIMESTAMP(6),
    version          BIGINT,
    isbn             VARCHAR(64),
    title            VARCHAR(512) NOT NULL,
    barcode          VARCHAR(256),
    qr_payload       VARCHAR(1024),
    available_copies INTEGER      NOT NULL CHECK (available_copies >= 0),
    total_copies     INTEGER      NOT NULL CHECK (total_copies >= 0),
    fine_per_day     NUMERIC(10, 2),
    author_id        BIGINT,
    category_id      BIGINT,
    CONSTRAINT fk_books_author
        FOREIGN KEY (author_id) REFERENCES library.authors (id),
    CONSTRAINT fk_books_category
        FOREIGN KEY (category_id) REFERENCES library.categories (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_library_books_barcode
    ON library.books (barcode)
    WHERE barcode IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_books_title
    ON library.books (title);

CREATE INDEX IF NOT EXISTS idx_books_available_copies
    ON library.books (available_copies);

CREATE INDEX IF NOT EXISTS idx_books_category
    ON library.books (category_id);

CREATE INDEX IF NOT EXISTS idx_books_author
    ON library.books (author_id);

-- -----------------------------------------------------------------------------
-- ebooks
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.ebooks (
    id                BIGSERIAL PRIMARY KEY,
    created_at        TIMESTAMP(6),
    updated_at        TIMESTAMP(6),
    version           BIGINT,
    original_filename VARCHAR(512)  NOT NULL,
    stored_path       VARCHAR(1024) NOT NULL,
    book_id           BIGINT        NOT NULL,
    CONSTRAINT fk_ebooks_book
        FOREIGN KEY (book_id) REFERENCES library.books (id),
    CONSTRAINT uk_library_ebooks_book_id UNIQUE (book_id)
);

-- -----------------------------------------------------------------------------
-- book_issues
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.book_issues (
    id                  BIGSERIAL PRIMARY KEY,
    created_at          TIMESTAMP(6),
    updated_at          TIMESTAMP(6),
    version             BIGINT,
    issued_at           TIMESTAMP(6) NOT NULL,
    due_at              TIMESTAMP(6) NOT NULL,
    returned_at         TIMESTAMP(6),
    fine_amount         NUMERIC(10, 2),
    status              VARCHAR(32)  NOT NULL
                            CHECK (status IN ('BORROWED', 'RETURNED', 'OVERDUE')),
    book_id             BIGINT       NOT NULL,
    student_profile_id  BIGINT       NOT NULL,
    CONSTRAINT fk_book_issues_book
        FOREIGN KEY (book_id) REFERENCES library.books (id),
    CONSTRAINT fk_book_issues_student
        FOREIGN KEY (student_profile_id) REFERENCES library.student_profiles (id)
);

CREATE INDEX IF NOT EXISTS idx_book_issues_student_status
    ON library.book_issues (student_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_book_issues_book_status
    ON library.book_issues (book_id, status);

CREATE INDEX IF NOT EXISTS idx_book_issues_due_status
    ON library.book_issues (due_at, status);

-- -----------------------------------------------------------------------------
-- reservations
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.reservations (
    id                  BIGSERIAL PRIMARY KEY,
    created_at          TIMESTAMP(6),
    updated_at          TIMESTAMP(6),
    version             BIGINT,
    queue_order         INTEGER      NOT NULL,
    status              VARCHAR(32)  NOT NULL
                            CHECK (status IN ('WAITING', 'FULFILLED', 'CANCELLED')),
    book_id             BIGINT       NOT NULL,
    student_profile_id  BIGINT       NOT NULL,
    CONSTRAINT fk_reservations_book
        FOREIGN KEY (book_id) REFERENCES library.books (id),
    CONSTRAINT fk_reservations_student
        FOREIGN KEY (student_profile_id) REFERENCES library.student_profiles (id)
);

CREATE INDEX IF NOT EXISTS idx_reservations_book_status_queue
    ON library.reservations (book_id, status, queue_order);

CREATE INDEX IF NOT EXISTS idx_reservations_student_status
    ON library.reservations (student_profile_id, status);

-- -----------------------------------------------------------------------------
-- admin_revenue
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS library.admin_revenue (
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMP(6),
    updated_at     TIMESTAMP(6),
    version        BIGINT,
    amount         NUMERIC(10, 2) NOT NULL,
    revenue_type   VARCHAR(255)   NOT NULL,
    book_issue_id  BIGINT         NOT NULL,
    CONSTRAINT fk_admin_revenue_book_issue
        FOREIGN KEY (book_issue_id) REFERENCES library.book_issues (id)
);

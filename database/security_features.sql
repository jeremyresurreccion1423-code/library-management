-- Security features migration (shared public.users + library audit)
-- Safe to re-run

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS library.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES public.users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    details TEXT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lib_audit_logs_created_at ON library.audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lib_audit_logs_action ON library.audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_users_locked_until ON public.users (locked_until);

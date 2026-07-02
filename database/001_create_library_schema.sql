-- =============================================================================
-- Phase 2 — Step 1: Library schema (Supabase PostgreSQL)
-- Safe: creates library schema only; does NOT touch public (Attendance) tables.
-- Run in Supabase SQL Editor before other library scripts.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS library;

COMMENT ON SCHEMA library IS 'EduLibrary domain tables (isolated from Attendance public schema)';

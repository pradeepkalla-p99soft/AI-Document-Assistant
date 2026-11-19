-- Users and Document Ownership Schema Migration
-- V3__create_users_and_ownership.sql

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Add owner_id to documents table
ALTER TABLE documents ADD COLUMN IF NOT EXISTS owner_id UUID;

-- Create index and foreign key constraint for document ownership
CREATE INDEX IF NOT EXISTS idx_documents_owner_id ON documents(owner_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_documents_owner'
    ) THEN
        ALTER TABLE documents
            ADD CONSTRAINT fk_documents_owner
            FOREIGN KEY (owner_id)
            REFERENCES users(id)
            ON DELETE CASCADE;
    END IF;
END $$;


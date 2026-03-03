-- V2__create_refresh_tokens.sql (PostgreSQL)

-- For gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,

  -- Deterministic hash (HMAC-SHA256) of the *opaque* refresh token
  token_hash VARCHAR(64) NOT NULL,

  -- Token family/session: supports multiple devices/sessions per user
  family_id UUID NOT NULL,
  session_id UUID NOT NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,

  revoked_at TIMESTAMPTZ NULL,

  -- Rotation linkage
  replaced_by_token_id UUID NULL,

  -- Optional device hints
  user_agent TEXT NULL,
  ip_address VARCHAR(64) NULL,

  CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

  CONSTRAINT fk_refresh_tokens_replaced_by
    FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_session_id ON refresh_tokens(session_id);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires_at ON refresh_tokens(expires_at);
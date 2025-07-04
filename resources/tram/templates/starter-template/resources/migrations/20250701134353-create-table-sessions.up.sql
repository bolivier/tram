CREATE TABLE sessions (
  id SERIAL PRIMARY KEY,
  expires_at TIMESTAMPTZ NOT NULL,
  user_id INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)

--;;

CREATE TRIGGER "set_updated_at_on_:sessions" BEFORE
UPDATE
  ON sessions FOR EACH row EXECUTE FUNCTION update_updated_at_column()
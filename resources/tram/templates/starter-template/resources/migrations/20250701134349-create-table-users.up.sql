CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  email CITEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)

--;;

CREATE TRIGGER "set_updated_at_on_:users" BEFORE
UPDATE
  ON users FOR EACH row EXECUTE FUNCTION update_updated_at_column()
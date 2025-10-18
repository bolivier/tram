CREATE TABLE accounts (
  id SERIAL PRIMARY KEY
)

--;;

CREATE TABLE settings (
  id SERIAL PRIMARY KEY
)

--;;

CREATE TABLE birds (
  id SERIAL PRIMARY KEY
)

--;;

CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  name text UNIQUE NOT NULL,
  account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  bird_id INTEGER references birds(id) ON DELETE CASCADE
)

--;;

CREATE TABLE settings_users (
  id SERIAL PRIMARY KEY,
  setting_id INTEGER NOT NULL REFERENCES settings(id) ON DELETE CASCADE,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE
)

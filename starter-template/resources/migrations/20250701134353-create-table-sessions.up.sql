CREATE TABLE sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  expires_at TEXT NOT NULL,
  user_id INTEGER REFERENCES users(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
)

--;;

CREATE TRIGGER set_updated_at_on_sessions
AFTER UPDATE ON sessions FOR EACH ROW
BEGIN
  UPDATE sessions SET updated_at = datetime('now') WHERE id = NEW.id;
END

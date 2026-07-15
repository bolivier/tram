# ---
name: create-migration
description: Generate `.up.sql` and `.down.sql` migration files through a short conversation. 
---

## How to Run This Skill

Start a conversation with the user. Ask questions until you have enough to write
the SQL, then write the files directly. The SQL dialect is SQLite.

---

## Step 1 — Ask What Kind of Migration

Ask:
> "What kind of migration do you need? (create table / add column / drop column / add index / custom SQL)"

## Step 2 — Ask for the Table Name

If not already given, ask for the table name.

## Step 3 — Gather Columns (for create-table or add-column)

Ask:
> "What columns do you need? (`id` and timestamp columns are included automatically)"

For each column, determine (through natural conversation — don't ask all at once):
- Name
- Type (translate from plain language — see mapping below)
- Nullable? (default: not nullable unless user says "optional")
- Unique?
- Default value?
- Foreign key to another table?
- Index?

After each column, ask:
> "Anything else to add? (do you need indexes, for example?)"

Keep asking until the user says no or is clearly done.

## Step 4 — Summarize

Before writing files, show a plain-English summary and call out likely mistakes
or bad design:

> "I'll create a `posts` table with:
> - `title` — TEXT, required
> - `body` — TEXT, optional
> - `user_id` — INTEGER REFERENCES users(id)
> - `account_id` — INTEGER
>
> Plus auto-added: `id`, `created_at`, `updated_at`
>
> `account_id` looks like a foreign key, but is only an integer 
>
> `user_id` is not required, but probably should be
>
> Shall I write the files?"


## Step 5 — Write the Files

Use the current date/time (`$CURRENT_DATE`) formatted as `YYYYMMDDHHmmss` for the timestamp.

Write two files:
- `resources/migrations/<timestamp>-<migration-name>.up.sql`
- `resources/migrations/<timestamp>-<migration-name>.down.sql`

## Step 6 — Remind the User

After writing:
> "Files written. Run `(db/migrate)` in the REPL to apply the migration."

---

## Column Type Mapping

SQLite has few real types: every value is INTEGER, REAL, TEXT, BLOB, or NULL.
The declared type still matters — it drives affinity, and Tram's Toucan2
integration reads booleans back by their declared type — so declare the type in
the right column below rather than reaching for a Postgres one.

| User says                                         | SQL type                         |
|---------------------------------------------------|----------------------------------|
| string / short text / name                        | TEXT                             |
| long text / body / content                        | TEXT                             |
| email                                             | TEXT COLLATE NOCASE              |
| number / integer / count                          | INTEGER                          |
| big number                                        | INTEGER                          |
| decimal / price / money                           | INTEGER (store minor units, e.g. cents) |
| yes/no / boolean / flag                           | BOOLEAN                          |
| date                                              | TEXT                             |
| datetime / timestamp                              | TEXT                             |
| uuid / identifier                                 | TEXT                             |
| json / object                                     | TEXT                             |
| reference to \<table\> / foreign key to \<table\> | INTEGER REFERENCES \<table\>(id) |

Notes on the ones that differ from Postgres:

- **email** — SQLite has no `CITEXT`. `TEXT COLLATE NOCASE` gives the same
  case-insensitive comparison and uniqueness.
- **boolean** — declare `BOOLEAN`. SQLite stores 0/1 and the driver hands back
  an Integer; `tram.db.sqlite` converts it to a real boolean on read, keyed on
  the declared type.
- **money** — SQLite's `NUMERIC`/`REAL` is floating point and will lose cents.
  Store minor units in an INTEGER.
- **date / datetime** — SQLite has no date type. Store TEXT in
  `datetime('now')` format, which sorts and compares correctly.
- **uuid / json** — no native types; store TEXT.

---

## Auto-Added Columns (never ask about these)

Every `CREATE TABLE` automatically gets:
- `id INTEGER PRIMARY KEY AUTOINCREMENT`
- `created_at TEXT NOT NULL DEFAULT (datetime('now'))`
- `updated_at TEXT NOT NULL DEFAULT (datetime('now'))`
- An `updated_at` trigger

---

Leave these off if the user specifically requests it.

## SQL Templates

### Create Table — up

```sql
CREATE TABLE <table> (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  <col> <TYPE> [NOT NULL] [UNIQUE] [DEFAULT <val>] [REFERENCES <other>(id)],
  ...,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
)

--;;

CREATE TRIGGER set_updated_at_on_<table>
AFTER UPDATE ON <table> FOR EACH ROW
BEGIN
  UPDATE <table> SET updated_at = datetime('now') WHERE id = NEW.id;
END
```

### Create Table — down

```sql
DROP TABLE <table>
```

Dropping a table drops its triggers and indexes too — they need no separate
statement.

### Add Column — up

```sql
ALTER TABLE <table> ADD COLUMN <name> <TYPE> [NOT NULL DEFAULT <val>] [REFERENCES <other>(id)]
```

A `NOT NULL` column added to an existing table must carry a `DEFAULT`, since
SQLite has to put something in the existing rows.

### Add Column — down

```sql
ALTER TABLE <table> DROP COLUMN <name>
```

### Add Index — up

```sql
CREATE INDEX <index_name> ON <table> (<col>)
```

SQLite has no auto-generated index names: name the index yourself, by convention
`idx_<table>_<col>`.

### Add Index — down

```sql
DROP INDEX <index_name>
```

---

## What SQLite Will Not Let You Do

`ALTER TABLE` only renames a table/column, adds a column, or drops a column.
Changing a column's type, adding or removing a constraint, or changing a default
has no statement. Any of those means rebuilding the table in one migration:
create the new table under a temporary name, `INSERT INTO ... SELECT` the data
across, drop the original, and rename the new one into its place.

If a migration seems to want one of those, say so before writing it — a table
rebuild is worth the user's agreement first.

---

## File Format Notes

- Multiple statements in one file are separated by `\n\n--;\;\n\n` (without the backslash — that is a literal `--;;` on its own line, surrounded by blank lines)
- `init.sql` does nothing on SQLite: there are no extensions to enable and no
  shared trigger function, which is why each table declares its own `updated_at`
  trigger

---

## Example Output

For a `posts` table with `title` (required), `body` (optional), `user_id` (FK to users):

**`resources/migrations/20250801120000-create-table-posts.up.sql`:**
```sql
CREATE TABLE posts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  body TEXT,
  user_id INTEGER REFERENCES users(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
)

--;;

CREATE TRIGGER set_updated_at_on_posts
AFTER UPDATE ON posts FOR EACH ROW
BEGIN
  UPDATE posts SET updated_at = datetime('now') WHERE id = NEW.id;
END
```

**`resources/migrations/20250801120000-create-table-posts.down.sql`:**
```sql
DROP TABLE posts
```

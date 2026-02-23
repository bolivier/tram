# ---
name: create-migration
description: Generate `.up.sql` and `.down.sql` migration files through a short conversation. 
---

## How to Run This Skill

Start a conversation with the user. Ask questions until you have enough to write
the SQL, then write the files directly. The SQL dialect is postgres.

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

| User says                                         | SQL type                         |
|---------------------------------------------------|----------------------------------|
| string / short text / name                        | TEXT                             |
| long text / body / content                        | TEXT                             |
| email                                             | CITEXT                           |
| number / integer / count                          | INTEGER                          |
| big number                                        | BIGINT                           |
| decimal / price / money                           | NUMERIC                          |
| yes/no / boolean / flag                           | BOOLEAN                          |
| date                                              | DATE                             |
| datetime / timestamp                              | TIMESTAMPTZ                      |
| uuid / identifier                                 | UUID                             |
| json / object                                     | JSONB                            |
| reference to \<table\> / foreign key to \<table\> | INTEGER REFERENCES \<table\>(id) |

---

## Auto-Added Columns (never ask about these)

Every `CREATE TABLE` automatically gets:
- `id SERIAL PRIMARY KEY`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- An `updated_at` trigger

---

Leave these off if the user specifically requests it.

## SQL Templates

### Create Table — up

```sql
CREATE TABLE <table> (
  id SERIAL PRIMARY KEY,
  <col> <TYPE> [NOT NULL] [UNIQUE] [DEFAULT <val>] [REFERENCES <other>(id)],
  ...,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)

--;;

CREATE TRIGGER "set_updated_at_on_<table>" BEFORE
UPDATE
  ON <table> FOR EACH row EXECUTE FUNCTION update_updated_at_column()
```

### Create Table — down

```sql
DROP TABLE <table>
```

### Add Column — up

```sql
ALTER TABLE <table> ADD COLUMN <name> <TYPE> [NOT NULL] [UNIQUE] [DEFAULT <val>] [REFERENCES <other>(id)]
```

### Add Column — down

```sql
ALTER TABLE <table> DROP COLUMN <name>
```

### Add Index — up

```sql
CREATE INDEX ON <table> (<col>)
```

### Add Index — down

```sql
DROP INDEX <index_name>
```

---

## File Format Notes

- Multiple statements in one file are separated by `\n\n--;\;\n\n` (without the backslash — that is a literal `--;;` on its own line, surrounded by blank lines)
- The `citext` extension, `uuid-ossp` extension, and `update_updated_at_column()` trigger function are pre-established by `init.sql` — do not re-create them

---

## Example Output

For a `posts` table with `title` (required), `body` (optional), `user_id` (FK to users):

**`resources/migrations/20250801120000-create-table-posts.up.sql`:**
```sql
CREATE TABLE posts (
  id SERIAL PRIMARY KEY,
  title TEXT NOT NULL,
  body TEXT,
  user_id INTEGER REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)

--;;

CREATE TRIGGER "set_updated_at_on_posts" BEFORE
UPDATE
  ON posts FOR EACH row EXECUTE FUNCTION update_updated_at_column()
```

**`resources/migrations/20250801120000-create-table-posts.down.sql`:**
```sql
DROP TABLE posts
```



# Runtimes

Some namespaces are used as a developer **runtime**. They are meant to write and
evaluate code as part of a task and then the code is discarded when the task is
complete.

Developer runtimes are
* used for evaluating code with side effects
* ephemeral
* not checked into git
* deleted once the task is completed

The only use cases directly supported by Tram are migration runtimes for the
database.

## Migration Runtimes

Migration runtimes let you write sql migrations without directly writing sql.

They have 2 parts. A migration blueprint, and a commented function call to write
that blueprint to the sql file.

The blueprint is meant to define all the required data to create a sql migration
file that you can run with `tram.migrations/migrate`.

### Root

These are keys related to the file itself.

| key            | notes                                                                       |
|----------------|-----------------------------------------------------------------------------|
| migration name | the name of the migration file (after the timestamp)                        |
| timestamp      | value of the timestamp (keeps the same file)                                |
| actions        | represents SQL code to put into the migration.  Each is separated by a --;; |


### Action keys

These are keys related to the particular action you're taking. They must be
reversible in the down-migration.

| key        | notes                                                                        |
|------------|------------------------------------------------------------------------------|
| type       | type of migration.  Only supports :create-table right now.                   |
| table      | table name to create                                                         |
| timestamps | boolean indicating presence of default `created_at` and `updated_at` columns |
| attributes | attributes of he table to create                                             |


### Attribute keys

These are the keys for the attributes of the table being set.

| key       | notes                                                                            |
|-----------|----------------------------------------------------------------------------------|
| name      | name of the column                                                               |
| type      | data type of the column                                                          |
| required? | is the column required (ie. NOT NULL) - defaults to `true`                       |
| unique?   | is the column unique                                                             |
| default   | Either a value or db fn (use keyword ns :fn for functions)                       |
| trigger   | Write a database trigger for this column (only :update-updated-at supported now) |

Types are normal Postgres types.

For references, set the type to `:reference` and the name to `(keyword (str
singular-table-name "-id"))` and the correct table can be inferred. An override
is available with the key `:table-name`.

### Creation

To create a migration runtime from the Tram CLI you can run something like

```
$ tram generate migration create-users-table
```

It's recommended to create from the CLI even if you don't use any of the
shorthand, because the namespace can be set up automatically for you.

### Usage

By default, the migration blueprint looks something like this

```clojure
(def blueprint
  {:migration-name "my-fancy-migration"
   :timestamp      "20251015180142"
   :actions        [{:type       :create-table
                     :table      "my-table-name"
                     :timestamps true
                     :attributes [{:name :id
                                   :type :primary-key}]}]})
```

This blueprint can be written to the migration file by executing a line of code
in a comment sexp at the bottom of the file.

```clojure
(comment
  ;; run this to re/write the migration file
  (write-to-migration-files blueprint)
  nil)
```

Evaluate that function and the migration is written to the appropriate file it
specifies.

NOTE: Migrations are not checked for errors on write. Invalid migrations will
fail when you evaluate `tram.migrations/migrate`.

These are the default attributes that all tables have.  Let's look more closely
at a few of them.

### Primary Key

```clojure
{:name :id
 :type :primary-key}
```

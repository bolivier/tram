# Associations

Tram only supports 3 kinds of associations.  

- has-one (implicit)
- has-many (call `tram.hydration/has-many!`)
- belongs-to (call `tram.hydration/belongs-to!`)

## Rationale

ORMs are very complex, they quickly get out of control , and anything too
complex is usually better handled at the level of sql. 

That said, it's not fun to constantly rewrite simple queries for all your
models.  To that end, Tram provides some simple associations and querying that
can be done with minimal configuration.

## Usage

Call an association in your model files, like this 

```clj
(tram.hydration/has-many! :models/users :models/projects :through :users-projects)
```

This declares that a user has many projects, and there exists a join table
users_projects that connects them. 

After this, you can call `(t2/hydrate <user-model> :projects)` and get back the
user model with the projects associated. 

This requires a `users` table and a `projects` table, but they don't require any
foreign keys.

`users_projects`
| column     | value                       |
|------------|-----------------------------|
| user-id    | foreign key to users(id)    |
| project-id | foreign key to projects(id) |


You can also use `belongs-to!` like this 

```clj
(tram.hydration/belongs-to! :models/users :models/settings)
```

This declares that a foreign key exists on a table `settings` which refers to
the table `users`.

This association requires these tables

`users`
| column | value                 |
|--------|-----------------------|
| id     | primary key for users |

`settings`
| column  | value                    |
|---------|--------------------------|
| user-id | foreign key to users(id) |

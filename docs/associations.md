# Associations

Tram only supports 3 kinds of associations.  

- has-one (implicit)
- has-many (call `tram.associations/has-many!`)
- belongs-to (call `tram.associations/belongs-to!`)

## Rationale

ORMs are complex. Nontrivial queries are better handled with raw-sql.

That said, it sucks to constantly rewrite simple queries for all your models. To
that end, Tram provides some simple associations and querying that can be done
with minimal configuration.

Associations are handled via a mechanism called **hydration**. Hydration is when
a model has additional keys inserted into it that are held in other tables.

If a model has a foreign key on it, that model can be hydrated without
configuration. For example, a model `:models/book` with a fk to
`:models/authors` called `:author-id` requires no configuration to allow for
hydration via `t2/hydrate`.

Hydrating models with a belongs-to relationship require configuration. You'll
have to define an association with either `tram.associations/belongs-to!` for
models that have a foreign key on another model, or
`tram.associations/has-many!` for models that have many other models via a
foreign key on another table or use a join table.

## Usage

In these examples, there are 4 relevant tables (all abbreviated to only the
relevant parts).

An `accounts` table representing a paying account
| column | value       |
|--------|-------------|
| id     | primary-key |

A `users` table representing users of an account
| column     | value                       |
|------------|-----------------------------|
| id         | primary-key                 |
| account_id | foreign key to accounts(id) |

A `settings` table for user settings
| column | value       |
|--------|-------------|
| id     | primary-key |

A join table for users and settings
`settings_users`
| column     | value                       |
|------------|-----------------------------|
| id         | primary-key                 |
| setting-id | foreign key to settings(id) |
| user-id    | foreign key to users(id)    |


To enable hydration of settings, create a has-many association for settings on users. 

```clj
(tram.associations/has-many! :models/users :models/settings)
```

The join table is called, by
convention, settings_users. Concat the two table names in alphabetical order.

The table can be specified with `:through`, although I don't recommend this.

```clj
(tram.associations/has-many! :models/users :models/settings :through :settings-for-users)
```

The table name should be a kebab-case keyword here.

With that done, you can call hydrate settings. Note that the hydration keywords
are not namespaced.

```clj
(t2/hydrate <user-model-instance> :settings)
```

### Belongs To

Enable hydration of user on account like this 

```clj
(tram.associations/belongs-to! :models/accounts :models/users)
```

You can hydrate an account model like this 

```clj
(t2/hydrate <account-model-instance> :user)
```


Note this is singular because this is a 1-1 relationship.

### Has One

`has-one` associations are implicit and will work whenever a table has a foreign
key.  In this instance you would write 

```clj
(t2/hydrate <settings-model-instance> :user)
```

and you would have the user object

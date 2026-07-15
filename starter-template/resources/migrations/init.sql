-- Migratus requires an init script. SQLite has no extensions to install and no
-- procedural language to define a shared updated_at function in, so each table
-- carries its own updated_at trigger instead and there is nothing to do here.
SELECT 1;

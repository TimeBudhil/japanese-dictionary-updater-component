-- Read-only login for apps that query the dictionary data (the future API backend).
-- Run by the `db-init` compose service on every `docker compose up`, so it must
-- stay idempotent. It works on a fresh volume (before Flyway has created any
-- table) and on an existing one.

DO $$
BEGIN
	IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'dictionary_reader') THEN
		CREATE ROLE dictionary_reader LOGIN PASSWORD 'dictionary_reader';
	END IF;
END
$$;

GRANT CONNECT ON DATABASE updater TO dictionary_reader;
GRANT USAGE ON SCHEMA public TO dictionary_reader;
-- Tables that exist now...
GRANT SELECT ON ALL TABLES IN SCHEMA public TO dictionary_reader;
-- ...and tables Flyway (running as `updater`) creates later.
ALTER DEFAULT PRIVILEGES FOR ROLE updater IN SCHEMA public GRANT SELECT ON TABLES TO dictionary_reader;

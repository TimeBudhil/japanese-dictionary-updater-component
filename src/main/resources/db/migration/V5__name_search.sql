  CREATE EXTENSION IF NOT EXISTS pg_trgm;
  -- Values joined by spaces, with a leading space, so `LIKE '% q%'` is a word-start match
  -- that the trigram index serves even for 2-character queries (田中). array_to_string is
  -- only STABLE, so it can't be indexed directly; these wrappers are IMMUTABLE.
  CREATE FUNCTION name_entry_jp_text(kanji text[], readings text[]) RETURNS text
      LANGUAGE sql IMMUTABLE PARALLEL SAFE
      AS $$ SELECT ' ' || array_to_string(kanji || readings, ' ') $$;
  CREATE FUNCTION name_entry_en_text(translations text[]) RETURNS text
      LANGUAGE sql IMMUTABLE PARALLEL SAFE
      AS $$ SELECT ' ' || lower(array_to_string(translations, ' ')) $$;
  CREATE INDEX name_entry_jp_text_idx ON name_entry USING gin (name_entry_jp_text(kanji, readings) gin_trgm_ops);
  CREATE INDEX name_entry_en_text_idx ON name_entry USING gin (name_entry_en_text(translations) gin_trgm_ops);
  
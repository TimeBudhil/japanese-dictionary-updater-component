-- Keep the JMdict order of a sense's glosses, parts of speech and misc tags (the first
-- gloss is the most important one). Each list gets a 0-based order column, which with
-- sense_id is the table's primary key. The dictionary service reads these exact names
-- (gloss_order, pos_order, misc_order). Sense maps them with @OrderColumn.
--
-- Existing rows have no recorded order, and none can be rebuilt from the table, so they
-- are deleted, and the jmdict feed's version row is removed: the next run sees the file
-- as changed and re-imports every entry, writing the order. Until that run, senses have
-- no glosses or tags (decided 2026-11-14: drop and re-import, no order guessing).

DELETE FROM sense_gloss;
DELETE FROM sense_pos;
DELETE FROM sense_misc;
DELETE FROM source_version WHERE feed_id = 'jmdict';

ALTER TABLE sense_gloss ADD COLUMN gloss_order integer NOT NULL CHECK (gloss_order >= 0);
ALTER TABLE sense_pos   ADD COLUMN pos_order   integer NOT NULL CHECK (pos_order >= 0);
ALTER TABLE sense_misc  ADD COLUMN misc_order  integer NOT NULL CHECK (misc_order >= 0);

ALTER TABLE sense_gloss ADD CONSTRAINT sense_gloss_pkey PRIMARY KEY (sense_id, gloss_order);
ALTER TABLE sense_pos   ADD CONSTRAINT sense_pos_pkey   PRIMARY KEY (sense_id, pos_order);
ALTER TABLE sense_misc  ADD CONSTRAINT sense_misc_pkey  PRIMARY KEY (sense_id, misc_order);

-- The primary keys start with sense_id, so they index the foreign key (V3's guard,
-- UpdaterComponentApplicationIT.everyForeignKeyColumnIsIndexed, accepts them).
DROP INDEX sense_gloss_sense_id_idx;
DROP INDEX sense_pos_sense_id_idx;
DROP INDEX sense_misc_sense_id_idx;

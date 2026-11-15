package badee.updaterComponent.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V4 on a database that already holds data (the case Flyway meets on an existing
 * install): sense lists before V4 have no order, so V4 deletes them and forgets the
 * jmdict feed version, which makes the next run re-import every entry.
 */
class SenseListOrderMigrationIT {

	private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
	private static JdbcTemplate jdbc;
	private static DriverManagerDataSource dataSource;

	@BeforeAll
	static void start() {
		postgres.start();
		dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void v4ClearsUnorderedSenseListsAndForcesAJmdictReimport() {
		flyway("3").migrate();
		jdbc.update("INSERT INTO vocab_entry (ent_seq, common) VALUES (1000001, true)");
		jdbc.update("INSERT INTO sense (id, ent_seq) VALUES (1, 1000001)");
		jdbc.update("INSERT INTO sense_gloss (sense_id, gloss) VALUES (1, 'b'), (1, 'a')");
		jdbc.update("INSERT INTO sense_pos (sense_id, pos) VALUES (1, 'noun')");
		jdbc.update("INSERT INTO sense_misc (sense_id, misc) VALUES (1, 'colloquial')");
		jdbc.update("INSERT INTO source_version (feed_id, content_hash) VALUES ('jmdict', 'h1'), ('tatoeba', 'h2')");

		flyway(null).migrate();

		assertEquals(0, count("sense_gloss") + count("sense_pos") + count("sense_misc"));
		assertEquals(1, count("vocab_entry"), "entries stay; the re-import updates them");
		assertEquals(1, count("sense"));
		assertEquals(List.of("tatoeba"), jdbc.queryForList(
				"SELECT feed_id FROM source_version ORDER BY 1", String.class),
				"only the jmdict version is forgotten");
		assertEquals(List.of("sense_id", "gloss_order"), jdbc.queryForList("""
				SELECT a.attname FROM pg_index i
				JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
				WHERE i.indrelid = 'sense_gloss'::regclass AND i.indisprimary
				ORDER BY array_position(i.indkey::int2[], a.attnum)""", String.class));
	}

	private static Flyway flyway(String target) {
		var config = Flyway.configure().dataSource(dataSource).cleanDisabled(true);
		if (target != null) {
			config.target(target);
		}
		return config.load();
	}

	private static int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}
}

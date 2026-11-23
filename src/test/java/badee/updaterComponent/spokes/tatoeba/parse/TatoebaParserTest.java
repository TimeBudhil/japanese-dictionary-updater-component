package badee.updaterComponent.spokes.tatoeba.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import badee.updaterComponent.spokes.tatoeba.domain.ExampleSentence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TatoebaParserTest {

	// A-line: japanese <tab> english #ID=<eng>_<jpn>; B-line: index headwords.
	// As in the real corpus, two Japanese variants share one English sentence (id 300).
	private static final String FIXTURE =
			"A: 私は日本語を話します。\tI speak Japanese.#ID=300_100\n"
			+ "B: 私 は 日本語 話す\n"
			+ "A: 日本語を話します。\tI speak Japanese.#ID=300_101\n"
			+ "B: 日本語 話す\n"
			+ "A: これはペンです。\tThis is a pen.#ID=301_102\n"
			+ "B: 此れ は ペン\n";

	@Test
	void parsesPairsAndKeysOnJapaneseId(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("examples.utf");
		Files.writeString(file, FIXTURE, StandardCharsets.UTF_8);

		List<ExampleSentence> sentences = new ArrayList<>();
		new TatoebaParser().parse(file, sentences::add);

		assertEquals(List.of(100L, 101L, 102L), sentences.stream().map(ExampleSentence::getId).toList(),
				"Japanese variants sharing an English translation keep distinct ids");

		ExampleSentence first = sentences.get(0);
		assertEquals("私は日本語を話します。", first.getJapanese());
		assertEquals("I speak Japanese.", first.getEnglish());
		assertEquals("私 は 日本語 話す", first.getIndexWords());

		assertEquals("日本語を話します。", sentences.get(1).getJapanese());
		assertEquals("This is a pen.", sentences.get(2).getEnglish());
	}

	@Test
	void headwordsStripReadingSenseAndFormMarkers() {
		var h = TatoebaParser.parseHeadwords("其の[01]{その} 猫(ねこ)[01] 食べる{食べない}");

		assertEquals(List.of("其の", "猫", "食べる"), h.headwords());
		assertEquals(List.of(), h.exampleFor());
	}

	@Test
	void tildeTokensGoToExampleForAndRepeatedWordsAppearOnce() {
		var h = TatoebaParser.parseHeadwords("彼(かれ)[01] は 忙しい~ 彼 忙しい{忙しかった}~ は");

		assertEquals(List.of("彼", "は", "忙しい"), h.headwords());
		assertEquals(List.of("忙しい"), h.exampleFor());
	}

	@Test
	void blankOrMarkerOnlyIndexWordsGiveNoHeadwords() {
		var h = TatoebaParser.parseHeadwords("  [01] ~ ");

		assertEquals(List.of(), h.headwords());
		assertEquals(List.of(), h.exampleFor());
	}
}

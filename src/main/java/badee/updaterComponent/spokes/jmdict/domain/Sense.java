package badee.updaterComponent.spokes.jmdict.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * A single sense of a {@link VocabEntry} (the {@code <sense>} element): a set of
 * English glosses plus part-of-speech and misc tags.
 *
 * <p>Each list keeps the JMdict document order (most important gloss first) in a
 * 0-based order column ({@code gloss_order}, {@code pos_order}, {@code misc_order},
 * Flyway V4), which with {@code sense_id} is the table's primary key. The dictionary
 * service reads these exact column names.
 */
@Entity
@Table(name = "sense")
public class Sense {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "ent_seq")
	private VocabEntry vocabEntry;

	@ElementCollection
	@CollectionTable(name = "sense_gloss", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "gloss_order")
	@Column(name = "gloss", columnDefinition = "text") // a few real JMdict glosses exceed 255 chars
	private List<String> glosses = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "sense_pos", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "pos_order")
	@Column(name = "pos")
	private List<String> partsOfSpeech = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "sense_misc", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "misc_order")
	@Column(name = "misc")
	private List<String> miscTags = new ArrayList<>();

	public Sense() {
	}

	public Long getId() {
		return id;
	}

	public VocabEntry getVocabEntry() {
		return vocabEntry;
	}

	void setVocabEntry(VocabEntry vocabEntry) {
		this.vocabEntry = vocabEntry;
	}

	public List<String> getGlosses() {
		return glosses;
	}

	public List<String> getPartsOfSpeech() {
		return partsOfSpeech;
	}

	public List<String> getMiscTags() {
		return miscTags;
	}
}

package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.unifiedsearch.source.DocumentRepository;
import org.openmrs.module.unifiedsearch.source.VirtualDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Builds all 13 in-memory indices once at module startup (six local word + six
 * local n-gram + one global pair). Duration is logged for tugas 09 verification.
 */
@Component("unifiedsearch.indexBuilder")
public class IndexBuilder {

	private static final Log log = LogFactory.getLog(IndexBuilder.class);

	public static final int NGRAM = 4;

	public static final double EPS = 0.05;

	public static final int K_RRF = 20;

	private final DocumentRepository repository;

	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();

	private final TokenFunction gramTokenizer = new TokenFunction() {

		@Override
		public List<String> tokenize(String s) {
			return Tokenizer.charGrams(s, NGRAM);
		}
	};

	private final Object lock = new Object();

	private volatile boolean ready;

	private volatile long buildDurationMs;

	private Map<String, FusionSearch> lokal = new TreeMap<String, FusionSearch>();

	private GlobalIndex global;

	private Map<String, VirtualDocument> documentsByKunci = new TreeMap<String, VirtualDocument>();

	private RankingEngine engine;

	@Autowired
	public IndexBuilder(DocumentRepository repository) {
		this.repository = repository;
	}

	public boolean isReady() {
		return ready;
	}

	public long getBuildDurationMs() {
		return buildDurationMs;
	}

	public Map<String, FusionSearch> getLokal() {
		ensureBuilt();
		return lokal;
	}

	public GlobalIndex getGlobal() {
		ensureBuilt();
		return global;
	}

	public Map<String, VirtualDocument> getDocumentsByKunci() {
		ensureBuilt();
		return documentsByKunci;
	}

	/**
	 * The engine is built once alongside the indices and reused across requests — it holds no
	 * per-query state (RankingEngine.search() takes query/mode/alpha as arguments), only
	 * references into {@link #lokal}/{@link #global}, which themselves only change on a full
	 * {@link #build()}. Investigated for C4 (tugas 09 p95 latency > 50ms): constructing a new
	 * RankingEngine per request copies {@code lokal} into a fresh TreeMap on every call.
	 */
	public RankingEngine createEngine() {
		ensureBuilt();
		return engine;
	}

	public void ensureBuilt() {
		if (!ready) {
			synchronized (lock) {
				if (!ready) {
					build();
				}
			}
		}
	}

	public void build() {
		long mulai = System.nanoTime();
		log.info("Unified search index build starting ...");

		List<VirtualDocumentList> perEntitas = repository.loadPerEntitas();
		Map<String, FusionSearch> lokalBaru = new TreeMap<String, FusionSearch>();
		Map<String, VirtualDocument> dokumenBaru = new TreeMap<String, VirtualDocument>();
		List<SurfaceForm> semuaForm = new ArrayList<SurfaceForm>();

		for (VirtualDocumentList list : perEntitas) {
			List<SurfaceForm> forms = extractor.extractAll(list.getDokumen());
			for (VirtualDocument d : list.getDokumen()) {
				dokumenBaru.put(d.getKunci(), d);
			}
			semuaForm.addAll(forms);

			TfIdfIndex indeksKepingan = new TfIdfIndex(gramTokenizer);
			indeksKepingan.build(teks(forms));
			TfIdfIndex indeksKata = new TfIdfIndex(Tokenizer::words);
			indeksKata.build(teks(forms));
			lokalBaru.put(list.getEntitas(), new FusionSearch(indeksKata, indeksKepingan, forms));
		}

		TfIdfIndex globalKata = new TfIdfIndex(Tokenizer::words);
		globalKata.build(teks(semuaForm));
		TfIdfIndex globalKepingan = new TfIdfIndex(gramTokenizer);
		globalKepingan.build(teks(semuaForm));
		GlobalIndex globalBaru = new GlobalIndex(globalKata, globalKepingan, semuaForm);

		this.lokal = lokalBaru;
		this.global = globalBaru;
		this.documentsByKunci = dokumenBaru;
		this.engine = new RankingEngine(lokalBaru, globalBaru, EPS, K_RRF);
		this.buildDurationMs = (System.nanoTime() - mulai) / 1000000L;
		this.ready = true;

		// RefApp's log4j2.xml caps the "org.openmrs" logger family at WARN (see
		// openmrs-distro-referenceapplication, not ours to edit — CLAUDE.md rule 9), so an
		// INFO line here would silently vanish from openmrs.log. tugas 09 requires the build
		// duration to actually be visible in the log, so this one line is WARN, not spam.
		log.warn("Unified search index build finished in " + buildDurationMs + " ms ("
		        + dokumenBaru.size() + " documents, " + semuaForm.size() + " surface forms, 13 indices)");
	}

	private static List<String> teks(List<SurfaceForm> forms) {
		List<String> out = new ArrayList<String>(forms.size());
		for (SurfaceForm form : forms) {
			out.add(form.getTeks());
		}
		return out;
	}
}

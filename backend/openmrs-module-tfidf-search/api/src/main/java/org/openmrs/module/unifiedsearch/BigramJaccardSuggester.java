package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Query-suggestion ranking for the O3 navbar typeahead ONLY. This is NOT a REST-facing search
 * mode -- {@code EvalService.REST_MODES} still enforces b0/b1/e1/e3 as the only modes
 * {@code /unifiedsearch} accepts, and this class is never reached through that endpoint or
 * through EvalService. It is not part of the K1-K6 research pipeline and has no B0/B1/E1/E3
 * numbers of its own (CLAUDE.md rule 2/3 do not apply to it).
 * <p>
 * Scores each surface form by Jaccard similarity of its character bigrams against the query's
 * bigrams: {@code |A n B| / |A u B|}. Unlike b0 (OpenMrsHeuristic, exact word-prefix match), this
 * tolerates a mistyped letter and still needs only 2 characters to produce a gram (vs. K5's
 * NGRAM=4), so very short or slightly-off fragments still surface a suggestion.
 * <p>
 * Guard: a surface form is scored only if it shares AT LEAST TWO bigrams with the query, unless
 * the two are literally identical after normalisation. A single shared bigram is not enough
 * evidence once either side has more than one -- found live in tugas testing: the code "Fe"
 * (chemical symbol, one of konsep "Serum iron measurement"'s surface forms) scored a 0.5 Jaccard
 * tie with "Fever" against query "fev", because a 1-gram form has nowhere to go but 1.0/n. The
 * one-gram-of-one-gram case ("tb" query against a "TB" code) is still allowed: same-set is an
 * exact match, not a coincidence.
 */
public final class BigramJaccardSuggester {

	private static final int NGRAM = 2;

	private static final int MIN_IRISAN = 2;

	private BigramJaccardSuggester() {
	}

	public static List<RankedDocument> search(List<SurfaceForm> surfaceForms, String query) {
		Set<String> queryGrams = new TreeSet<String>(Tokenizer.charGrams(query, NGRAM));
		if (queryGrams.isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, RankedDocument> terbaikPerKunci = new TreeMap<String, RankedDocument>();
		for (SurfaceForm form : surfaceForms) {
			Set<String> formGrams = new TreeSet<String>(Tokenizer.charGrams(form.getTeks(), NGRAM));
			if (formGrams.isEmpty()) {
				continue;
			}
			Set<String> irisan = new TreeSet<String>(queryGrams);
			irisan.retainAll(formGrams);
			if (irisan.isEmpty()) {
				continue;
			}
			boolean cocokPersis = queryGrams.equals(formGrams);
			if (irisan.size() < MIN_IRISAN && !cocokPersis) {
				continue;
			}
			Set<String> gabungan = new TreeSet<String>(queryGrams);
			gabungan.addAll(formGrams);
			double score = (double) irisan.size() / (double) gabungan.size();
			String kunci = form.getDokumen().getKunci();
			RankedDocument sebelumnya = terbaikPerKunci.get(kunci);
			if (sebelumnya == null || score > sebelumnya.getSkor()) {
				terbaikPerKunci.put(kunci, new RankedDocument(form.getDokumen(), score));
			}
		}
		List<RankedDocument> hasil = new ArrayList<RankedDocument>(terbaikPerKunci.values());
		Collections.sort(hasil, KUNCI_COMPARATOR);
		return hasil;
	}

	/** -skor lalu kunci dokumen (CLAUDE.md aturan 1: tie-break majemuk, bukan skor saja). */
	static final Comparator<RankedDocument> KUNCI_COMPARATOR = new Comparator<RankedDocument>() {

		@Override
		public int compare(RankedDocument a, RankedDocument b) {
			int byScore = Double.compare(b.getSkor(), a.getSkor());
			return byScore != 0 ? byScore : a.getDokumen().getKunci().compareTo(b.getDokumen().getKunci());
		}
	};
}

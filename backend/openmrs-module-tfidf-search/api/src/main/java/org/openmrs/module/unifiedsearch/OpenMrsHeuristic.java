package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * B0 — the OpenMRS/OCL prefix heuristic, matching {@code heuristik_openmrs()}
 * in {@code riset/eksperimen2.py}.
 * <p>
 * Stage 1 drops a surface form unless every query word is a prefix of at least
 * one word on that form. Stage 2 scores only the survivors: +1000 exact
 * normalised match, +500 if the form is the title, +200 / +100 per query word
 * (whole-word vs prefix), then a short-name preference of {@code -0.6 * length}.
 * The published B0 numbers were produced with this function, not a fuzzier
 * variant — do not add typo tolerance here.
 */
public final class OpenMrsHeuristic {

	private OpenMrsHeuristic() {
	}

	public static List<RankedDocument> search(List<SurfaceForm> surfaceForms, String query) {
		List<String> queryWords = Tokenizer.words(query);
		if (queryWords.isEmpty()) {
			return Collections.emptyList();
		}
		String queryNorm = TextNormalizer.normalize(query);
		Map<String, RankedDocument> best = new TreeMap<String, RankedDocument>();
		for (SurfaceForm form : surfaceForms) {
			List<String> formWords = Tokenizer.words(form.getTeks());
			if (!passesPrefixFilter(queryWords, formWords)) {
				continue;
			}
			double score = scoreForm(form, queryWords, queryNorm, formWords);
			String kunci = form.getDokumen().getKunci();
			RankedDocument previous = best.get(kunci);
			if (previous == null || score > previous.getSkor()) {
				best.put(kunci, new RankedDocument(form.getDokumen(), score));
			}
		}
		List<RankedDocument> out = new ArrayList<RankedDocument>(best.values());
		Collections.sort(out, new Comparator<RankedDocument>() {

			@Override
			public int compare(RankedDocument a, RankedDocument b) {
				int byScore = Double.compare(b.getSkor(), a.getSkor());
				return byScore != 0 ? byScore : a.getDokumen().getKunci().compareTo(b.getDokumen().getKunci());
			}
		});
		return out;
	}

	private static boolean passesPrefixFilter(List<String> queryWords, List<String> formWords) {
		for (String queryWord : queryWords) {
			boolean hit = false;
			for (String formWord : formWords) {
				if (formWord.startsWith(queryWord)) {
					hit = true;
					break;
				}
			}
			if (!hit) {
				return false;
			}
		}
		return true;
	}

	private static double scoreForm(SurfaceForm form, List<String> queryWords, String queryNorm, List<String> formWords) {
		String formNorm = TextNormalizer.normalize(form.getTeks());
		double score = 0.0;
		if (formNorm.equals(queryNorm)) {
			score += 1000.0;
		}
		if (form.isJudul()) {
			score += 500.0;
		}
		for (String queryWord : queryWords) {
			score += formWords.contains(queryWord) ? 200.0 : 100.0;
		}
		score -= formNorm.length() * 0.6;
		return score;
	}
}

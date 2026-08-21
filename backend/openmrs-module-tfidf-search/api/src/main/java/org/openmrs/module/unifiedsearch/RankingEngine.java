package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wires the per-entity K5 rankings and the K6 fusion together into the research systems.
 * REST-facing modes are only b0, b1, e1, e3 (docs/algoritma.md sec. 7) — enforced by
 * {@code EvalService.REST_MODES}, not by this class. "e2" is also accepted here but is a
 * Java-internal test fixture only (see the e2 branch below); it was rejected by the research
 * (CLAUDE.md rule 3) and must never be reachable through the REST layer.
 */
public class RankingEngine {

	private final Map<String, FusionSearch> lokal;

	private final GlobalIndex global;

	private final double eps;

	private final int kRrf;

	public RankingEngine(Map<String, FusionSearch> lokal, GlobalIndex global, double eps, int kRrf) {
		this.lokal = new TreeMap<String, FusionSearch>(lokal);
		this.global = global;
		this.eps = eps;
		this.kRrf = kRrf;
	}

	public List<SearchHit> search(String mode, String query, double alpha) {
		if ("b0".equals(mode)) {
			return hitsFromPerEntity(b0PerEntity(query));
		}
		Map<String, List<RankedDocument>> perEntitas = rankedPerEntity(mode, query, alpha);
		if ("b1".equals(mode) || "e1".equals(mode)) {
			return hitsFromPerEntity(perEntitas);
		}
		if ("e2".equals(mode)) {
			// Not a REST mode — EvalService.REST_MODES rejects it before it ever reaches here.
			// Kept only because WeightedRrfDeterminismRunner / WeightedRrfSeparateProcessDeterminismTest
			// rely on E2's uniform per-entity weights to force the rank-1 ties that used to leak
			// hash-iteration order into results (tugas 07, CLAUDE.md rule 1). E2 itself was rejected
			// by the research (CLAUDE.md rule 3) and must never be exposed as a live component.
			return hitsFromWeightedRrf(perEntitas, bobotSeragam());
		}
		if ("e3".equals(mode)) {
			Map<String, Double> bobot = global.collectionWeights(query, alpha, eps);
			return hitsFromWeightedRrf(perEntitas, bobot);
		}
		throw new IllegalArgumentException("mode tidak dikenal: " + mode);
	}

	private Map<String, List<RankedDocument>> b0PerEntity(String query) {
		Map<String, List<RankedDocument>> perEntitas = new TreeMap<String, List<RankedDocument>>();
		for (Map.Entry<String, FusionSearch> entry : lokal.entrySet()) {
			perEntitas.put(entry.getKey(),
			    OpenMrsHeuristic.search(entry.getValue().getSurfaceForms(), query));
		}
		return perEntitas;
	}

	private Map<String, List<RankedDocument>> rankedPerEntity(String mode, String query, double alpha) {
		Map<String, List<RankedDocument>> perEntitas = new TreeMap<String, List<RankedDocument>>();
		for (Map.Entry<String, FusionSearch> entry : lokal.entrySet()) {
			if ("b1".equals(mode)) {
				perEntitas.put(entry.getKey(), entry.getValue().searchWordsOnly(query));
			} else {
				perEntitas.put(entry.getKey(), entry.getValue().search(query, alpha));
			}
		}
		return perEntitas;
	}

	private Map<String, Double> bobotSeragam() {
		Map<String, Double> bobot = new TreeMap<String, Double>();
		for (String entitas : lokal.keySet()) {
			bobot.put(entitas, Double.valueOf(1.0));
		}
		return bobot;
	}

	private List<SearchHit> hitsFromPerEntity(Map<String, List<RankedDocument>> perEntitas) {
		List<SearchHit> hits = new ArrayList<SearchHit>();
		for (Map.Entry<String, List<RankedDocument>> entry : new TreeMap<String, List<RankedDocument>>(
		        perEntitas).entrySet()) {
			List<RankedDocument> list = entry.getValue();
			for (int i = 0; i < list.size(); i++) {
				RankedDocument rd = list.get(i);
				hits.add(new SearchHit(rd.getDokumen(), rd.getSkor(), Double.valueOf(rd.getSkor()),
				        Integer.valueOf(i + 1), null));
			}
		}
		return sortHits(hits);
	}

	private List<SearchHit> hitsFromWeightedRrf(Map<String, List<RankedDocument>> perEntitas,
	        Map<String, Double> bobot) {
		List<SearchHit> hits = new ArrayList<SearchHit>();
		for (Map.Entry<String, List<RankedDocument>> entry : new TreeMap<String, List<RankedDocument>>(
		        perEntitas).entrySet()) {
			String entitas = entry.getKey();
			double w = bobot.get(entitas).doubleValue();
			List<RankedDocument> list = entry.getValue();
			for (int i = 0; i < list.size(); i++) {
				RankedDocument rd = list.get(i);
				int peringkat = i + 1;
				double nilai = w * (1.0 / (kRrf + peringkat));
				hits.add(new SearchHit(rd.getDokumen(), nilai, Double.valueOf(rd.getSkor()),
				        Integer.valueOf(peringkat), Double.valueOf(w)));
			}
		}
		return sortHits(hits);
	}

	private static List<SearchHit> sortHits(List<SearchHit> hits) {
		Collections.sort(hits, new Comparator<SearchHit>() {

			@Override
			public int compare(SearchHit a, SearchHit b) {
				int byScore = Double.compare(b.getSkor(), a.getSkor());
				return byScore != 0 ? byScore : a.getDokumen().getKunci().compareTo(b.getDokumen().getKunci());
			}
		});
		return hits;
	}

	/** Top-k document keys only — used by the eval runner. */
	public List<String> searchKeys(String mode, String query, double alpha, int topK) {
		List<SearchHit> hits = search(mode, query, alpha);
		List<String> keys = new ArrayList<String>();
		int n = Math.min(topK, hits.size());
		for (int i = 0; i < n; i++) {
			keys.add(hits.get(i).getDokumen().getKunci());
		}
		return keys;
	}
}

package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wires the per-entity K5 rankings and the K6 fusion together into the three
 * research systems that share this plumbing: E1 (K5 only, six lists simply
 * merged and re-sorted, no RRF), E2 (RRF polos — uniform weight 1.0 per
 * entity, kept only as a research comparison; measured worse than the B0
 * baseline, and that is the point, not a bug to hide), and E3 (Weighted RRF).
 * <p>
 * Mode routing for the REST endpoint is tugas 09. This class computes B0
 * (prefix heuristic), E1, E2, and E3.
 */
public class RankingEngine {
	
	private final Map<String, FusionSearch> lokal;
	
	private final GlobalIndex global;
	
	private final double alpha;
	
	private final double eps;
	
	private final int kRrf;
	
	public RankingEngine(Map<String, FusionSearch> lokal, GlobalIndex global, double alpha, double eps, int kRrf) {
		this.lokal = new TreeMap<String, FusionSearch>(lokal);
		this.global = global;
		this.alpha = alpha;
		this.eps = eps;
		this.kRrf = kRrf;
	}
	
	public List<RankedDocument> search(String mode, String query) {
		if ("b0".equals(mode)) {
			Map<String, List<RankedDocument>> perEntitas = new TreeMap<String, List<RankedDocument>>();
			for (Map.Entry<String, FusionSearch> entry : lokal.entrySet()) {
				perEntitas.put(entry.getKey(), OpenMrsHeuristic.search(entry.getValue().getSurfaceForms(), query));
			}
			return unionSorted(perEntitas);
		}
		Map<String, List<RankedDocument>> perEntitas = new TreeMap<String, List<RankedDocument>>();
		for (Map.Entry<String, FusionSearch> entry : lokal.entrySet()) {
			perEntitas.put(entry.getKey(), entry.getValue().search(query, alpha));
		}
		
		if ("e1".equals(mode)) {
			return unionSorted(perEntitas);
		}
		if ("e2".equals(mode)) {
			return WeightedRrf.fuse(perEntitas, bobotSeragam(), kRrf);
		}
		if ("e3".equals(mode)) {
			Map<String, Double> bobot = global.collectionWeights(query, alpha, eps);
			return WeightedRrf.fuse(perEntitas, bobot, kRrf);
		}
		throw new IllegalArgumentException("mode tidak dikenal: " + mode);
	}
	
	private Map<String, Double> bobotSeragam() {
		Map<String, Double> bobot = new TreeMap<String, Double>();
		for (String entitas : lokal.keySet()) {
			bobot.put(entitas, Double.valueOf(1.0));
		}
		return bobot;
	}
	
	private static List<RankedDocument> unionSorted(Map<String, List<RankedDocument>> perEntitas) {
		List<RankedDocument> hasil = new ArrayList<RankedDocument>();
		for (List<RankedDocument> list : perEntitas.values()) {
			hasil.addAll(list);
		}
		Collections.sort(hasil, new Comparator<RankedDocument>() {
			
			@Override
			public int compare(RankedDocument a, RankedDocument b) {
				int byScore = Double.compare(b.getSkor(), a.getSkor());
				return byScore != 0 ? byScore : a.getDokumen().getKunci().compareTo(b.getDokumen().getKunci());
			}
		});
		return hasil;
	}
}

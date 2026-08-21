package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * K6 — merges six independently-ranked lists (one per entity) into one
 * (docs/algoritma.md sec. 5):
 *
 * <pre>
 * untuk tiap dokumen d pada entitas e dengan peringkat r (mulai dari 1):
 *     nilai(d) = bobot[e] * 1 / (K_RRF + r)
 * urutkan menurun dengan kunci (-nilai, kunci_unik)
 * </pre>
 *
 * This component is architectural, not a quality improvement: measured effect
 * was +0.007 nDCG, p=0.207 — not significant. It exists to turn six separate
 * per-entity rankings into the single list a unified search box needs, and to
 * fix RRF's degenerate behavior on disjoint collections (every table's rank-1
 * entry would otherwise tie at the same raw RRF value regardless of how good
 * a match it actually is — see "Cara A" in docs/proposal.html).
 * <p>
 * RRF produces many exactly-equal scores by construction (each entity
 * contributes one identical top value), so the final sort key MUST be
 * {@code (-value, kunci)}, never value alone — see CLAUDE.md rule 1 and the
 * "TITIK RAWAN" note in tugas/07-weighted-rrf.md.
 */
public final class WeightedRrf {
	
	private WeightedRrf() {
	}
	
	/**
	 * @param perEntityResults each entity's own locally-ranked list (K5 output),
	 *            already sorted by (-skor, kunci); rank r is this list's position
	 * @param weights per-entity weight, e.g. from {@link GlobalIndex#collectionWeights}
	 *            (K3) or all 1.0 for RRF polos (E2)
	 * @param kRrf the RRF rank-damping constant
	 */
	public static List<RankedDocument> fuse(Map<String, List<RankedDocument>> perEntityResults,
	        Map<String, Double> weights, int kRrf) {
		List<RankedDocument> hasil = new ArrayList<RankedDocument>();
		// TreeMap regardless of the caller's map type: never iterate a hash-ordered
		// collection here (CLAUDE.md rule 1).
		for (Map.Entry<String, List<RankedDocument>> entry : new TreeMap<String, List<RankedDocument>>(
		        perEntityResults).entrySet()) {
			String entitas = entry.getKey();
			double bobot = weights.get(entitas).doubleValue();
			List<RankedDocument> urut = entry.getValue();
			for (int i = 0; i < urut.size(); i++) {
				int peringkat = i + 1;
				double nilai = bobot * (1.0 / (kRrf + peringkat));
				hasil.add(new RankedDocument(urut.get(i).getDokumen(), nilai));
			}
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

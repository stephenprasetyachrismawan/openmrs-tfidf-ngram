package org.openmrs.module.unifiedsearch;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openmrs.module.unifiedsearch.source.DocumentRepository;

/**
 * The extra index beyond the per-entity ones (docs/algoritma.md sec. 3): word
 * + n-gram TF-IDF built over every surface form of all entities combined
 * (DocumentRepository.ENTITAS), used only to compute per-entity collection
 * weights for K6. It is never used to rank results directly — ranking within
 * an entity comes from that entity's own local index (see docs/algoritma.md
 * sec. 5, step 1).
 */
public class GlobalIndex {
	
	private final FusionSearch fusi;
	
	public GlobalIndex(TfIdfIndex indeksKata, TfIdfIndex indeksKepingan, List<SurfaceForm> surfaceForms) {
		this.fusi = new FusionSearch(indeksKata, indeksKepingan, surfaceForms);
	}
	
	/**
	 * K6 step 1+2: for each entity, the highest K5 score any of its documents
	 * reaches in the global index, turned into a weight with an EPS floor so an
	 * entity that scored 0 is not zeroed out entirely (docs/algoritma.md sec. 5).
	 * Iterates entities in the fixed order from {@link DocumentRepository#ENTITAS},
	 * never a hash-ordered collection.
	 */
	public Map<String, Double> collectionWeights(String query, double alpha, double eps) {
		Map<String, Double> g = new TreeMap<String, Double>();
		for (String entitas : DocumentRepository.ENTITAS) {
			g.put(entitas, Double.valueOf(0.0));
		}
		for (RankedDocument r : fusi.search(query, alpha)) {
			String entitas = r.getDokumen().getEntitas();
			if (r.getSkor() > g.get(entitas).doubleValue()) {
				g.put(entitas, Double.valueOf(r.getSkor()));
			}
		}
		
		double total = 0.0;
		for (double v : g.values()) {
			total += v;
		}
		
		Map<String, Double> bobot = new TreeMap<String, Double>();
		if (total <= 0.0) {
			for (String entitas : g.keySet()) {
				bobot.put(entitas, Double.valueOf(1.0));
			}
			return bobot;
		}
		for (Map.Entry<String, Double> entry : g.entrySet()) {
			bobot.put(entry.getKey(), Double.valueOf(eps + (1.0 - eps) * entry.getValue().doubleValue() / total));
		}
		return bobot;
	}
}

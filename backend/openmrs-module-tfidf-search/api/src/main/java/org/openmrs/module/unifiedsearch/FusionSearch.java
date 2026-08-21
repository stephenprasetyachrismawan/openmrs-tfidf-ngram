package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * K5 — combines the word index and the character n-gram index into one score
 * per document (docs/algoritma.md sec. 4):
 *
 * <pre>
 * skor(surface form) = ALPHA * cosine_kata + (1 - ALPHA) * cosine_kepingan
 * skor(dokumen)       = max atas surface form-nya, diambil SETELAH digabung
 * </pre>
 *
 * {@code alpha} is a caller-supplied parameter, never a constant baked into
 * this class — see the "CATATAN PARAMETER" note in tugas/05-fusi-k5.md. Its
 * final value is decided in tugas 06 using 100 dev queries, not here.
 * <p>
 * Both indices MUST have been built from surface forms in the exact same
 * order — this class walks them by position and has no way to detect a
 * mismatch.
 */
public class FusionSearch {
	
	/** docs/algoritma.md "Parameter — nilai resmi": documents at or below this are dropped. */
	public static final double SCORE_THRESHOLD = 0.07;
	
	private final TfIdfIndex indeksKata;
	
	private final TfIdfIndex indeksKepingan;
	
	private final List<SurfaceForm> surfaceForms;
	
	public FusionSearch(TfIdfIndex indeksKata, TfIdfIndex indeksKepingan, List<SurfaceForm> surfaceForms) {
		this.indeksKata = indeksKata;
		this.indeksKepingan = indeksKepingan;
		this.surfaceForms = surfaceForms;
	}
	
	public List<RankedDocument> search(String query, double alpha) {
		double[] skorKata = indeksKata.search(query);
		double[] skorKepingan = indeksKepingan.search(query);
		
		Map<String, VirtualDocument> dokumenByKunci = new LinkedHashMap<String, VirtualDocument>();
		Map<String, Double> skorTerbaikByKunci = new LinkedHashMap<String, Double>();
		for (int i = 0; i < surfaceForms.size(); i++) {
			double gabungan = alpha * skorKata[i] + (1.0 - alpha) * skorKepingan[i];
			VirtualDocument dokumen = surfaceForms.get(i).getDokumen();
			String kunci = dokumen.getKunci();
			Double sebelumnya = skorTerbaikByKunci.get(kunci);
			if (sebelumnya == null || gabungan > sebelumnya.doubleValue()) {
				skorTerbaikByKunci.put(kunci, Double.valueOf(gabungan));
				dokumenByKunci.put(kunci, dokumen);
			}
		}
		
		List<RankedDocument> hasil = new ArrayList<RankedDocument>();
		for (Map.Entry<String, Double> entry : skorTerbaikByKunci.entrySet()) {
			if (entry.getValue().doubleValue() > SCORE_THRESHOLD) {
				hasil.add(new RankedDocument(dokumenByKunci.get(entry.getKey()), entry.getValue().doubleValue()));
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

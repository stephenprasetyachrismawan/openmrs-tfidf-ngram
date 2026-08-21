package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * K5 — combines the word index and the character n-gram index into one score
 * per document (docs/algoritma.md sec. 4, matching {@code fusi1()} in
 * riset/eksperimen2.py):
 *
 * <pre>
 * cos_kata(dokumen)     = max atas surface form-nya, jalur kata SENDIRI
 * cos_kepingan(dokumen) = max atas surface form-nya, jalur kepingan SENDIRI
 * skor(dokumen)         = ALPHA * cos_kata(dokumen) + (1 - ALPHA) * cos_kepingan(dokumen)
 * </pre>
 *
 * The maximum is taken per path first, THEN the two paths are combined — the
 * reverse (combine per surface form, then take the max) was tried first and
 * measured to disagree with the research pipeline on 17.8% of queries' top-10
 * (see docs/keputusan.md "Dua penyimpangan K5"). This order is what every
 * published number in the study was produced with.
 * <p>
 * {@code alpha} is a caller-supplied parameter, never a constant baked into
 * this class — see the "CATATAN PARAMETER" note in tugas/05-fusi-k5.md. Its
 * final value is decided in tugas 06 using 100 dev queries, not here.
 * <p>
 * Both indices MUST have been built from surface forms in the exact same
 * order — this class walks them by position and has no way to detect a
 * mismatch.
 */
public class FusionSearch {

	/**
	 * Matches the 1e-6 threshold literally present three times in
	 * riset/eksperimen2.py. 0.07 was a demo-mockup value mistakenly copied into
	 * CLAUDE.md's parameter table — see docs/keputusan.md "Dua penyimpangan K5".
	 */
	public static final double SCORE_THRESHOLD = 1e-6;

	private final TfIdfIndex indeksKata;

	private final TfIdfIndex indeksKepingan;

	private final List<SurfaceForm> surfaceForms;

	public FusionSearch(TfIdfIndex indeksKata, TfIdfIndex indeksKepingan, List<SurfaceForm> surfaceForms) {
		this.indeksKata = indeksKata;
		this.indeksKepingan = indeksKepingan;
		this.surfaceForms = surfaceForms;
	}

	public List<SurfaceForm> getSurfaceForms() {
		return surfaceForms;
	}

	/** B1 — word TF-IDF only (docs/algoritma.md mode {@code b1}). */
	public List<RankedDocument> searchWordsOnly(String query) {
		return rankFromWordScores(indeksKata.search(query));
	}

	public List<RankedDocument> search(String query, double alpha) {
		double[] skorKata = indeksKata.search(query);
		double[] skorKepingan = indeksKepingan.search(query);

		Map<String, VirtualDocument> dokumenByKunci = new LinkedHashMap<String, VirtualDocument>();
		Map<String, Double> maksKataByKunci = new LinkedHashMap<String, Double>();
		Map<String, Double> maksKepinganByKunci = new LinkedHashMap<String, Double>();
		for (int i = 0; i < surfaceForms.size(); i++) {
			VirtualDocument dokumen = surfaceForms.get(i).getDokumen();
			String kunci = dokumen.getKunci();
			dokumenByKunci.put(kunci, dokumen);

			Double kataSebelumnya = maksKataByKunci.get(kunci);
			if (kataSebelumnya == null || skorKata[i] > kataSebelumnya.doubleValue()) {
				maksKataByKunci.put(kunci, Double.valueOf(skorKata[i]));
			}
			Double kepinganSebelumnya = maksKepinganByKunci.get(kunci);
			if (kepinganSebelumnya == null || skorKepingan[i] > kepinganSebelumnya.doubleValue()) {
				maksKepinganByKunci.put(kunci, Double.valueOf(skorKepingan[i]));
			}
		}

		List<RankedDocument> hasil = new ArrayList<RankedDocument>();
		for (Map.Entry<String, VirtualDocument> entry : dokumenByKunci.entrySet()) {
			String kunci = entry.getKey();
			double gabungan = alpha * maksKataByKunci.get(kunci).doubleValue() + (1.0 - alpha) * maksKepinganByKunci
			        .get(kunci).doubleValue();
			if (gabungan > SCORE_THRESHOLD) {
				hasil.add(new RankedDocument(entry.getValue(), gabungan));
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

	private List<RankedDocument> rankFromWordScores(double[] skorKata) {
		Map<String, VirtualDocument> dokumenByKunci = new LinkedHashMap<String, VirtualDocument>();
		Map<String, Double> maksByKunci = new LinkedHashMap<String, Double>();
		for (int i = 0; i < surfaceForms.size(); i++) {
			VirtualDocument dokumen = surfaceForms.get(i).getDokumen();
			String kunci = dokumen.getKunci();
			dokumenByKunci.put(kunci, dokumen);
			Double sebelumnya = maksByKunci.get(kunci);
			if (sebelumnya == null || skorKata[i] > sebelumnya.doubleValue()) {
				maksByKunci.put(kunci, Double.valueOf(skorKata[i]));
			}
		}
		List<RankedDocument> hasil = new ArrayList<RankedDocument>();
		for (Map.Entry<String, VirtualDocument> entry : dokumenByKunci.entrySet()) {
			double skor = maksByKunci.get(entry.getKey()).doubleValue();
			if (skor > SCORE_THRESHOLD) {
				hasil.add(new RankedDocument(entry.getValue(), skor));
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

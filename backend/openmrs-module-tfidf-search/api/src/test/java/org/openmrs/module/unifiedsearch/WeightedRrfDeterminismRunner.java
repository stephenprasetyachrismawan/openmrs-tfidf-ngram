package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * Standalone entry point used by {@link WeightedRrfSeparateProcessDeterminismTest}
 * to run the exact same RRF-polos (E2) computation in a brand-new JVM process.
 * E2 is used deliberately: uniform weights guarantee every entity's rank-1
 * document ties at the identical raw RRF value (the scenario "Cara A" in
 * docs/proposal.html describes), which is exactly the condition under which
 * hash-iteration order used to leak into the result. Prints one "kunci=skor"
 * line per ranked document, in final order, to stdout.
 */
public final class WeightedRrfDeterminismRunner {
	
	private WeightedRrfDeterminismRunner() {
	}
	
	public static void main(String[] args) {
		for (RankedDocument r : hasil()) {
			System.out.println(r.getDokumen().getKunci() + "=" + r.getSkor());
		}
	}
	
	/** Shared by the in-process (20x) and separate-process determinism tests. */
	static List<RankedDocument> hasil() {
		SurfaceFormExtractor extractor = new SurfaceFormExtractor();
		
		// Enam dokumen, satu per entitas, semuanya cocok query "diabete" di
		// peringkat 1 di entitasnya sendiri -- memaksa dasi RRF persis sama untuk
		// keenamnya, sesuai skenario yang dulu bocor non-determinisme.
		java.util.Map<String, VirtualDocument> dokPerEntitas = new TreeMap<String, VirtualDocument>();
		dokPerEntitas.put("konsep", new VirtualDocument("konsep", 1, "diabetes mellitus", new ArrayList<String>(),
		    new ArrayList<String>(), "", null));
		dokPerEntitas.put("obat", new VirtualDocument("obat", 1, "diabetes medication", new ArrayList<String>(),
		    new ArrayList<String>(), "", null));
		dokPerEntitas.put("pasien", new VirtualDocument("pasien", 1, "diabetes patient name", new ArrayList<String>(),
		    new ArrayList<String>(), "", null));
		dokPerEntitas.put("form", new VirtualDocument("form", 1, "diabetes form", new ArrayList<String>(),
		    new ArrayList<String>(), "", null));
		dokPerEntitas.put("lokasi", new VirtualDocument("lokasi", 1, "diabetes clinic", new ArrayList<String>(),
		    new ArrayList<String>(), "", null));
		dokPerEntitas.put("provider", new VirtualDocument("provider", 1, "diabetes specialist", new ArrayList<String>(),
		    new ArrayList<String>(), "", null));
		
		java.util.Map<String, FusionSearch> lokal = new TreeMap<String, FusionSearch>();
		List<SurfaceForm> semuaForm = new ArrayList<SurfaceForm>();
		for (java.util.Map.Entry<String, VirtualDocument> entry : dokPerEntitas.entrySet()) {
			List<SurfaceForm> forms = extractor.extract(entry.getValue());
			semuaForm.addAll(forms);
			
			TfIdfIndex kata = new TfIdfIndex(Tokenizer::words);
			kata.build(teks(forms));
			TfIdfIndex kepingan = new TfIdfIndex(s -> Tokenizer.charGrams(s, 4));
			kepingan.build(teks(forms));
			lokal.put(entry.getKey(), new FusionSearch(kata, kepingan, forms));
		}
		
		TfIdfIndex globalKata = new TfIdfIndex(Tokenizer::words);
		globalKata.build(teks(semuaForm));
		TfIdfIndex globalKepingan = new TfIdfIndex(s -> Tokenizer.charGrams(s, 4));
		globalKepingan.build(teks(semuaForm));
		GlobalIndex global = new GlobalIndex(globalKata, globalKepingan, semuaForm);
		
		RankingEngine engine = new RankingEngine(lokal, global, 0.05, 20);
		List<SearchHit> hits = engine.search("e2", "diabete", 0.20);
		List<RankedDocument> out = new ArrayList<RankedDocument>();
		for (SearchHit hit : hits) {
			out.add(new RankedDocument(hit.getDokumen(), hit.getSkor()));
		}
		return out;
	}
	
	private static List<String> teks(List<SurfaceForm> forms) {
		List<String> out = new ArrayList<String>();
		for (SurfaceForm f : forms) {
			out.add(f.getTeks());
		}
		return out;
	}
}

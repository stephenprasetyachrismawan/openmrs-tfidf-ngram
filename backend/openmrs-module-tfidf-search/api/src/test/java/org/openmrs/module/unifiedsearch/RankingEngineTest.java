package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;

/**
 * K6 end to end: E1 (K5 only, no RRF) vs E3 (Weighted RRF).
 */
public class RankingEngineTest {

	private static final double ALPHA = 0.20;

	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();

	private RankingEngine engine;

	@Before
	public void bangunEngine() {
		VirtualDocument dm1 = new VirtualDocument("konsep", 1, "diabetes mellitus type 1", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument dm2 = new VirtualDocument("konsep", 2, "diabetes mellitus type 2", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument insipidus = new VirtualDocument("konsep", 3, "diabetes insipidus", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument klinik = new VirtualDocument("lokasi", 1, "diabetes clinic", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument form = new VirtualDocument("form", 1, "diabetes follow-up form", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument edema = new VirtualDocument("konsep", 4, "pulmonary edema", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);

		java.util.Map<String, List<VirtualDocument>> perEntitas = new TreeMap<String, List<VirtualDocument>>();
		perEntitas.put("konsep", Arrays.asList(dm1, dm2, insipidus, edema));
		perEntitas.put("lokasi", Arrays.asList(klinik));
		perEntitas.put("form", Arrays.asList(form));

		java.util.Map<String, FusionSearch> lokal = new TreeMap<String, FusionSearch>();
		List<SurfaceForm> semuaForm = new ArrayList<SurfaceForm>();
		for (java.util.Map.Entry<String, List<VirtualDocument>> entry : perEntitas.entrySet()) {
			List<SurfaceForm> forms = extractor.extractAll(entry.getValue());
			semuaForm.addAll(forms);
			lokal.put(entry.getKey(), bangunFusi(forms));
		}
		GlobalIndex global = new GlobalIndex(bangunKata(semuaForm), bangunKepingan(semuaForm), semuaForm);

		engine = new RankingEngine(lokal, global, 0.05, 20);
	}

	private static TfIdfIndex bangunKata(List<SurfaceForm> forms) {
		TfIdfIndex idx = new TfIdfIndex(Tokenizer::words);
		idx.build(teks(forms));
		return idx;
	}

	private static TfIdfIndex bangunKepingan(List<SurfaceForm> forms) {
		TfIdfIndex idx = new TfIdfIndex(s -> Tokenizer.charGrams(s, 4));
		idx.build(teks(forms));
		return idx;
	}

	private static FusionSearch bangunFusi(List<SurfaceForm> forms) {
		return new FusionSearch(bangunKata(forms), bangunKepingan(forms), forms);
	}

	private static List<String> teks(List<SurfaceForm> forms) {
		List<String> out = new ArrayList<String>();
		for (SurfaceForm f : forms) {
			out.add(f.getTeks());
		}
		return out;
	}

	private static List<String> kunciSaja(List<SearchHit> hasil) {
		List<String> out = new ArrayList<String>();
		for (SearchHit r : hasil) {
			out.add(r.getDokumen().getKunci());
		}
		return out;
	}

	@Test
	public void e1DanE3KeduanyaBerjalanDanMenghasilkanUrutanBerbeda() {
		List<SearchHit> e1 = engine.search("e1", "diabete", ALPHA);
		List<SearchHit> e3 = engine.search("e3", "diabete", ALPHA);

		assertFalse("e1 harus menghasilkan sesuatu", e1.isEmpty());
		assertFalse("e3 harus menghasilkan sesuatu", e3.isEmpty());
		assertFalse("e1 dan e3 harus menghasilkan urutan berbeda untuk korpus ini", kunciSaja(e1).equals(kunciSaja(e3)));
	}

	@Test
	public void e2JugaBerjalan() {
		List<SearchHit> e2 = engine.search("e2", "diabete", ALPHA);
		assertFalse(e2.isEmpty());
	}

	@Test
	public void panggilanBerulangDalamProsesYangSamaMemberiUrutanIdentik() {
		List<String> pertama = kunciSaja(engine.search("e3", "diabete", ALPHA));
		for (int i = 0; i < 50; i++) {
			assertEquals(pertama, kunciSaja(engine.search("e3", "diabete", ALPHA)));
		}
	}

	@Test
	public void modeB0DiabeteMelitusKosongPulmEdemAda() {
		assertTrue(engine.search("b0", "diabete melitus", ALPHA).isEmpty());
		List<SearchHit> pulm = engine.search("b0", "pulm edem", ALPHA);
		assertFalse(pulm.isEmpty());
		assertEquals("konsep:4", pulm.get(0).getDokumen().getKunci());
		List<SearchHit> exact = engine.search("b0", "diabetes mellitus", ALPHA);
		assertFalse(exact.isEmpty());
		assertTrue(exact.get(0).getDokumen().getKunci().startsWith("konsep:"));
	}
}

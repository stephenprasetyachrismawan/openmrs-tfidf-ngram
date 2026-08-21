package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * K6 step 1+2 (docs/algoritma.md sec. 5): the EPS floor on collection weights.
 */
public class GlobalIndexTest {
	
	private static final double EPS = 0.05;
	
	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();
	
	private GlobalIndex global;
	
	@Before
	public void bangunIndeksGlobal() {
		VirtualDocument konsep = new VirtualDocument("konsep", 1, "diabetes mellitus", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument obat = new VirtualDocument("obat", 1, "completely unrelated drug name", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		
		List<SurfaceForm> forms = new ArrayList<SurfaceForm>();
		forms.addAll(extractor.extract(konsep));
		forms.addAll(extractor.extract(obat));
		
		List<String> teks = new ArrayList<String>();
		for (SurfaceForm f : forms) {
			teks.add(f.getTeks());
		}
		TfIdfIndex kata = new TfIdfIndex(Tokenizer::words);
		kata.build(teks);
		TfIdfIndex kepingan = new TfIdfIndex(s -> Tokenizer.charGrams(s, 4));
		kepingan.build(teks);
		
		global = new GlobalIndex(kata, kepingan, forms);
	}
	
	@Test
	public void entitasTanpaKecocokanTetapDapatLantaiEpsBukanNol() {
		Map<String, Double> bobot = global.collectionWeights("diabetes mellitus", 0.20, EPS);
		
		// "pasien", "form", "lokasi", "provider" tidak punya dokumen di korpus ini
		// sama sekali -- skor global-nya pasti 0 -- tapi harus tetap dapat lantai.
		assertEquals(Double.valueOf(EPS), bobot.get("pasien"));
		assertEquals(Double.valueOf(EPS), bobot.get("lokasi"));
		assertEquals(Double.valueOf(EPS), bobot.get("form"));
		assertEquals(Double.valueOf(EPS), bobot.get("provider"));
	}
	
	@Test
	public void entitasYangCocokDapatBobotLebihBesarDariLantai() {
		Map<String, Double> bobot = global.collectionWeights("diabetes mellitus", 0.20, EPS);
		
		assertTrue(bobot.get("konsep").doubleValue() > EPS);
	}
	
	@Test
	public void bobotSeluruhEntitasBerjumlahMasukAkal() {
		// bukan klaim presisi tinggi -- cuma sanity check tanda dan urutan besaran.
		Map<String, Double> bobot = global.collectionWeights("diabetes mellitus", 0.20, EPS);
		
		for (double v : bobot.values()) {
			assertTrue(v >= EPS - 1e-9);
			assertTrue(v <= 1.0 + 1e-9);
		}
	}
	
	@Test
	public void queryTanpaKecocokanSamaSekaliMemberiBobotSatuUntukSemua() {
		Map<String, Double> bobot = global.collectionWeights("xyzxyzxyz tidak ada", 0.20, EPS);
		
		for (String entitas : bobot.keySet()) {
			assertEquals(Double.valueOf(1.0), bobot.get(entitas));
		}
	}
}

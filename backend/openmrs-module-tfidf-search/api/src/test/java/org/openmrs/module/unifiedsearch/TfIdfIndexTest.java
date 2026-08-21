package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Corpus: surface forms "apple" (d0) and "banana apple" (d1). N=2, so
 * idf(apple) = ln(2/2)+1 = 1.0 and idf(banana) = ln(2/1)+1 ~= 1.693147. Every
 * expected value below was computed by hand from the ltc formula in
 * docs/algoritma.md sec. 2, not read off the implementation.
 */
public class TfIdfIndexTest {
	
	private static final double EPS = 1e-6;
	
	private TfIdfIndex index;
	
	@Before
	public void bangunIndeks() {
		index = new TfIdfIndex(Tokenizer::words);
		index.build(Arrays.asList("apple", "banana apple"));
	}
	
	@Test
	public void queryIdentikDenganSurfaceFormMemberiSkorMendekatiSatu() {
		double[] skor = index.search("apple");
		assertEquals(1.0, skor[0], EPS);
	}
	
	@Test
	public void queryIdentikDenganSurfaceFormMultiKataMemberiSkorMendekatiSatu() {
		double[] skor = index.search("banana apple");
		assertEquals(1.0, skor[1], EPS);
	}
	
	@Test
	public void skorDihitungTanganUntukKorpusKecil() {
		double[] skorApple = index.search("apple");
		assertEquals(1.0, skorApple[0], EPS);
		assertEquals(0.5085423203783267, skorApple[1], EPS);
		
		double[] skorBanana = index.search("banana");
		assertEquals(0.0, skorBanana[0], EPS);
		assertEquals(0.8610369959439764, skorBanana[1], EPS);
	}
	
	@Test
	public void kataYangTidakAdaDiKosakataMemberiSkorNolTanpaGalat() {
		double[] skor = index.search("grapefruit");
		
		assertArrayEquals(new double[] { 0.0, 0.0 }, skor, EPS);
	}
	
	@Test
	public void queryKosongMemberiSkorNolTanpaGalat() {
		double[] skor = index.search("");
		
		assertArrayEquals(new double[] { 0.0, 0.0 }, skor, EPS);
	}
	
	@Test
	public void panggilanSearchBerulangMemberiUrutanIdentik() {
		List<String> korpus = Arrays.asList("diabetes mellitus type 2", "type 2 diabetes", "hypertension",
		    "pulmonary edema", "acute kidney injury");
		TfIdfIndex idx = new TfIdfIndex(Tokenizer::words);
		idx.build(korpus);
		
		double[] pertama = idx.search("type 2 diabetes");
		for (int i = 0; i < 20; i++) {
			assertArrayEquals(pertama, idx.search("type 2 diabetes"), 0.0);
		}
	}
	
	@Test
	public void enamIndeksKataBisaDibangunPerEntitas() {
		String[] entitas = { "konsep", "obat", "pasien", "form", "lokasi", "provider" };
		int dibangun = 0;
		for (String e : entitas) {
			TfIdfIndex perEntitas = new TfIdfIndex(Tokenizer::words);
			perEntitas.build(Arrays.asList(e + " contoh judul"));
			assertTrue(perEntitas.vocabularySize() > 0);
			dibangun++;
		}
		assertEquals(6, dibangun);
	}
}

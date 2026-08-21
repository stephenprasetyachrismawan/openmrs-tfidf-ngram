package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.junit.Test;

/**
 * K6 fuse() (docs/algoritma.md sec. 5): {@code nilai = bobot[e] * 1/(K_RRF + r)},
 * r starting at 1. Every expected value here is computed by hand, not read off
 * the implementation.
 */
public class WeightedRrfTest {
	
	private static final double EPS = 1e-9;
	
	private static VirtualDocument dok(String entitas, int id) {
		return new VirtualDocument(entitas, id, entitas + ":" + id, new ArrayList<String>(), new ArrayList<String>(),
		    "", null);
	}
	
	@Test
	public void nilaiRrfDihitungTanganDenganPeringkatMulaiSatu() {
		// entitas "a": bobot 0.5, dua dokumen berperingkat 1 dan 2
		// entitas "b": bobot 1.0, satu dokumen berperingkat 1
		VirtualDocument a1 = dok("a", 1);
		VirtualDocument a2 = dok("a", 2);
		VirtualDocument b1 = dok("b", 1);
		
		java.util.Map<String, List<RankedDocument>> per = new TreeMap<String, List<RankedDocument>>();
		per.put("a", Arrays.asList(new RankedDocument(a1, 0.9), new RankedDocument(a2, 0.3)));
		per.put("b", Arrays.asList(new RankedDocument(b1, 0.7)));
		
		java.util.Map<String, Double> bobot = new TreeMap<String, Double>();
		bobot.put("a", 0.5);
		bobot.put("b", 1.0);
		
		List<RankedDocument> hasil = WeightedRrf.fuse(per, bobot, 20);
		
		// a1: peringkat 1 -> 0.5 * 1/(20+1) = 0.5/21 = 0.023809523809523808
		// b1: peringkat 1 -> 1.0 * 1/(20+1) = 1/21  = 0.047619047619047616
		// a2: peringkat 2 -> 0.5 * 1/(20+2) = 0.5/22 = 0.022727272727272728
		assertEquals(3, hasil.size());
		assertEquals("b:1", hasil.get(0).getDokumen().getKunci());
		assertEquals(1.0 / 21.0, hasil.get(0).getSkor(), EPS);
		assertEquals("a:1", hasil.get(1).getDokumen().getKunci());
		assertEquals(0.5 / 21.0, hasil.get(1).getSkor(), EPS);
		assertEquals("a:2", hasil.get(2).getDokumen().getKunci());
		assertEquals(0.5 / 22.0, hasil.get(2).getSkor(), EPS);
	}
	
	@Test
	public void skorPersisSamaDipecahDenganKunci() {
		// Skenario "Cara A" di proposal.html: bobot seragam 1.0, tiap entitas
		// menyumbang satu dokumen peringkat 1 -> nilai RRF-nya PERSIS sama.
		// Urutan akhirnya wajib ditentukan kunci_unik, bukan urutan penyisipan.
		VirtualDocument z = dok("z", 1);
		VirtualDocument m = dok("m", 1);
		VirtualDocument a = dok("a", 1);
		
		java.util.Map<String, List<RankedDocument>> per = new TreeMap<String, List<RankedDocument>>();
		per.put("z", Arrays.asList(new RankedDocument(z, 0.1)));
		per.put("m", Arrays.asList(new RankedDocument(m, 0.9)));
		per.put("a", Arrays.asList(new RankedDocument(a, 0.5)));
		
		java.util.Map<String, Double> bobotSeragam = new TreeMap<String, Double>();
		bobotSeragam.put("z", 1.0);
		bobotSeragam.put("m", 1.0);
		bobotSeragam.put("a", 1.0);
		
		List<RankedDocument> hasil = WeightedRrf.fuse(per, bobotSeragam, 20);
		
		for (RankedDocument r : hasil) {
			assertEquals(1.0 / 21.0, r.getSkor(), EPS);
		}
		// kunci_unik: "a:1" < "m:1" < "z:1" secara leksikografis
		assertEquals(Arrays.asList("a:1", "m:1", "z:1"), kunciSaja(hasil));
	}
	
	@Test
	public void panggilanBerulangMemberiUrutanIdentik() {
		VirtualDocument z = dok("z", 1);
		VirtualDocument m = dok("m", 1);
		java.util.Map<String, List<RankedDocument>> per = new TreeMap<String, List<RankedDocument>>();
		per.put("z", Arrays.asList(new RankedDocument(z, 0.5)));
		per.put("m", Arrays.asList(new RankedDocument(m, 0.5)));
		java.util.Map<String, Double> bobot = new TreeMap<String, Double>();
		bobot.put("z", 1.0);
		bobot.put("m", 1.0);
		
		List<String> pertama = kunciSaja(WeightedRrf.fuse(per, bobot, 20));
		for (int i = 0; i < 20; i++) {
			assertEquals(pertama, kunciSaja(WeightedRrf.fuse(per, bobot, 20)));
		}
	}
	
	private static List<String> kunciSaja(List<RankedDocument> hasil) {
		List<String> out = new ArrayList<String>();
		for (RankedDocument r : hasil) {
			out.add(r.getDokumen().getKunci());
		}
		return out;
	}
}

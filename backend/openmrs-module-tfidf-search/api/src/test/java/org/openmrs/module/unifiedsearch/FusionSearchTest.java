package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * K5 fusion (docs/algoritma.md sec. 4). ALPHA is always passed in by the
 * caller here, never a constant on {@link FusionSearch} — see the "CATATAN
 * PARAMETER" note in tugas/05-fusi-k5.md. None of these tests tune it; 0.5 is
 * used only because it is the midpoint, not because it is a candidate value.
 */
public class FusionSearchTest {
	
	private static final double EPS = 1e-9;
	
	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();
	
	private List<SurfaceForm> forms;
	
	private TfIdfIndex indeksKata;
	
	private TfIdfIndex indeksKepingan;
	
	@Before
	public void bangunIndeks() {
		// Documen 1 (kunci "konsep:1") sengaja dibuat punya dua surface form yang
		// unggul di jalur berbeda: judul cocok kata dengan query tapi jauh secara
		// karakter dari kata keduanya; alias adalah salah ketik kedua kata query,
		// jadi tidak ada kecocokan kata sama sekali tapi kepingan karakternya mirip.
		VirtualDocument dok1 = new VirtualDocument("konsep", 1, "diabetes hypertension",
		    Arrays.asList("diabetez melitus"), Collections.<String> emptyList(), "", null);
		VirtualDocument dok2 = new VirtualDocument("konsep", 2, "completely unrelated term", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		
		forms = new ArrayList<SurfaceForm>();
		forms.addAll(extractor.extract(dok1));
		forms.addAll(extractor.extract(dok2));
		
		List<String> teks = new ArrayList<String>();
		for (SurfaceForm f : forms) {
			teks.add(f.getTeks());
		}
		indeksKata = new TfIdfIndex(Tokenizer::words);
		indeksKata.build(teks);
		indeksKepingan = new TfIdfIndex(s -> Tokenizer.charGrams(s, 4));
		indeksKepingan.build(teks);
	}
	
	@Test
	public void maksimumDiambilPerJalurSebelumDigabung() {
		// Diperiksa ulang setelah tugas 05: pipeline penelitian (fusi1() di
		// eksperimen2.py) memaksimumkan tiap jalur DULU, baru menggabung. Urutan
		// sebaliknya (gabung per surface form, baru maksimumkan) diukur menyimpang
		// pada 17,8% query 10-besar — lihat docs/keputusan.md "Dua penyimpangan K5".
		String query = "diabetes mellitus";
		double alpha = 0.5;

		double[] skorKata = indeksKata.search(query);
		double[] skorKepingan = indeksKepingan.search(query);

		// Buktikan korpus ini benar-benar membelah dua jalur: form judul (indeks 0)
		// unggul di kata, form alias (indeks 1) unggul di kepingan.
		assertTrue("judul harus unggul di jalur kata", skorKata[0] > skorKata[1]);
		assertTrue("alias harus unggul di jalur kepingan", skorKepingan[1] > skorKepingan[0]);

		double skorBenar = alpha * Math.max(skorKata[0], skorKata[1]) + (1 - alpha) * Math.max(skorKepingan[0],
		    skorKepingan[1]);

		double kombinasiForm0 = alpha * skorKata[0] + (1 - alpha) * skorKepingan[0];
		double kombinasiForm1 = alpha * skorKata[1] + (1 - alpha) * skorKepingan[1];
		double skorSalah = Math.max(kombinasiForm0, kombinasiForm1);

		// Untuk korpus ini kedua pendekatan harus berbeda, kalau tidak uji ini tidak
		// membuktikan apa pun.
		assertTrue("korpus harus membuat dua pendekatan berbeda", skorBenar > skorSalah + 1e-6);

		FusionSearch fusi = new FusionSearch(indeksKata, indeksKepingan, forms);
		List<RankedDocument> hasil = fusi.search(query, alpha);

		RankedDocument dok1 = cari(hasil, "konsep:1");
		assertEquals(skorBenar, dok1.getSkor(), EPS);
	}
	
	@Test
	public void alphaSatuMemberiHasilIdentikDenganIndeksKataSaja() {
		String query = "diabetes mellitus";
		
		FusionSearch fusi = new FusionSearch(indeksKata, indeksKepingan, forms);
		List<RankedDocument> hasil = fusi.search(query, 1.0);
		
		double[] skorKata = indeksKata.search(query);
		double skorHarapanDok1 = Math.max(skorKata[0], skorKata[1]);
		
		RankedDocument dok1 = cari(hasil, "konsep:1");
		assertEquals(skorHarapanDok1, dok1.getSkor(), EPS);
	}
	
	@Test
	public void alphaNolMemberiHasilIdentikDenganIndeksKepinganSaja() {
		String query = "diabetes mellitus";
		
		FusionSearch fusi = new FusionSearch(indeksKata, indeksKepingan, forms);
		List<RankedDocument> hasil = fusi.search(query, 0.0);
		
		double[] skorKepingan = indeksKepingan.search(query);
		double skorHarapanDok1 = Math.max(skorKepingan[0], skorKepingan[1]);
		
		RankedDocument dok1 = cari(hasil, "konsep:1");
		assertEquals(skorHarapanDok1, dok1.getSkor(), EPS);
	}
	
	@Test
	public void dokumenDenganSkorDiBawahAmbangDibuang() {
		FusionSearch fusi = new FusionSearch(indeksKata, indeksKepingan, forms);
		List<RankedDocument> hasil = fusi.search("diabetes mellitus", 0.5);
		
		for (RankedDocument r : hasil) {
			assertTrue(r.getSkor() > FusionSearch.SCORE_THRESHOLD);
		}
		// dok2 ("completely unrelated term") tidak boleh nongol untuk query ini
		assertEquals(null, cariOrNull(hasil, "konsep:2"));
	}
	
	@Test
	public void panggilanBerulangMemberiUrutanIdentik() {
		FusionSearch fusi = new FusionSearch(indeksKata, indeksKepingan, forms);
		List<RankedDocument> pertama = fusi.search("diabetes mellitus", 0.45);
		
		for (int i = 0; i < 20; i++) {
			List<RankedDocument> ulang = fusi.search("diabetes mellitus", 0.45);
			assertEquals(pertama.size(), ulang.size());
			for (int j = 0; j < pertama.size(); j++) {
				assertEquals(pertama.get(j).getDokumen().getKunci(), ulang.get(j).getDokumen().getKunci());
				assertEquals(pertama.get(j).getSkor(), ulang.get(j).getSkor(), 0.0);
			}
		}
	}
	
	@Test
	public void skorAliasYangTepatSamaTidakDiencerkanOlehAliasLain() {
		// konsep dengan banyak alias tak relevan plus satu alias yang persis sama
		// dengan query; skor dokumen harus setara skor alias itu sendiri (~1,0),
		// bukan dirusak oleh alias-alias lain yang tidak relevan.
		List<String> aliasBanyak = new ArrayList<String>();
		for (int i = 0; i < 50; i++) {
			aliasBanyak.add("alias tidak relevan nomor " + i);
		}
		aliasBanyak.add("Panadol");
		VirtualDocument acetaminophen = new VirtualDocument("konsep", 132, "Acetaminophen", aliasBanyak,
		    new ArrayList<String>(), "", null);
		VirtualDocument lain = new VirtualDocument("konsep", 999, "Ibuprofen", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		
		List<SurfaceForm> korpus = new ArrayList<SurfaceForm>();
		korpus.addAll(extractor.extract(acetaminophen));
		korpus.addAll(extractor.extract(lain));
		List<String> teks = new ArrayList<String>();
		for (SurfaceForm f : korpus) {
			teks.add(f.getTeks());
		}
		TfIdfIndex kata = new TfIdfIndex(Tokenizer::words);
		kata.build(teks);
		TfIdfIndex kepingan = new TfIdfIndex(s -> Tokenizer.charGrams(s, 4));
		kepingan.build(teks);
		
		FusionSearch fusi = new FusionSearch(kata, kepingan, korpus);
		List<RankedDocument> hasil = fusi.search("panadol", 0.45);
		
		assertTrue("harus ada hasil", !hasil.isEmpty());
		assertEquals("Acetaminophen harus peringkat 1", "konsep:132", hasil.get(0).getDokumen().getKunci());
		assertEquals(1.0, hasil.get(0).getSkor(), 1e-6);
	}
	
	private static RankedDocument cari(List<RankedDocument> hasil, String kunci) {
		RankedDocument r = cariOrNull(hasil, kunci);
		assertTrue("tidak ditemukan: " + kunci, r != null);
		return r;
	}
	
	private static RankedDocument cariOrNull(List<RankedDocument> hasil, String kunci) {
		for (RankedDocument r : hasil) {
			if (r.getDokumen().getKunci().equals(kunci)) {
				return r;
			}
		}
		return null;
	}
}

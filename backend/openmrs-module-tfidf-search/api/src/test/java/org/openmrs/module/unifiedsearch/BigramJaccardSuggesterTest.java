package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Unlike OpenMrsHeuristicTest (b0, exact word-prefix), this suggester must survive a mistyped
 * letter and a query shorter than K5's NGRAM=4 -- that gap is exactly why it exists (see class
 * Javadoc).
 */
public class BigramJaccardSuggesterTest {

	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();

	private List<SurfaceForm> forms;

	@Before
	public void bangunKorpus() {
		VirtualDocument fever = new VirtualDocument("konsep", 1, "Fever", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument yellowFever = new VirtualDocument("konsep", 2, "Yellow fever", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument edema = new VirtualDocument("konsep", 3, "Pulmonary edema", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		forms = new ArrayList<SurfaceForm>();
		forms.addAll(extractor.extract(fever));
		forms.addAll(extractor.extract(yellowFever));
		forms.addAll(extractor.extract(edema));
	}

	@Test
	public void prefiksPendekDiBawahEmpatHurufTetapDapatSaran() {
		List<RankedDocument> hasil = BigramJaccardSuggester.search(forms, "fev");
		assertFalse(hasil.isEmpty());
		assertEquals("konsep:1", hasil.get(0).getDokumen().getKunci());
	}

	@Test
	public void judulLebihPendekPeringkatLebihAtasDaripadaJudulPanjangDenganKataSama() {
		List<RankedDocument> hasil = BigramJaccardSuggester.search(forms, "fev");
		assertEquals("konsep:1", hasil.get(0).getDokumen().getKunci());
		assertEquals("konsep:2", hasil.get(1).getDokumen().getKunci());
	}

	@Test
	public void typoSatuHurufMasihMenemukanHasil() {
		// "fefer" -- huruf ke-3 salah ketik, bukan prefiks "fever" sama sekali.
		List<RankedDocument> hasil = BigramJaccardSuggester.search(forms, "fefer");
		assertFalse(hasil.isEmpty());
		assertEquals("konsep:1", hasil.get(0).getDokumen().getKunci());
	}

	@Test
	public void queryTanpaKemiripanTidakMengembalikanHasil() {
		assertTrue(BigramJaccardSuggester.search(forms, "xyz").isEmpty());
	}

	@Test
	public void kodeDuaHurufTidakMengalahkanJudulYangBenarBenarCocok() {
		// Ditemukan langsung: alias "Fe" (lambang unsur besi) satu kepingan dengan "fev"
		// ({"fe"}), jadi Jaccard-nya 1/2 = 0.5 -- sama persis dengan skor "fev" vs "Fever".
		// Tanpa syarat minimal dua irisan, keduanya seri dan tie-break kunci bisa menaruh kode
		// dua huruf itu DI ATAS "Fever" yang justru paling relevan.
		VirtualDocument zatBesi = new VirtualDocument("konsep", 99, "Serum iron measurement",
		    Collections.<String> emptyList(), Collections.singletonList("Fe"), "", null);
		List<SurfaceForm> korpus = new ArrayList<SurfaceForm>(forms);
		korpus.addAll(extractor.extract(zatBesi));

		List<RankedDocument> hasil = BigramJaccardSuggester.search(korpus, "fev");
		assertEquals("konsep:1", hasil.get(0).getDokumen().getKunci());
		for (RankedDocument rd : hasil) {
			assertFalse("konsep:99".equals(rd.getDokumen().getKunci()));
		}
	}

	@Test
	public void kecocokanPersisDuaHurufTetapDiterima() {
		VirtualDocument tb = new VirtualDocument("konsep", 42, "Tb", Collections.<String> emptyList(),
		    Collections.<String> emptyList(), "", null);
		List<RankedDocument> hasil = BigramJaccardSuggester.search(extractor.extract(tb), "tb");
		assertEquals(1, hasil.size());
		assertEquals(1.0, hasil.get(0).getSkor(), 1e-9);
	}

	@Test
	public void skorAdalahJaccardBigramManual() {
		// "fev" -> {"fe","ev"}; "fever" -> {"fe","ev","ve","er"}. irisan=2, gabungan=4.
		List<RankedDocument> hasil = BigramJaccardSuggester.search(forms, "fev");
		assertEquals(0.5, hasil.get(0).getSkor(), 1e-9);
	}

	@Test
	public void namaPasienLewatJudulMenangAtasCatatanYangCumaMenyebutNamanya() {
		// Ditemukan langsung lewat query "mark": pasien "Mark Smith" (cocok lewat JUDULnya)
		// seri skor dengan hasillab yang alias-nya juga "Mark Smith" (cuma menyebut pasien
		// itu, bukan namanya sendiri). Tanpa tie-break ini, "hasillab" menang murni karena
		// alfabetis lebih awal dari "pasien", dan nama pasiennya sendiri terdorong keluar
		// dari dropdown 6-item.
		VirtualDocument pasien = new VirtualDocument("pasien", 1, "Mark Smith", Collections.<String> emptyList(),
		    Collections.<String> emptyList(), "", null);
		VirtualDocument hasilLab = new VirtualDocument("hasillab", 2, "Blood urea nitrogen",
		    Collections.singletonList("Mark Smith"), Collections.<String> emptyList(), "Pasien: Mark Smith", null);
		List<SurfaceForm> korpus = new ArrayList<SurfaceForm>();
		korpus.addAll(extractor.extract(pasien));
		korpus.addAll(extractor.extract(hasilLab));

		List<RankedDocument> hasil = BigramJaccardSuggester.search(korpus, "mark");
		assertEquals(2, hasil.size());
		assertEquals(hasil.get(0).getSkor(), hasil.get(1).getSkor(), 1e-9);
		assertEquals("pasien:1", hasil.get(0).getDokumen().getKunci());
		assertEquals("hasillab:2", hasil.get(1).getDokumen().getKunci());
	}
}

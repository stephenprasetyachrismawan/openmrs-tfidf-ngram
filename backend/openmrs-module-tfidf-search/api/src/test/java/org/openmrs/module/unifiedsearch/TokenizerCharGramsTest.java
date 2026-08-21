package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * charGrams() must mirror keping() in riset/eksperimen2.py bit for bit — see
 * the "TITIK RAWAN" note in tugas/04-kepingan-karakter.md. Three edge cases,
 * each with its expected value written out explicitly.
 */
public class TokenizerCharGramsTest {
	
	@Test
	public void spasiDigantiGarisBawahSetelahNormalisasi() {
		assertEquals(Arrays.asList("pulm", "ulm_", "lm_e", "m_ed", "_ede", "edem"),
		    Tokenizer.charGrams("pulm edem", 4));
	}
	
	@Test
	public void teksLebihPendekDariKDikembalikanUtuhSebagaiSatuKepingan() {
		assertEquals(Arrays.asList("tbc"), Tokenizer.charGrams("tbc", 4));
		assertEquals(Arrays.asList("pulm"), Tokenizer.charGrams("pulm", 4));
		assertEquals(Arrays.asList("tb"), Tokenizer.charGrams("tb", 4));
	}
	
	@Test
	public void teksKosongMenghasilkanDaftarKosongBukanSatuKepinganKosong() {
		List<String> hasil = Tokenizer.charGrams("", 4);
		
		assertEquals(0, hasil.size());
	}
	
	@Test
	public void teksYangHanyaBerisiKarakterYangDibuangJugaMenghasilkanDaftarKosong() {
		// norm("---") == "" karena seluruh karakter di luar [a-z0-9] disapu jadi spasi lalu di-trim
		List<String> hasil = Tokenizer.charGrams("---", 4);
		
		assertEquals(0, hasil.size());
	}
	
	@Test
	public void urutanUbahDulukanNormalisasiBaruGarisBawah() {
		// Kalau urutan terbalik (garis bawah dulu, baru normalisasi), spasi ganda dan
		// tanda baca di antaranya akan runtuh berbeda. norm() dulu menjamin tepat satu
		// spasi antar kata sebelum diganti "_", jadi "a   b,,," -> norm "a b" -> "a_b"
		// (panjang 3, di bawah k=4, dikembalikan utuh) — bukan "a___b" atau sejenisnya.
		assertEquals(Arrays.asList("a_b"), Tokenizer.charGrams("a   b,,,", 4));
	}
}

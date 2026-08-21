package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * AlphaConfig.parse() is tested directly (package-private) so this does not
 * need an OpenMRS Context/Spring container. current() itself just wires that
 * parsing to Context.getAdministrationService(), which is exercised through
 * the live module instead (see docs/keputusan.md tugas 06).
 */
public class AlphaConfigTest {
	
	@Test
	public void propertiKosongAtauTidakAdaMemberiDefault() {
		assertEquals(AlphaConfig.DEFAULT_ALPHA, AlphaConfig.parse(null), 0.0);
		assertEquals(AlphaConfig.DEFAULT_ALPHA, AlphaConfig.parse(""), 0.0);
		assertEquals(AlphaConfig.DEFAULT_ALPHA, AlphaConfig.parse("   "), 0.0);
	}
	
	@Test
	public void propertiValidDipakaiApaAdanya() {
		assertEquals(0.25, AlphaConfig.parse("0.25"), 1e-9);
		assertEquals(1.0, AlphaConfig.parse(" 1.0 "), 1e-9);
		assertEquals(0.0, AlphaConfig.parse("0"), 1e-9);
	}
	
	@Test
	public void propertiTidakBisaDiuraiMemberiDefaultBukanGalat() {
		assertEquals(AlphaConfig.DEFAULT_ALPHA, AlphaConfig.parse("bukan-angka"), 0.0);
	}
}

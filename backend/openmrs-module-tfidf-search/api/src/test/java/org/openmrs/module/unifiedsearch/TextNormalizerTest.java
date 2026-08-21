package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Guards against the default-locale toLowerCase() bug recorded in
 * docs/keputusan.md: under a Turkish JVM locale, "I" must not turn into the
 * dotless "ı" and get stripped as punctuation.
 */
public class TextNormalizerTest {
	
	private Locale sebelumnya;
	
	@Before
	public void simpanLocale() {
		sebelumnya = Locale.getDefault();
	}
	
	@After
	public void kembalikanLocale() {
		Locale.setDefault(sebelumnya);
	}
	
	@Test
	public void normalisasiStabilDiBawahLocaleTurki() {
		Locale.setDefault(Locale.forLanguageTag("tr"));
		
		assertEquals("insulin glargine", TextNormalizer.normalize("Insulin glargine"));
	}
	
	@Test
	public void normalisasiSamaDenganLocaleRoot() {
		Locale.setDefault(Locale.ROOT);
		
		assertEquals("insulin glargine", TextNormalizer.normalize("Insulin glargine"));
	}
}

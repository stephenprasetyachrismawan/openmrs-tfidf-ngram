package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SurfaceFormExtractorTest {
	
	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();
	
	private static VirtualDocument dokumen(String judul, List<String> alias, List<String> kode, String konteks) {
		return new VirtualDocument("konsep", 1, judul, alias, kode, konteks, null);
	}
	
	@Test
	public void documentWithoutAliasOrCodeStillYieldsOneSurfaceForm() {
		List<SurfaceForm> forms = extractor.extract(dokumen("Malaria", new ArrayList<String>(),
		    new ArrayList<String>(), ""));
		
		assertEquals(1, forms.size());
		assertEquals("Malaria", forms.get(0).getTeks());
		assertTrue(forms.get(0).isJudul());
	}
	
	@Test
	public void contextIsNeverASurfaceForm() {
		List<SurfaceForm> forms = extractor.extract(dokumen("Paracetamol", new ArrayList<String>(),
		    new ArrayList<String>(), "500 mg tablet"));
		
		assertEquals(1, forms.size());
		for (SurfaceForm form : forms) {
			assertFalse("500 mg tablet".equals(form.getTeks()));
		}
	}
	
	@Test
	public void eachAliasAndCodeBecomesItsOwnSurfaceForm() {
		List<SurfaceForm> forms = extractor.extract(dokumen("Diabetes mellitus, type 2",
		    Arrays.asList("Type 2 diabetes", "T2DM", "NIDDM"), Arrays.asList("E11", "44054006"), "Diagnosis"));
		
		assertEquals(6, forms.size());
		assertEquals(Arrays.asList("Diabetes mellitus, type 2", "Type 2 diabetes", "T2DM", "NIDDM", "E11", "44054006"),
		    teks(forms));
		assertTrue(forms.get(0).isJudul());
		for (int i = 1; i < forms.size(); i++) {
			assertFalse(forms.get(i).isJudul());
		}
	}
	
	@Test
	public void aliasesAreNotMergedIntoOneString() {
		List<SurfaceForm> forms = extractor.extract(dokumen("Aspirin", Arrays.asList("ASA", "acetylsalicylic acid"),
		    new ArrayList<String>(), ""));
		
		assertEquals(3, forms.size());
		assertFalse(teks(forms).contains("ASA acetylsalicylic acid"));
	}
	
	@Test
	public void formsWithoutAnyIndexableCharacterAreDropped() {
		List<SurfaceForm> forms = extractor.extract(dokumen("Fever", Arrays.asList("", "---"),
		    Arrays.asList("!!", "R50"), ""));
		
		assertEquals(Arrays.asList("Fever", "R50"), teks(forms));
	}
	
	private static List<String> teks(List<SurfaceForm> forms) {
		List<String> out = new ArrayList<String>();
		for (SurfaceForm form : forms) {
			out.add(form.getTeks());
		}
		return out;
	}
}

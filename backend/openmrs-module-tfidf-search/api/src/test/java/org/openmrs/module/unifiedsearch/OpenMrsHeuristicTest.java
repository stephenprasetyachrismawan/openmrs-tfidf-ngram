package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * The three acceptance queries from tugas/08-baseline-b0.md. The filter must
 * stay strict: a mid-word typo yields nothing, a truncated prefix still hits.
 */
public class OpenMrsHeuristicTest {

	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();

	private List<SurfaceForm> forms;

	@Before
	public void bangunKorpus() {
		VirtualDocument dm1 = new VirtualDocument("konsep", 1, "diabetes mellitus type 1", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument dm2 = new VirtualDocument("konsep", 2, "diabetes mellitus type 2", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		VirtualDocument edema = new VirtualDocument("konsep", 3, "pulmonary edema", new ArrayList<String>(),
		    new ArrayList<String>(), "", null);
		forms = new ArrayList<SurfaceForm>();
		forms.addAll(extractor.extract(dm1));
		forms.addAll(extractor.extract(dm2));
		forms.addAll(extractor.extract(edema));
	}

	@Test
	public void diabeteMelitusMengembalikanNolHasil() {
		assertTrue(OpenMrsHeuristic.search(forms, "diabete melitus").isEmpty());
	}

	@Test
	public void pulmEdemMengembalikanHasil() {
		List<RankedDocument> hasil = OpenMrsHeuristic.search(forms, "pulm edem");
		assertFalse(hasil.isEmpty());
		assertEquals("konsep:3", hasil.get(0).getDokumen().getKunci());
	}

	@Test
	public void diabetesMellitusPeringkatAtasMasukAkal() {
		List<RankedDocument> hasil = OpenMrsHeuristic.search(forms, "diabetes mellitus");
		assertFalse(hasil.isEmpty());
		assertEquals("konsep:1", hasil.get(0).getDokumen().getKunci());
		assertEquals("konsep:2", hasil.get(1).getDokumen().getKunci());
	}

	@Test
	public void skorMengikutiEksperimen2() {
		List<RankedDocument> hasil = OpenMrsHeuristic.search(forms, "pulm edem");
		// title +500, two prefix-only words +100 each, -0.6 * len("pulmonary edema")
		double expected = 500.0 + 100.0 + 100.0 - ("pulmonary edema".length() * 0.6);
		assertEquals(expected, hasil.get(0).getSkor(), 1e-9);
	}

	@Test
	public void aliasBukanJudulTidakMendapatPlusLimaRatus() {
		VirtualDocument withAlias = new VirtualDocument("konsep", 9, "other name", Arrays.asList("diabetes mellitus"),
		    Collections.<String> emptyList(), "", null);
		List<SurfaceForm> onlyAlias = extractor.extract(withAlias);
		List<RankedDocument> hasil = OpenMrsHeuristic.search(onlyAlias, "diabetes mellitus");
		assertEquals(1, hasil.size());
		String aliasNorm = TextNormalizer.normalize("diabetes mellitus");
		double expected = 1000.0 + 200.0 + 200.0 - (aliasNorm.length() * 0.6);
		assertEquals(expected, hasil.get(0).getSkor(), 1e-9);
	}
}

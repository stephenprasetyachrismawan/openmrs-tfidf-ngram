package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Cross-check named in the "TITIK RAWAN" note of tugas/04-kepingan-karakter.md:
 * 20 concept titles sampled from riset/data/konsep.jsonl (random.seed(42)), run
 * through keping() in Python and through Tokenizer.charGrams() here — the two
 * lists must be identical for every title.
 * <p>
 * src/test/resources/silang-kepingan.tsv holds the Python-computed reference,
 * one title per line: {@code judul<TAB>gram1|gram2|...}. It was produced once
 * by riset/eksperimen2.py's own keping(), not re-derived from this class.
 */
public class CharGramsSilangPythonTest {
	
	@Test
	public void duaPuluhJudulMenghasilkanKepinganYangSamaDenganPython() throws IOException {
		List<String[]> baris = bacaFixture();
		assertEquals("fixture harus berisi 20 judul", 20, baris.size());
		
		for (String[] b : baris) {
			String judul = b[0];
			List<String> kepinganPython = b[1].isEmpty() ? new ArrayList<String>() : Arrays.asList(b[1].split("[|]"));
			List<String> kepinganJava = Tokenizer.charGrams(judul, 4);
			
			assertEquals("kepingan berbeda untuk judul: " + judul, kepinganPython, kepinganJava);
		}
	}
	
	private List<String[]> bacaFixture() throws IOException {
		List<String[]> out = new ArrayList<String[]>();
		InputStream in = getClass().getResourceAsStream("/silang-kepingan.tsv");
		assertTrue("fixture silang-kepingan.tsv tidak ditemukan di classpath", in != null);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				int tab = line.indexOf('\t');
				out.add(new String[] { line.substring(0, tab), line.substring(tab + 1) });
			}
		}
		finally {
			reader.close();
		}
		return out;
	}
}

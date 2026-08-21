package org.openmrs.module.unifiedsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * The "TITIK RAWAN" test required by tugas/07-weighted-rrf.md: the same E2
 * (RRF polos, deliberately full of exact score ties) search run 20 times in
 * this JVM, and ALSO in brand-new JVM processes spawned from scratch. All of
 * them must agree, not just the in-process ones -- a hash-iteration-order bug
 * can easily be invisible within one process (JVM hash seeding can be stable
 * for a run) and only show up across processes, which is exactly how this
 * class of bug wasted time before (see docs/keputusan.md).
 */
public class WeightedRrfSeparateProcessDeterminismTest {
	
	@Test
	public void duaPuluhKaliDalamProsesYangSamaMemberiUrutanIdentik() {
		List<String> pertama = baris(WeightedRrfDeterminismRunner.hasil());
		assertFalse("skenario ini harus menghasilkan sesuatu", pertama.isEmpty());
		
		for (int i = 0; i < 20; i++) {
			assertEquals(pertama, baris(WeightedRrfDeterminismRunner.hasil()));
		}
	}
	
	@Test
	public void prosesTerpisahMemberiKeluaranIdentikDenganProsesIni() throws IOException, InterruptedException {
		String acuan = gabung(baris(WeightedRrfDeterminismRunner.hasil()));
		
		String proses1 = jalankanDiProsesBaru();
		String proses2 = jalankanDiProsesBaru();
		
		assertTrue("proses terpisah tidak boleh kosong", !proses1.isEmpty());
		assertEquals("proses terpisah #1 harus sama dengan proses ini", acuan, proses1);
		assertEquals("proses terpisah #2 harus sama dengan proses ini", acuan, proses2);
		assertEquals("dua proses terpisah harus sama satu sama lain", proses1, proses2);
	}
	
	private static String jalankanDiProsesBaru() throws IOException, InterruptedException {
		String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator
		        + "java";
		String classpath = System.getProperty("java.class.path");
		
		ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", classpath, WeightedRrfDeterminismRunner.class.getName());
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		
		StringBuilder out = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				out.append(line).append('\n');
			}
		}
		finally {
			reader.close();
		}
		
		int exit = proc.waitFor();
		assertEquals("proses terpisah harus keluar bersih; keluaran:\n" + out, 0, exit);
		return out.toString().trim();
	}
	
	private static List<String> baris(List<RankedDocument> hasil) {
		List<String> out = new ArrayList<String>();
		for (RankedDocument r : hasil) {
			out.add(r.getDokumen().getKunci() + "=" + r.getSkor());
		}
		return out;
	}
	
	private static String gabung(List<String> baris) {
		StringBuilder sb = new StringBuilder();
		for (String b : baris) {
			sb.append(b).append('\n');
		}
		return sb.toString().trim();
	}
}

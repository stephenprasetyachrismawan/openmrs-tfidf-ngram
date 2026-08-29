package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * REST-facing search with patient privilege filtering (CLAUDE.md rule 5).
 */
@Component("unifiedsearch.unifiedSearchService")
public class UnifiedSearchService {

	private static final String PRIVILEGE_VIEW_PATIENTS = "View Patients";

	private final IndexBuilder indexBuilder;

	@Autowired
	public UnifiedSearchService(IndexBuilder indexBuilder) {
		this.indexBuilder = indexBuilder;
	}

	public Timed<Map<String, Object>> search(String q, String mode, int limit, String entitasFilter) {
		EvalService.validateMode(mode);
		if (q == null || q.trim().isEmpty()) {
			throw new IllegalArgumentException("parameter q wajib");
		}
		if (limit <= 0) {
			limit = 10;
		}
		indexBuilder.ensureBuilt();
		long mulai = System.nanoTime();
		RankingEngine engine = indexBuilder.createEngine();
		double alpha = AlphaConfig.current();
		List<SearchHit> hits = engine.search(mode, q.trim(), alpha);
		boolean mayViewPatients = Context.hasPrivilege(PRIVILEGE_VIEW_PATIENTS);
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		for (SearchHit hit : hits) {
			VirtualDocument d = hit.getDokumen();
			if (isDataPasien(d) && !mayViewPatients) {
				continue;
			}
			if (entitasFilter != null && !entitasFilter.trim().isEmpty()
			        && !entitasFilter.trim().equalsIgnoreCase(d.getEntitas())) {
				continue;
			}
			results.add(toResultRow(hit, mode));
			if (results.size() >= limit) {
				break;
			}
		}
		long waktuMs = (System.nanoTime() - mulai) / 1000000L;
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("query", q.trim());
		out.put("mode", mode);
		out.put("results", results);
		return new Timed<Map<String, Object>>(out, waktuMs);
	}

	/**
	 * Saran ketikan untuk dropdown navbar (BigramJaccardSuggester) -- bukan salah satu dari
	 * mode b0/b1/e1/e3 yang dikunci EvalService.REST_MODES, jalur terpisah sepenuhnya. Sama
	 * seperti {@link #search}, hasil pasien/hasillab/kondisi disaring lewat privilege "View Patients"
	 * (CLAUDE.md aturan 5) sebelum dikembalikan.
	 */
	public Timed<Map<String, Object>> saran(String q, int limit) {
		if (q == null || q.trim().isEmpty()) {
			throw new IllegalArgumentException("parameter q wajib");
		}
		if (limit <= 0) {
			limit = 10;
		}
		indexBuilder.ensureBuilt();
		long mulai = System.nanoTime();
		List<RankedDocument> gabungan = new ArrayList<RankedDocument>();
		for (FusionSearch fusion : indexBuilder.getLokal().values()) {
			gabungan.addAll(BigramJaccardSuggester.search(fusion.getSurfaceForms(), q.trim()));
		}
		Collections.sort(gabungan, BigramJaccardSuggester.KUNCI_COMPARATOR);
		boolean mayViewPatients = Context.hasPrivilege(PRIVILEGE_VIEW_PATIENTS);
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		int peringkat = 0;
		for (RankedDocument rd : gabungan) {
			VirtualDocument d = rd.getDokumen();
			if (isDataPasien(d) && !mayViewPatients) {
				continue;
			}
			peringkat++;
			SearchHit hit = new SearchHit(d, rd.getSkor(), null, Integer.valueOf(peringkat), null);
			results.add(toResultRow(hit, "saran"));
			if (results.size() >= limit) {
				break;
			}
		}
		long waktuMs = (System.nanoTime() - mulai) / 1000000L;
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("query", q.trim());
		out.put("mode", "saran");
		out.put("results", results);
		return new Timed<Map<String, Object>>(out, waktuMs);
	}

	/**
	 * True for every entity whose rows identify a patient. "hasillab" and "kondisi"
	 * carry the same patient-identifying data as "pasien" (patient full name as an
	 * indexed alias, plus tautan_pasien), so CLAUDE.md rule 5 applies to all three,
	 * not just "pasien".
	 */
	private static boolean isDataPasien(VirtualDocument d) {
		String entitas = d.getEntitas();
		return "pasien".equals(entitas) || "hasillab".equals(entitas) || "kondisi".equals(entitas);
	}

	private static Map<String, Object> toResultRow(SearchHit hit, String mode) {
		VirtualDocument d = hit.getDokumen();
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("entitas", d.getEntitas());
		row.put("id", Integer.valueOf(d.getId()));
		row.put("judul", d.getJudul());
		row.put("konteks", d.getKonteks());
		row.put("skor", Double.valueOf(round4(hit.getSkor())));
		row.put("skor_asli", hit.getSkorAsli() == null ? null : Double.valueOf(round4(hit.getSkorAsli().doubleValue())));
		row.put("peringkat_di_tabel", hit.getPeringkatDiTabel());
		row.put("bobot_tabel", "e3".equals(mode) && hit.getBobotTabel() != null
		        ? Double.valueOf(round4(hit.getBobotTabel().doubleValue())) : null);
		row.put("url", buildUrl(d));
		return row;
	}

	/**
	 * O3 (the current RefApp frontend) only has a real page for patients
	 * (the chart, addressed by UUID, not the numeric id in the index) --
	 * verified against docs/arsip/routes.registry.json.sebelum-unifiedsearch,
	 * no other installed app owns a concept/drug/form/location/provider
	 * detail route. Those five keep pointing at the legacy admin UI because
	 * that is the only page that exists for them, not because O3 was skipped.
	 * hasillab and kondisi have no detail page of their own either (one is an
	 * obs, the other a problem-list row, neither a concept/patient in its own
	 * right) -- the closest useful destination is the patient they belong to
	 * (tautan_pasien), same chart as "pasien".
	 */
	private static String buildUrl(VirtualDocument d) {
		Integer patientId = "pasien".equals(d.getEntitas()) ? Integer.valueOf(d.getId()) : d.getTautanPasien();
		if (patientId != null) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (patient != null) {
				return "/openmrs/spa/patient/" + patient.getUuid() + "/chart";
			}
		}
		return ResultUrlBuilder.url(d);
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}
}

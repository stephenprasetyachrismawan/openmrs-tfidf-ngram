package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
			if ("pasien".equals(d.getEntitas()) && !mayViewPatients) {
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
		row.put("url", ResultUrlBuilder.url(d));
		return row;
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}
}

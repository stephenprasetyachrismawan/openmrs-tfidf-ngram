package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.unifiedsearch.DevQueryGoldStandard.EvalQuery;
import org.openmrs.module.unifiedsearch.DevQueryGoldStandard.Gold;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the fixed 100-query dev gold standard (exported once from {@code eksperimen2.py} by
 * {@code riset/ekspor_gold_dev.py}, see {@link DevQueryGoldStandard}) against the in-memory
 * index. Never touches {@code qs[100:]} (CLAUDE.md rule 10).
 */
@Component("unifiedsearch.evalService")
public class EvalService {

	private static final Log log = LogFactory.getLog(EvalService.class);

	private static final String[] REST_MODES = new String[] { "b0", "b1", "e1", "e3" };

	private final IndexBuilder indexBuilder;

	private volatile Gold gold;

	@Autowired
	public EvalService(IndexBuilder indexBuilder) {
		this.indexBuilder = indexBuilder;
	}

	@Transactional(readOnly = true)
	public Timed<Map<String, Object>> evaluate(String mode) {
		validateMode(mode);
		indexBuilder.ensureBuilt();
		long mulai = System.nanoTime();
		RankingEngine engine = indexBuilder.createEngine();
		double alpha = AlphaConfig.current();
		List<EvalQuery> queries = devQueries();
		List<EvalMetrics.Result> rows = new ArrayList<EvalMetrics.Result>();
		Map<String, List<EvalMetrics.Result>> rowsPerTipe = new TreeMap<String, List<EvalMetrics.Result>>();
		for (EvalQuery item : queries) {
			List<String> keys = engine.searchKeys(mode, item.getQ(), alpha, 10);
			EvalMetrics.Result r = EvalMetrics.compute(keys, item.getRel());
			rows.add(r);
			rowsPerTipe.computeIfAbsent(item.getTipe(), k -> new ArrayList<EvalMetrics.Result>()).add(r);
		}
		long waktuMs = (System.nanoTime() - mulai) / 1000000L;
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("mode", mode);
		out.put("n_query", Integer.valueOf(queries.size()));
		out.put("gold", "dev 100 query (bangun_query SEED=42, qs[:100]) — riset/eksperimen2.py, diekspor via riset/ekspor_gold_dev.py");
		out.put("gold_sha256", gold().getSha256Sumber());
		Map<String, Double> avg = EvalMetrics.average(rows);
		out.put("p1", avg.get("p1"));
		out.put("p5", avg.get("p5"));
		out.put("r10", avg.get("r10"));
		out.put("mrr", avg.get("mrr"));
		out.put("map", avg.get("map"));
		out.put("ndcg10", avg.get("ndcg10"));
		out.put("pct_nol", avg.get("pct_nol"));
		out.put("waktu_indeks_ms", Long.valueOf(indexBuilder.getBuildDurationMs()));
		Map<String, Object> perTipe = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, List<EvalMetrics.Result>> entry : rowsPerTipe.entrySet()) {
			Map<String, Double> avgTipe = EvalMetrics.average(entry.getValue());
			Map<String, Object> ringkasTipe = new LinkedHashMap<String, Object>();
			ringkasTipe.put("n_query", Integer.valueOf(entry.getValue().size()));
			ringkasTipe.put("ndcg10", avgTipe.get("ndcg10"));
			ringkasTipe.put("p1", avgTipe.get("p1"));
			perTipe.put(entry.getKey(), ringkasTipe);
		}
		out.put("per_tipe", perTipe);
		log.info("Eval " + mode + " on " + queries.size() + " dev queries finished in " + waktuMs + " ms");
		return new Timed<Map<String, Object>>(out, waktuMs);
	}

	private List<EvalQuery> devQueries() {
		return gold().getQueries();
	}

	private Gold gold() {
		if (gold == null) {
			synchronized (this) {
				if (gold == null) {
					gold = DevQueryGoldStandard.load();
				}
			}
		}
		return gold;
	}

	static void validateMode(String mode) {
		for (String allowed : REST_MODES) {
			if (allowed.equals(mode)) {
				return;
			}
		}
		throw new IllegalArgumentException("mode harus b0, b1, e1, atau e3");
	}
}

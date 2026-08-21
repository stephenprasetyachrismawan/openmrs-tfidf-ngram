package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IR metrics matching {@code metrik()} in {@code riset/eksperimen2.py}.
 */
public final class EvalMetrics {

	private EvalMetrics() {
	}

	public static final class Result {

		public final double p1;

		public final double p5;

		public final double r10;

		public final double mrr;

		public final double map;

		public final double ndcg;

		public final double kosong;

		public Result(double p1, double p5, double r10, double mrr, double map, double ndcg, double kosong) {
			this.p1 = p1;
			this.p5 = p5;
			this.r10 = r10;
			this.mrr = mrr;
			this.map = map;
			this.ndcg = ndcg;
			this.kosong = kosong;
		}
	}

	public static Result compute(List<String> rankedKeys, Map<String, Integer> rel) {
		List<String> ids = rankedKeys == null ? Collections.<String> emptyList() : rankedKeys;
		int r = rel.size();
		double p1 = (!ids.isEmpty() && grade(rel, ids.get(0)) > 0) ? 1.0 : 0.0;
		int h5 = 0;
		int h10 = 0;
		for (int i = 0; i < Math.min(5, ids.size()); i++) {
			if (grade(rel, ids.get(i)) > 0) {
				h5++;
			}
		}
		for (int i = 0; i < Math.min(10, ids.size()); i++) {
			if (grade(rel, ids.get(i)) > 0) {
				h10++;
			}
		}
		double mrr = 0.0;
		for (int i = 0; i < Math.min(10, ids.size()); i++) {
			if (grade(rel, ids.get(i)) > 0) {
				mrr = 1.0 / (i + 1);
				break;
			}
		}
		double ap = 0.0;
		int c = 0;
		for (int i = 0; i < Math.min(10, ids.size()); i++) {
			if (grade(rel, ids.get(i)) > 0) {
				c++;
				ap += (double) c / (i + 1);
			}
		}
		double map = r > 0 ? ap / r : 0.0;
		double dcg = 0.0;
		for (int i = 0; i < Math.min(10, ids.size()); i++) {
			int g = grade(rel, ids.get(i));
			dcg += (Math.pow(2, g) - 1) / (Math.log(i + 2) / Math.log(2));
		}
		List<Integer> ideal = new ArrayList<Integer>(rel.values());
		ideal.sort(Collections.reverseOrder());
		double idcg = 0.0;
		for (int i = 0; i < Math.min(10, ideal.size()); i++) {
			idcg += (Math.pow(2, ideal.get(i).intValue()) - 1) / (Math.log(i + 2) / Math.log(2));
		}
		double ndcg = idcg > 0 ? dcg / idcg : 0.0;
		double kosong = ids.isEmpty() ? 1.0 : 0.0;
		return new Result(p1, (double) h5 / 5.0, r > 0 ? (double) h10 / r : 0.0, mrr, map, ndcg, kosong);
	}

	private static int grade(Map<String, Integer> rel, String key) {
		Integer v = rel.get(key);
		return v == null ? 0 : v.intValue();
	}

	public static Map<String, Double> average(List<Result> rows) {
		Map<String, Double> out = new LinkedHashMap<String, Double>();
		if (rows.isEmpty()) {
			return out;
		}
		double p1 = 0, p5 = 0, r10 = 0, mrr = 0, map = 0, ndcg = 0, kosong = 0;
		for (Result r : rows) {
			p1 += r.p1;
			p5 += r.p5;
			r10 += r.r10;
			mrr += r.mrr;
			map += r.map;
			ndcg += r.ndcg;
			kosong += r.kosong;
		}
		double n = rows.size();
		out.put("p1", Double.valueOf(p1 / n));
		out.put("p5", Double.valueOf(p5 / n));
		out.put("r10", Double.valueOf(r10 / n));
		out.put("mrr", Double.valueOf(mrr / n));
		out.put("map", Double.valueOf(map / n));
		out.put("ndcg10", Double.valueOf(ndcg / n));
		out.put("pct_nol", Double.valueOf(100.0 * kosong / n));
		return out;
	}
}

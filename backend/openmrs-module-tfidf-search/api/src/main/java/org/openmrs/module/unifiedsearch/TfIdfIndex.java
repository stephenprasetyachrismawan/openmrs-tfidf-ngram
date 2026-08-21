package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inverted index with ltc weighting and cosine search (docs/algoritma.md sec. 2):
 *
 * <pre>
 * tf_w  = 1 + ln(count)
 * idf_w = ln(N / df) + 1
 * bobot = tf_w * idf_w, then cosine-normalized per surface form
 * </pre>
 *
 * The same class serves both the word index (task 03) and the character n-gram
 * index (task 04) — only the {@link TokenFunction} passed to {@link #build}
 * differs; the weighting formula never changes.
 * <p>
 * idf is kept in a {@link TreeMap} so iteration is deterministic (CLAUDE.md
 * rule 1). Scores are written into an array indexed by surface form position,
 * so the result order does not depend on hash-map iteration either.
 */
public class TfIdfIndex {
	
	private final TokenFunction tokenizer;
	
	private final TreeMap<String, Double> idf = new TreeMap<String, Double>();
	
	private final List<Map<String, Double>> vectors = new ArrayList<Map<String, Double>>();
	
	private int n;
	
	public TfIdfIndex(TokenFunction tokenizer) {
		this.tokenizer = tokenizer;
	}
	
	public void build(List<String> surfaceForms) {
		idf.clear();
		vectors.clear();
		n = surfaceForms.size();
		
		List<List<String>> tokenized = new ArrayList<List<String>>(n);
		Map<String, Integer> df = new HashMap<String, Integer>();
		for (String form : surfaceForms) {
			List<String> tokens = tokenizer.tokenize(form);
			tokenized.add(tokens);
			for (String term : new java.util.HashSet<String>(tokens)) {
				Integer count = df.get(term);
				df.put(term, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			}
		}
		for (Map.Entry<String, Integer> entry : df.entrySet()) {
			idf.put(entry.getKey(), Double.valueOf(Math.log((double) n / entry.getValue().intValue()) + 1.0));
		}
		
		for (List<String> tokens : tokenized) {
			vectors.add(weightedVector(tokens));
		}
	}
	
	/**
	 * Cosine score of {@code query} against every surface form used to build this
	 * index, returned in the same order they were passed to {@link #build}. A term
	 * absent from the index is silently ignored rather than raising an error, so a
	 * query with unknown words yields 0.0, not an exception.
	 */
	public double[] search(String query) {
		Map<String, Double> qvec = weightedVector(tokenizer.tokenize(query));
		double[] scores = new double[n];
		for (int i = 0; i < n; i++) {
			scores[i] = dot(qvec, vectors.get(i));
		}
		return scores;
	}
	
	private Map<String, Double> weightedVector(List<String> tokens) {
		Map<String, Integer> tf = new HashMap<String, Integer>();
		for (String term : tokens) {
			if (!idf.containsKey(term)) {
				continue;
			}
			Integer count = tf.get(term);
			tf.put(term, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		Map<String, Double> weights = new HashMap<String, Double>();
		double sumSquares = 0.0;
		for (Map.Entry<String, Integer> entry : tf.entrySet()) {
			double weight = (1.0 + Math.log(entry.getValue().intValue())) * idf.get(entry.getKey()).doubleValue();
			weights.put(entry.getKey(), Double.valueOf(weight));
			sumSquares += weight * weight;
		}
		double norm = sumSquares > 0.0 ? Math.sqrt(sumSquares) : 1.0;
		Map<String, Double> normalized = new HashMap<String, Double>();
		for (Map.Entry<String, Double> entry : weights.entrySet()) {
			normalized.put(entry.getKey(), Double.valueOf(entry.getValue().doubleValue() / norm));
		}
		return normalized;
	}
	
	private static double dot(Map<String, Double> a, Map<String, Double> b) {
		Map<String, Double> smaller = a.size() <= b.size() ? a : b;
		Map<String, Double> larger = smaller == a ? b : a;
		double sum = 0.0;
		for (Map.Entry<String, Double> entry : smaller.entrySet()) {
			Double other = larger.get(entry.getKey());
			if (other != null) {
				sum += entry.getValue().doubleValue() * other.doubleValue();
			}
		}
		return sum;
	}
	
	/** Vocabulary size, mostly useful for tests and memory-usage reporting. */
	public int vocabularySize() {
		return idf.size();
	}
	
	public Map<String, Double> getIdf() {
		return Collections.unmodifiableMap(idf);
	}
}

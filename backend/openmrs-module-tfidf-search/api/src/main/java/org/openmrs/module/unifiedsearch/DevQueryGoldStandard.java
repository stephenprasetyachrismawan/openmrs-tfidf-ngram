package org.openmrs.module.unifiedsearch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the fixed 100-query dev gold standard from {@code gold-dev-100.json}, exported once
 * by {@code riset/ekspor_gold_dev.py} from {@code eksperimen2.py}'s {@code bangun_query()}.
 *
 * <p>Query generation used to be reimplemented here with {@code java.util.Random(42)}, on the
 * assumption that the same seed reproduces the same sequence as Python's
 * {@code random.Random(42)}. It does not: the two use different PRNG algorithms (LCG vs
 * Mersenne Twister), so the query lists diverged even though the ranking logic itself was
 * correct (see {@code riset/hasil3/investigasi_gap_eval.json}). Generating the query set here
 * in Java can never match Python bit-for-bit, so this class no longer tries — it reads the
 * export instead.
 */
public final class DevQueryGoldStandard {

	private static final String RESOURCE = "gold-dev-100.json";

	private DevQueryGoldStandard() {
	}

	public static final class EvalQuery {

		private final String q;

		private final Map<String, Integer> rel;

		public EvalQuery(String q, Map<String, Integer> rel) {
			this.q = q;
			this.rel = rel;
		}

		public String getQ() {
			return q;
		}

		public Map<String, Integer> getRel() {
			return rel;
		}
	}

	public static final class Gold {

		private final List<EvalQuery> queries;

		private final String sha256Sumber;

		Gold(List<EvalQuery> queries, String sha256Sumber) {
			this.queries = queries;
			this.sha256Sumber = sha256Sumber;
		}

		public List<EvalQuery> getQueries() {
			return queries;
		}

		public String getSha256Sumber() {
			return sha256Sumber;
		}
	}

	public static Gold load() {
		String json = readResource();
		Object parsed = new MiniJson(json).parseValue();
		if (!(parsed instanceof Map)) {
			throw new IllegalStateException(RESOURCE + " harus berisi objek JSON di akar");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>) parsed;
		Object rawQueries = root.get("queries");
		if (!(rawQueries instanceof List)) {
			throw new IllegalStateException(RESOURCE + " tidak memuat field 'queries' berupa larik");
		}
		List<EvalQuery> out = new ArrayList<EvalQuery>();
		for (Object item : (List<?>) rawQueries) {
			@SuppressWarnings("unchecked")
			Map<String, Object> row = (Map<String, Object>) item;
			String q = String.valueOf(row.get("q"));
			@SuppressWarnings("unchecked")
			Map<String, Object> relRaw = (Map<String, Object>) row.get("rel");
			Map<String, Integer> rel = new LinkedHashMap<String, Integer>();
			for (Map.Entry<String, Object> e : relRaw.entrySet()) {
				rel.put(e.getKey(), Integer.valueOf(((Number) e.getValue()).intValue()));
			}
			out.add(new EvalQuery(q, rel));
		}
		Object sha = root.get("sha256_sumber");
		return new Gold(out, sha == null ? null : String.valueOf(sha));
	}

	private static String readResource() {
		try (InputStream in = DevQueryGoldStandard.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Resource " + RESOURCE + " tidak ditemukan di classpath");
			}
			java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
			byte[] chunk = new byte[8192];
			int n;
			while ((n = in.read(chunk)) != -1) {
				buf.write(chunk, 0, n);
			}
			return new String(buf.toByteArray(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Gagal membaca " + RESOURCE, e);
		}
	}

	/**
	 * Minimal recursive-descent JSON parser for the fixed, self-controlled shape written by
	 * {@code riset/ekspor_gold_dev.py} — objects, arrays, strings, numbers, booleans, null.
	 * No third-party JSON dependency is declared in {@code api/pom.xml}, so this avoids adding
	 * one just to read a 100-line resource we generate ourselves.
	 */
	private static final class MiniJson {

		private final String s;

		private int i;

		MiniJson(String s) {
			this.s = s;
		}

		Object parseValue() {
			skipWs();
			char c = s.charAt(i);
			if (c == '{') {
				return parseObject();
			}
			if (c == '[') {
				return parseArray();
			}
			if (c == '"') {
				return parseString();
			}
			if (c == 't') {
				expect("true");
				return Boolean.TRUE;
			}
			if (c == 'f') {
				expect("false");
				return Boolean.FALSE;
			}
			if (c == 'n') {
				expect("null");
				return null;
			}
			return parseNumber();
		}

		private Map<String, Object> parseObject() {
			Map<String, Object> out = new LinkedHashMap<String, Object>();
			i++; // {
			skipWs();
			if (peek() == '}') {
				i++;
				return out;
			}
			while (true) {
				skipWs();
				String key = parseString();
				skipWs();
				if (s.charAt(i) != ':') {
					throw malformed();
				}
				i++;
				Object val = parseValue();
				out.put(key, val);
				skipWs();
				char c = s.charAt(i);
				if (c == ',') {
					i++;
					continue;
				}
				if (c == '}') {
					i++;
					break;
				}
				throw malformed();
			}
			return out;
		}

		private List<Object> parseArray() {
			List<Object> out = new ArrayList<Object>();
			i++; // [
			skipWs();
			if (peek() == ']') {
				i++;
				return out;
			}
			while (true) {
				out.add(parseValue());
				skipWs();
				char c = s.charAt(i);
				if (c == ',') {
					i++;
					continue;
				}
				if (c == ']') {
					i++;
					break;
				}
				throw malformed();
			}
			return out;
		}

		private String parseString() {
			if (s.charAt(i) != '"') {
				throw malformed();
			}
			i++;
			StringBuilder sb = new StringBuilder();
			while (true) {
				char c = s.charAt(i++);
				if (c == '"') {
					break;
				}
				if (c == '\\') {
					char esc = s.charAt(i++);
					switch (esc) {
						case '"':
							sb.append('"');
							break;
						case '\\':
							sb.append('\\');
							break;
						case '/':
							sb.append('/');
							break;
						case 'n':
							sb.append('\n');
							break;
						case 't':
							sb.append('\t');
							break;
						case 'r':
							sb.append('\r');
							break;
						case 'b':
							sb.append('\b');
							break;
						case 'f':
							sb.append('\f');
							break;
						case 'u':
							String hex = s.substring(i, i + 4);
							i += 4;
							sb.append((char) Integer.parseInt(hex, 16));
							break;
						default:
							throw malformed();
					}
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
		}

		private Object parseNumber() {
			int start = i;
			while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
				i++;
			}
			String tok = s.substring(start, i);
			if (tok.indexOf('.') >= 0 || tok.indexOf('e') >= 0 || tok.indexOf('E') >= 0) {
				return Double.valueOf(tok);
			}
			return Long.valueOf(tok);
		}

		private void expect(String lit) {
			if (!s.regionMatches(i, lit, 0, lit.length())) {
				throw malformed();
			}
			i += lit.length();
		}

		private char peek() {
			return s.charAt(i);
		}

		private void skipWs() {
			while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
				i++;
			}
		}

		private IllegalStateException malformed() {
			return new IllegalStateException("JSON gold rusak pada posisi " + i);
		}
	}
}

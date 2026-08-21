package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The two ways text is cut into terms (docs/algoritma.md sec. 1).
 */
public final class Tokenizer {

	private Tokenizer() {
	}

	public static List<String> words(String s) {
		String norm = TextNormalizer.normalize(s);
		if (norm.isEmpty()) {
			return new ArrayList<String>();
		}
		return new ArrayList<String>(Arrays.asList(norm.split(" ")));
	}

	/**
	 * Mirrors {@code keping()} in riset/eksperimen2.py exactly, including its two
	 * edge cases: an empty normalised text yields an empty list (not {@code [""]}),
	 * and text shorter than {@code n} is returned whole as one gram (not an empty
	 * list) — short queries depend on this. Spaces become {@code _} AFTER
	 * normalisation, not before; the order changes the result.
	 */
	public static List<String> charGrams(String s, int n) {
		String t = TextNormalizer.normalize(s).replace(' ', '_');
		List<String> out = new ArrayList<String>();
		if (t.isEmpty()) {
			return out;
		}
		if (t.length() < n) {
			out.add(t);
			return out;
		}
		for (int i = 0; i <= t.length() - n; i++) {
			out.add(t.substring(i, i + n));
		}
		return out;
	}
}

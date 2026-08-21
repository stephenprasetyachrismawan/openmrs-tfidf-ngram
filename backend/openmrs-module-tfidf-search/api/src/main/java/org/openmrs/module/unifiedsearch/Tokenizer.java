package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The two ways text is cut into terms (docs/algoritma.md sec. 1).
 * <p>
 * {@link #words(String)} splits on whitespace after normalisation. Character
 * n-grams are added in a later task; this class is deliberately just the word
 * path for now.
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
}

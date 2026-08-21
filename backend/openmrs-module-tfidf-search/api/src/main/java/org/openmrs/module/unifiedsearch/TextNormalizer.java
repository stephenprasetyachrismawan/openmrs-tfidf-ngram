package org.openmrs.module.unifiedsearch;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text normalisation shared by every stage of the pipeline.
 * <p>
 * It mirrors {@code norm()} in riset/eksperimen2.py exactly: lower case, every
 * run of characters outside [a-z0-9] becomes a single space, then trim. The Java
 * module must produce the same tokens as the Python experiment, otherwise the
 * two sets of numbers cannot be compared.
 * <p>
 * {@code toLowerCase()} MUST always take {@link Locale#ROOT}. Under a Turkish/
 * Azerbaijani JVM locale, "I".toLowerCase() yields "ı" (dotless i, U+0131),
 * which is outside [a-z0-9] and gets swept away as punctuation — "Insulin"
 * would become "nsulin". Locale.ROOT is locale-independent, so the same input
 * normalizes the same way on every machine. See docs/keputusan.md.
 */
public final class TextNormalizer {
	
	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
	
	private static final Pattern SPACES = Pattern.compile("\\s+");
	
	private TextNormalizer() {
	}
	
	public static String normalize(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String lowered = value.toLowerCase(Locale.ROOT);
		String replaced = NON_ALNUM.matcher(lowered).replaceAll(" ");
		return SPACES.matcher(replaced).replaceAll(" ").trim();
	}
}

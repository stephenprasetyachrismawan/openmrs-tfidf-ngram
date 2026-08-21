package org.openmrs.module.unifiedsearch;

import java.util.regex.Pattern;

/**
 * Text normalisation shared by every stage of the pipeline.
 * <p>
 * It mirrors {@code norm()} in riset/eksperimen2.py exactly: lower case, every
 * run of characters outside [a-z0-9] becomes a single space, then trim. The Java
 * module must produce the same tokens as the Python experiment, otherwise the
 * two sets of numbers cannot be compared.
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
		String lowered = value.toLowerCase();
		String replaced = NON_ALNUM.matcher(lowered).replaceAll(" ");
		return SPACES.matcher(replaced).replaceAll(" ").trim();
	}
}

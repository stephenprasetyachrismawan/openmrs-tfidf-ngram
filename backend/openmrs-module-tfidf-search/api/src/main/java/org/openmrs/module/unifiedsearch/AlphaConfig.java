package org.openmrs.module.unifiedsearch;

import org.openmrs.api.context.Context;

/**
 * ALPHA (word-path weight in K5), read at call time from the OpenMRS global
 * property so it can be adjusted without rebuilding the module.
 * <p>
 * {@link #DEFAULT_ALPHA} was set in tugas 06 from a sweep over the 100 dev
 * queries only (never the 180 test queries — see docs/keputusan.md). It is a
 * default, not a constant baked into the ranking code: every caller that needs
 * ALPHA should go through {@link #current()}.
 */
public final class AlphaConfig {
	
	public static final String GLOBAL_PROPERTY = "unifiedsearch.alpha";
	
	/** Chosen 2026-08-21 from the dev sweep in riset/hasil2/sapuan_alpha_dev.json. */
	public static final double DEFAULT_ALPHA = 0.20;
	
	private AlphaConfig() {
	}
	
	public static double current() {
		return parse(Context.getAdministrationService().getGlobalProperty(GLOBAL_PROPERTY));
	}
	
	static double parse(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return DEFAULT_ALPHA;
		}
		try {
			return Double.parseDouble(raw.trim());
		}
		catch (NumberFormatException e) {
			return DEFAULT_ALPHA;
		}
	}
}

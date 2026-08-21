package org.openmrs.module.unifiedsearch;

/**
 * Pairs a result with the wall-clock time it took to produce, kept out of the result body so
 * that response bodies stay byte-identical across repeated calls (CLAUDE.md rule 1 — the
 * determinism requirement extends to the REST layer, not just ranking order).
 */
public final class Timed<T> {

	private final T body;

	private final long waktuMs;

	public Timed(T body, long waktuMs) {
		this.body = body;
		this.waktuMs = waktuMs;
	}

	public T getBody() {
		return body;
	}

	public long getWaktuMs() {
		return waktuMs;
	}
}

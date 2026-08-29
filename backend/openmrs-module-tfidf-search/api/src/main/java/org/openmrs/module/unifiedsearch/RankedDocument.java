package org.openmrs.module.unifiedsearch;

/**
 * One document with the score it earned for a query. Immutable so a sorted
 * list of these can be handed out without risk of it being mutated later.
 */
public final class RankedDocument {

	private final VirtualDocument dokumen;

	private final double skor;

	/**
	 * True when the winning surface form was the document's title rather than an alias or
	 * code. Only BigramJaccardSuggester sets this (true title-vs-alias tie-break for the
	 * navbar suggestion dropdown); every other caller uses the two-argument constructor and
	 * gets {@code false}, which is inert wherever this field is not read.
	 */
	private final boolean viaJudul;

	public RankedDocument(VirtualDocument dokumen, double skor) {
		this(dokumen, skor, false);
	}

	public RankedDocument(VirtualDocument dokumen, double skor, boolean viaJudul) {
		this.dokumen = dokumen;
		this.skor = skor;
		this.viaJudul = viaJudul;
	}

	public VirtualDocument getDokumen() {
		return dokumen;
	}

	public double getSkor() {
		return skor;
	}

	public boolean isViaJudul() {
		return viaJudul;
	}

	@Override
	public String toString() {
		return dokumen.getKunci() + "=" + skor;
	}
}

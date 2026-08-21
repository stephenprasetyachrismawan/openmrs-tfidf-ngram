package org.openmrs.module.unifiedsearch;

/**
 * One document with the score it earned for a query. Immutable so a sorted
 * list of these can be handed out without risk of it being mutated later.
 */
public final class RankedDocument {
	
	private final VirtualDocument dokumen;
	
	private final double skor;
	
	public RankedDocument(VirtualDocument dokumen, double skor) {
		this.dokumen = dokumen;
		this.skor = skor;
	}
	
	public VirtualDocument getDokumen() {
		return dokumen;
	}
	
	public double getSkor() {
		return skor;
	}
	
	@Override
	public String toString() {
		return dokumen.getKunci() + "=" + skor;
	}
}

package org.openmrs.module.unifiedsearch;

/**
 * One ranked hit with optional K6 trace fields for the REST response shape in
 * docs/arsitektur-halaman.md.
 */
public final class SearchHit {

	private final VirtualDocument dokumen;

	private final double skor;

	private final Double skorAsli;

	private final Integer peringkatDiTabel;

	private final Double bobotTabel;

	public SearchHit(VirtualDocument dokumen, double skor, Double skorAsli, Integer peringkatDiTabel,
	        Double bobotTabel) {
		this.dokumen = dokumen;
		this.skor = skor;
		this.skorAsli = skorAsli;
		this.peringkatDiTabel = peringkatDiTabel;
		this.bobotTabel = bobotTabel;
	}

	public VirtualDocument getDokumen() {
		return dokumen;
	}

	public double getSkor() {
		return skor;
	}

	public Double getSkorAsli() {
		return skorAsli;
	}

	public Integer getPeringkatDiTabel() {
		return peringkatDiTabel;
	}

	public Double getBobotTabel() {
		return bobotTabel;
	}
}

package org.openmrs.module.unifiedsearch;

/**
 * One indexable unit: the title, one alias, or one code of a document. Aliases
 * are never merged into a single string — see docs/kontrak-data.md.
 */
public class SurfaceForm {
	
	private final VirtualDocument dokumen;
	
	private final String teks;
	
	private final boolean judul;
	
	public SurfaceForm(VirtualDocument dokumen, String teks, boolean judul) {
		this.dokumen = dokumen;
		this.teks = teks;
		this.judul = judul;
	}
	
	public VirtualDocument getDokumen() {
		return dokumen;
	}
	
	public String getTeks() {
		return teks;
	}
	
	public boolean isJudul() {
		return judul;
	}
	
	@Override
	public String toString() {
		return dokumen.getKunci() + (judul ? " [judul] " : " [form] ") + teks;
	}
}

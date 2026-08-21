package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document into its surface forms: title, then each alias, then each
 * code — one form each, in that order (component K2).
 * <p>
 * Mirrors {@code bentuk_form()} in riset/eksperimen2.py, including the filter:
 * a form is kept only when its normalised text is non-empty, so a code made of
 * punctuation alone is dropped. {@code konteks} is never a surface form.
 */
public class SurfaceFormExtractor {
	
	public List<SurfaceForm> extract(VirtualDocument dokumen) {
		List<SurfaceForm> forms = new ArrayList<SurfaceForm>();
		tambah(forms, dokumen, dokumen.getJudul(), true);
		for (String alias : dokumen.getAlias()) {
			tambah(forms, dokumen, alias, false);
		}
		for (String kode : dokumen.getKode()) {
			tambah(forms, dokumen, kode, false);
		}
		return forms;
	}
	
	public List<SurfaceForm> extractAll(List<VirtualDocument> dokumen) {
		List<SurfaceForm> forms = new ArrayList<SurfaceForm>();
		for (VirtualDocument d : dokumen) {
			forms.addAll(extract(d));
		}
		return forms;
	}
	
	private void tambah(List<SurfaceForm> forms, VirtualDocument dokumen, String teks, boolean judul) {
		if (teks == null || teks.isEmpty()) {
			return;
		}
		if (TextNormalizer.normalize(teks).isEmpty()) {
			return;
		}
		forms.add(new SurfaceForm(dokumen, teks, judul));
	}
}

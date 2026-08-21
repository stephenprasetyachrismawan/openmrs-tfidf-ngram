package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/** Forms. Title is form.name, the only code is form.version, no aliases. */
public class FormSource extends SqlDocumentSource {
	
	private static final String FORMS = "SELECT f.form_id, f.name, f.version, f.description,"
	        + " (SELECT et.name FROM encounter_type et WHERE et.encounter_type_id = f.encounter_type)"
	        + " FROM form f WHERE f.retired = 0 ORDER BY f.form_id";
	
	@Override
	public String getEntitas() {
		return "form";
	}
	
	@Override
	public List<VirtualDocument> load() {
		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		for (Object[] row : rows(FORMS)) {
			String title = str(row[1]);
			if (title == null || title.isEmpty()) {
				continue;
			}
			List<String> kode = new ArrayList<String>();
			addIfPresent(kode, str(row[2]));
			String konteks = concatWs(" ", str(row[3]), str(row[4]));
			out.add(new VirtualDocument(getEntitas(), integer(row[0]).intValue(), title,
			    new ArrayList<String>(), kode, konteks, null));
		}
		return out;
	}
}

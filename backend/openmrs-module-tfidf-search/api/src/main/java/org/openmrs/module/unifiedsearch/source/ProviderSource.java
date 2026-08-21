package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * Providers. Title is provider.name, falling back to the preferred person name.
 * The only code is provider.identifier.
 */
public class ProviderSource extends SqlDocumentSource {
	
	private static final String PROVIDERS = "SELECT pv.provider_id, pv.name, pv.identifier, pv.uuid,"
	        + " pn.given_name, pn.family_name FROM provider pv"
	        + " LEFT JOIN person_name pn ON pn.person_id = pv.person_id AND pn.voided = 0 AND pn.preferred = 1"
	        + " WHERE pv.retired = 0 ORDER BY pv.provider_id, pn.person_name_id";
	
	@Override
	public String getEntitas() {
		return "provider";
	}
	
	@Override
	public List<VirtualDocument> load() {
		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		Integer previous = null;
		for (Object[] row : rows(PROVIDERS)) {
			Integer providerId = integer(row[0]);
			if (providerId.equals(previous)) {
				continue; // a person with several preferred names would repeat the row
			}
			previous = providerId;
			// COALESCE semantics: the person name is used only when provider.name is NULL
			String title = str(row[1]);
			if (title == null) {
				title = concatWs(" ", str(row[4]), str(row[5])).trim();
			}
			if (title.isEmpty()) {
				continue;
			}
			List<String> kode = new ArrayList<String>();
			addIfPresent(kode, str(row[2]));
			out.add(new VirtualDocument(getEntitas(), providerId.intValue(), title, new ArrayList<String>(), kode,
			    str(row[3]), null));
		}
		return out;
	}
}

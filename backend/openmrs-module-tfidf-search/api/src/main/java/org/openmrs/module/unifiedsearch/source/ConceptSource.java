package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * Concepts. Title is the English fully specified name, falling back to the first
 * English locale-preferred name. Aliases are the English synonyms followed by the
 * English preferred names, minus any that equal the title; duplicates between the
 * two lists are kept, exactly as in riset/eksperimen2.py. Codes come from the
 * concept reference maps. Context is the English description and is not indexed.
 */
public class ConceptSource extends SqlDocumentSource {
	
	private static final String CONCEPTS = "SELECT c.concept_id,"
	        + " (SELECT cd.description FROM concept_description cd WHERE cd.concept_id = c.concept_id"
	        + "  AND cd.locale = 'en' ORDER BY cd.concept_description_id LIMIT 1)"
	        + " FROM concept c WHERE c.retired = 0 ORDER BY c.concept_id";
	
	private static final String NAMES = "SELECT cn.concept_id, cn.name, cn.concept_name_type, cn.locale_preferred"
	        + " FROM concept_name cn WHERE cn.voided = 0 AND cn.locale = 'en'"
	        + " ORDER BY cn.concept_id, cn.concept_name_id";
	
	private static final String CODES = "SELECT crm.concept_id, crt.code FROM concept_reference_map crm"
	        + " JOIN concept_reference_term crt ON crt.concept_reference_term_id = crm.concept_reference_term_id"
	        + " ORDER BY crm.concept_id, crm.concept_map_id";
	
	@Override
	public String getEntitas() {
		return "konsep";
	}
	
	@Override
	public List<VirtualDocument> load() {
		Map<Integer, String> fullySpecified = new LinkedHashMap<Integer, String>();
		Map<Integer, List<String>> preferred = new LinkedHashMap<Integer, List<String>>();
		Map<Integer, List<String>> synonyms = new LinkedHashMap<Integer, List<String>>();
		
		for (Object[] row : rows(NAMES)) {
			Integer conceptId = integer(row[0]);
			String name = str(row[1]);
			String type = str(row[2]);
			boolean localePreferred = flag(row[3]);
			if (name == null || name.isEmpty()) {
				continue;
			}
			if ("FULLY_SPECIFIED".equals(type)) {
				if (!fullySpecified.containsKey(conceptId)) {
					fullySpecified.put(conceptId, name);
				}
			} else {
				bucket(synonyms, conceptId).add(name);
			}
			if (localePreferred) {
				bucket(preferred, conceptId).add(name);
			}
		}
		
		Map<Integer, List<String>> codes = new LinkedHashMap<Integer, List<String>>();
		for (Object[] row : rows(CODES)) {
			addIfPresent(bucket(codes, integer(row[0])), str(row[1]));
		}
		
		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		for (Object[] row : rows(CONCEPTS)) {
			Integer conceptId = integer(row[0]);
			String title = fullySpecified.get(conceptId);
			if (title == null || title.isEmpty()) {
				List<String> pref = preferred.get(conceptId);
				title = (pref == null || pref.isEmpty()) ? null : pref.get(0);
			}
			if (title == null || title.isEmpty()) {
				continue;
			}
			List<String> alias = new ArrayList<String>();
			appendUnlessTitle(alias, synonyms.get(conceptId), title);
			appendUnlessTitle(alias, preferred.get(conceptId), title);
			
			List<String> kode = codes.containsKey(conceptId) ? codes.get(conceptId) : new ArrayList<String>();
			out.add(new VirtualDocument(getEntitas(), conceptId.intValue(), title, alias, kode, str(row[1]), null));
		}
		return out;
	}
	
	private static void appendUnlessTitle(List<String> target, List<String> values, String title) {
		if (values == null) {
			return;
		}
		for (String value : values) {
			if (!title.equals(value)) {
				target.add(value);
			}
		}
	}
}

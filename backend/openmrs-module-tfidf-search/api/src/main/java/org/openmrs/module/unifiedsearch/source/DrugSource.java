package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * Drugs. Title is drug.name; aliases are every English name of the concept the
 * drug points at; codes are that concept's reference terms. Strength and dosage
 * form go to context, which is not indexed.
 */
public class DrugSource extends SqlDocumentSource {
	
	private static final String DRUGS = "SELECT d.drug_id, d.name, d.concept_id, d.strength,"
	        + " (SELECT cn.name FROM concept_name cn WHERE cn.concept_id = d.dosage_form"
	        + "  AND cn.voided = 0 AND cn.locale = 'en' ORDER BY cn.concept_name_id LIMIT 1)"
	        + " FROM drug d WHERE d.retired = 0 ORDER BY d.drug_id";
	
	private static final String NAMES = "SELECT cn.concept_id, cn.name FROM concept_name cn"
	        + " WHERE cn.voided = 0 AND cn.locale = 'en' ORDER BY cn.concept_id, cn.concept_name_id";
	
	private static final String CODES = "SELECT crm.concept_id, crt.code FROM concept_reference_map crm"
	        + " JOIN concept_reference_term crt ON crt.concept_reference_term_id = crm.concept_reference_term_id"
	        + " ORDER BY crm.concept_id, crm.concept_map_id";
	
	@Override
	public String getEntitas() {
		return "obat";
	}
	
	@Override
	public List<VirtualDocument> load() {
		Map<Integer, List<String>> names = new LinkedHashMap<Integer, List<String>>();
		for (Object[] row : rows(NAMES)) {
			addIfPresent(bucket(names, integer(row[0])), str(row[1]));
		}
		Map<Integer, List<String>> codes = new LinkedHashMap<Integer, List<String>>();
		for (Object[] row : rows(CODES)) {
			addIfPresent(bucket(codes, integer(row[0])), str(row[1]));
		}
		
		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		for (Object[] row : rows(DRUGS)) {
			String title = str(row[1]);
			if (title == null || title.isEmpty()) {
				continue;
			}
			Integer conceptId = integer(row[2]);
			List<String> alias = names.containsKey(conceptId) ? names.get(conceptId) : new ArrayList<String>();
			List<String> kode = codes.containsKey(conceptId) ? codes.get(conceptId) : new ArrayList<String>();
			String konteks = concatWs(" ", str(row[3]), str(row[4]));
			out.add(new VirtualDocument(getEntitas(), integer(row[0]).intValue(), title, alias, kode, konteks, conceptId));
		}
		return out;
	}
}

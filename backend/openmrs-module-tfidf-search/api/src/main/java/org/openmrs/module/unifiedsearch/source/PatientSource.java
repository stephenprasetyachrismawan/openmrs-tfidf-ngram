package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * Patients. Title is the preferred name (given, middle, family); aliases are the
 * non-preferred names (given, family); codes are the patient identifiers.
 * <p>
 * The index is built over every patient. Filtering by the calling user's
 * privileges happens later, when results are returned — see CLAUDE.md rule 5.
 */
public class PatientSource extends SqlDocumentSource {
	
	private static final String PATIENTS = "SELECT p.patient_id, pr.gender, YEAR(pr.birthdate)"
	        + " FROM patient p JOIN person pr ON pr.person_id = p.patient_id"
	        + " WHERE p.voided = 0 ORDER BY p.patient_id";
	
	private static final String NAMES = "SELECT pn.person_id, pn.given_name, pn.middle_name, pn.family_name,"
	        + " pn.preferred FROM person_name pn WHERE pn.voided = 0 ORDER BY pn.person_id, pn.person_name_id";
	
	private static final String IDENTIFIERS = "SELECT pi.patient_id, pi.identifier FROM patient_identifier pi"
	        + " WHERE pi.voided = 0 ORDER BY pi.patient_id, pi.patient_identifier_id";
	
	@Override
	public String getEntitas() {
		return "pasien";
	}
	
	@Override
	public List<VirtualDocument> load() {
		Map<Integer, String> preferredName = new LinkedHashMap<Integer, String>();
		Map<Integer, List<String>> otherNames = new LinkedHashMap<Integer, List<String>>();
		for (Object[] row : rows(NAMES)) {
			Integer personId = integer(row[0]);
			boolean preferred = flag(row[4]);
			if (preferred) {
				if (!preferredName.containsKey(personId)) {
					preferredName.put(personId, concatWs(" ", str(row[1]), str(row[2]), str(row[3])).trim());
				}
			} else {
				addIfPresent(bucket(otherNames, personId), concatWs(" ", str(row[1]), str(row[3])).trim());
			}
		}
		
		Map<Integer, List<String>> identifiers = new LinkedHashMap<Integer, List<String>>();
		for (Object[] row : rows(IDENTIFIERS)) {
			addIfPresent(bucket(identifiers, integer(row[0])), str(row[1]));
		}
		
		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		for (Object[] row : rows(PATIENTS)) {
			Integer patientId = integer(row[0]);
			String title = preferredName.get(patientId);
			if (title == null || title.isEmpty()) {
				continue;
			}
			List<String> alias = otherNames.containsKey(patientId) ? otherNames.get(patientId)
			        : new ArrayList<String>();
			List<String> kode = identifiers.containsKey(patientId) ? identifiers.get(patientId)
			        : new ArrayList<String>();
			String konteks = concatWs(" ", str(row[1]), str(row[2]));
			out.add(new VirtualDocument(getEntitas(), patientId.intValue(), title, alias, kode, konteks, null));
		}
		return out;
	}
}

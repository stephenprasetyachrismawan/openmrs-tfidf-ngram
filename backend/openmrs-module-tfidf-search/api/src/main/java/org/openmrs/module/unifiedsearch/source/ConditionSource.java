package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * Patient conditions (table {@code conditions}, the problem list behind the
 * O3 "Conditions" widget). Like "hasillab", this is not part of the original
 * six-entity research corpus -- riset/eksperimen2.py has no equivalent -- so
 * there is no B0/B1/E1/E3 baseline number for it, only for the original six.
 * See docs/kontrak-data.md.
 * <p>
 * Title is the coded condition's concept name, so searching "faild abortion"
 * still reaches the patients whose problem list holds "Failed abortion" --
 * the same benefit K4 gives every other entity. Rows without a coded concept
 * fall back to {@code condition_non_coded}; note the demo data also fills
 * that column with the placeholder text "Some non-coded condition" on rows
 * that ARE coded, so it is only read when {@code condition_coded} is null.
 * <p>
 * Alias is the patient's full name, so searching the patient also surfaces
 * their conditions (K5 takes the max over surface forms, so title text and
 * alias text each score independently). Clinical status and onset date go to
 * context, not indexed -- indexing "ACTIVE" on 1200+ rows would pollute the
 * IDF exactly the way "tablet" would.
 */
public class ConditionSource extends SqlDocumentSource {

	private static final String CONDITIONS = "SELECT c.condition_id, c.patient_id, c.condition_coded,"
	        + " c.condition_non_coded, c.clinical_status, c.onset_date"
	        + " FROM conditions c WHERE c.voided = 0 ORDER BY c.condition_id";

	private static final String CONCEPT_NAMES = "SELECT cn.concept_id, cn.name FROM concept_name cn"
	        + " WHERE cn.voided = 0 AND cn.locale = 'en' AND cn.concept_name_type = 'FULLY_SPECIFIED'"
	        + " ORDER BY cn.concept_id, cn.concept_name_id";

	private static final String PATIENT_NAMES = "SELECT pn.person_id, pn.given_name, pn.middle_name, pn.family_name"
	        + " FROM person_name pn WHERE pn.voided = 0 AND pn.preferred = 1";

	@Override
	public String getEntitas() {
		return "kondisi";
	}

	@Override
	public List<VirtualDocument> load() {
		Map<Integer, String> namaKonsep = new LinkedHashMap<Integer, String>();
		for (Object[] row : rows(CONCEPT_NAMES)) {
			Integer conceptId = integer(row[0]);
			if (!namaKonsep.containsKey(conceptId)) {
				namaKonsep.put(conceptId, str(row[1]));
			}
		}

		Map<Integer, String> namaPasien = new LinkedHashMap<Integer, String>();
		for (Object[] row : rows(PATIENT_NAMES)) {
			Integer personId = integer(row[0]);
			if (!namaPasien.containsKey(personId)) {
				namaPasien.put(personId, concatWs(" ", str(row[1]), str(row[2]), str(row[3])).trim());
			}
		}

		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		for (Object[] row : rows(CONDITIONS)) {
			Integer conditionId = integer(row[0]);
			Integer patientId = integer(row[1]);
			Integer conceptId = integer(row[2]);
			String title = conceptId != null ? namaKonsep.get(conceptId) : str(row[3]);
			if (title == null || title.isEmpty()) {
				continue;
			}
			String pasien = namaPasien.get(patientId);
			List<String> alias = new ArrayList<String>();
			addIfPresent(alias, pasien);

			String status = str(row[4]);
			String onset = str(row[5]);
			String konteks = concatWs(" · ", pasien == null ? null : "Pasien: " + pasien,
			    status == null ? null : "Status: " + status, onset == null ? null : "Onset: " + onset);

			out.add(new VirtualDocument(getEntitas(), conditionId.intValue(), title, alias, new ArrayList<String>(),
			        konteks, conceptId, patientId));
		}
		return out;
	}
}

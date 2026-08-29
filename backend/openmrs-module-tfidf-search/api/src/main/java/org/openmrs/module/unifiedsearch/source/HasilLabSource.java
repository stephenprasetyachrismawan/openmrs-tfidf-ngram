package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * Lab results (obs rows whose concept is classed Test or LabSet -- verified
 * against the running demo data, not guessed: those two classes are exactly
 * the numeric/coded lab values, e.g. "Serum potassium" = 3.9). Not part of
 * the original six-entity research corpus (riset/eksperimen2.py has no
 * equivalent) -- added later at the repo owner's explicit request, so there
 * is no B0/B1/E1/E3 baseline comparison for this entity, only for the
 * original six. See docs/kontrak-data.md.
 * <p>
 * Title is the test concept's name, so a typo like "kalsium serum" still
 * finds "Serum calcium" -- the same benefit K4 gives every other entity.
 * Alias is the patient's full name, so searching the patient also surfaces
 * their lab results (a document is one entity: title text OR alias text can
 * each independently score the highest, per K5's max-over-surface-forms
 * rule). Value and date go to context, not indexed -- same reasoning as
 * every other source's context field (it would pollute the IDF).
 */
public class HasilLabSource extends SqlDocumentSource {

	private static final String OBS = "SELECT o.obs_id, o.person_id, o.concept_id, o.value_numeric, o.value_text,"
	        + " o.value_coded, o.obs_datetime"
	        + " FROM obs o"
	        + " JOIN concept c ON c.concept_id = o.concept_id"
	        + " JOIN concept_class cc ON cc.concept_class_id = c.class_id"
	        + " WHERE o.voided = 0 AND cc.name IN ('Test', 'LabSet')"
	        + " ORDER BY o.obs_id";

	private static final String CONCEPT_NAMES = "SELECT cn.concept_id, cn.name FROM concept_name cn"
	        + " WHERE cn.voided = 0 AND cn.locale = 'en' AND cn.concept_name_type = 'FULLY_SPECIFIED'"
	        + " ORDER BY cn.concept_id, cn.concept_name_id";

	private static final String PATIENT_NAMES = "SELECT pn.person_id, pn.given_name, pn.middle_name, pn.family_name"
	        + " FROM person_name pn WHERE pn.voided = 0 AND pn.preferred = 1";

	@Override
	public String getEntitas() {
		return "hasillab";
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
		for (Object[] row : rows(OBS)) {
			Integer obsId = integer(row[0]);
			Integer personId = integer(row[1]);
			Integer conceptId = integer(row[2]);
			String title = namaKonsep.get(conceptId);
			if (title == null || title.isEmpty()) {
				continue;
			}
			String pasien = namaPasien.get(personId);
			List<String> alias = new ArrayList<String>();
			addIfPresent(alias, pasien);

			String nilai = nilaiTampil(row[3], row[4], row[5], namaKonsep);
			String konteks = concatWs(" · ", pasien == null ? null : "Pasien: " + pasien,
			    nilai == null ? null : "Nilai: " + nilai, str(row[6]));

			out.add(new VirtualDocument(getEntitas(), obsId.intValue(), title, alias, new ArrayList<String>(), konteks,
			        conceptId, personId));
		}
		return out;
	}

	private static String nilaiTampil(Object valueNumeric, Object valueText, Object valueCoded,
	        Map<Integer, String> namaKonsep) {
		if (valueNumeric != null) {
			return String.valueOf(valueNumeric);
		}
		if (valueText != null) {
			return str(valueText);
		}
		if (valueCoded != null) {
			String nama = namaKonsep.get(integer(valueCoded));
			return nama != null ? nama : str(valueCoded);
		}
		return null;
	}
}

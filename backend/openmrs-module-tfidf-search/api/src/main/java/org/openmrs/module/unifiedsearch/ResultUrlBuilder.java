package org.openmrs.module.unifiedsearch;

/**
 * Deep links for each entity type in the legacy UI. Patients get an O3 link
 * instead -- see UnifiedSearchService.buildUrl -- because that is the only
 * entity O3 has a real page for; the "pasien" case here is only a fallback
 * for the rare case that lookup fails (voided/missing patient).
 * <p>
 * Every path below was verified with a real authenticated request against
 * the running platform 2.8.8 (curl with a session cookie, not guessed from
 * memory/older-version docs) -- three were wrong and returned 404: obat,
 * form, and provider. Correct paths found by loading the matching admin
 * list page and reading its actual per-row links.
 */
public final class ResultUrlBuilder {

	private ResultUrlBuilder() {
	}

	public static String url(VirtualDocument d) {
		String entitas = d.getEntitas();
		if ("konsep".equals(entitas)) {
			return "/openmrs/dictionary/concept.htm?conceptId=" + d.getId();
		}
		if ("obat".equals(entitas)) {
			return "/openmrs/admin/concepts/conceptDrug.form?drugId=" + d.getId();
		}
		if ("pasien".equals(entitas)) {
			return "/openmrs/patientDashboard.form?patientId=" + d.getId();
		}
		if ("form".equals(entitas)) {
			return "/openmrs/admin/forms/formEdit.form?formId=" + d.getId();
		}
		if ("lokasi".equals(entitas)) {
			return "/openmrs/admin/locations/location.form?locationId=" + d.getId();
		}
		if ("provider".equals(entitas)) {
			return "/openmrs/admin/provider/provider.form?providerId=" + d.getId();
		}
		return "/openmrs/";
	}
}

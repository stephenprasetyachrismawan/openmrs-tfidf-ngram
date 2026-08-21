package org.openmrs.module.unifiedsearch;

/**
 * Deep links for each entity type in the legacy UI.
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
			return "/openmrs/admin/drug/drug.form?drugId=" + d.getId();
		}
		if ("pasien".equals(entitas)) {
			return "/openmrs/patientDashboard.form?patientId=" + d.getId();
		}
		if ("form".equals(entitas)) {
			return "/openmrs/admin/forms/form.form?formId=" + d.getId();
		}
		if ("lokasi".equals(entitas)) {
			return "/openmrs/admin/locations/location.form?locationId=" + d.getId();
		}
		if ("provider".equals(entitas)) {
			return "/openmrs/admin/providers/provider.form?providerId=" + d.getId();
		}
		return "/openmrs/";
	}
}

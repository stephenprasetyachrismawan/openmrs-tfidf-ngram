package org.openmrs.module.unifiedsearch.extension.html;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openmrs.module.Extension;
import org.openmrs.module.web.extension.AdministrationSectionExt;

/**
 * Adds the module's entry to the legacy administration page.
 */
public class AdminList extends AdministrationSectionExt {
	
	@Override
	public Extension.MEDIA_TYPE getMediaType() {
		return Extension.MEDIA_TYPE.html;
	}
	
	@Override
	public String getTitle() {
		return "unifiedsearch.title";
	}
	
	@Override
	public String getRequiredPrivilege() {
		return "";
	}
	
	@Override
	public Map<String, String> getLinks() {
		Map<String, String> links = new LinkedHashMap<String, String>();
		links.put("/module/unifiedsearch/pencarianTerpadu.form", "unifiedsearch.menu.label");
		return links;
	}
}

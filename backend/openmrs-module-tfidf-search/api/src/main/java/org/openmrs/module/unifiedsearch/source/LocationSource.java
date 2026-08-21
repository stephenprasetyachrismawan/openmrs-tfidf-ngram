package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/** Locations. Title is location.name, aliases are its tag names, no codes. */
public class LocationSource extends SqlDocumentSource {
	
	private static final String LOCATIONS = "SELECT l.location_id, l.name, l.description, l.city_village,"
	        + " l.state_province FROM location l WHERE l.retired = 0 ORDER BY l.location_id";
	
	private static final String TAGS = "SELECT ltm.location_id, lt.name FROM location_tag_map ltm"
	        + " JOIN location_tag lt ON lt.location_tag_id = ltm.location_tag_id"
	        + " ORDER BY ltm.location_id, lt.location_tag_id";
	
	@Override
	public String getEntitas() {
		return "lokasi";
	}
	
	@Override
	public List<VirtualDocument> load() {
		Map<Integer, List<String>> tags = new LinkedHashMap<Integer, List<String>>();
		for (Object[] row : rows(TAGS)) {
			addIfPresent(bucket(tags, integer(row[0])), str(row[1]));
		}
		
		List<VirtualDocument> out = new ArrayList<VirtualDocument>();
		for (Object[] row : rows(LOCATIONS)) {
			Integer locationId = integer(row[0]);
			String title = str(row[1]);
			if (title == null || title.isEmpty()) {
				continue;
			}
			List<String> alias = tags.containsKey(locationId) ? tags.get(locationId) : new ArrayList<String>();
			String konteks = concatWs(" ", str(row[2]), str(row[3]), str(row[4]));
			out.add(new VirtualDocument(getEntitas(), locationId.intValue(), title, alias,
			    new ArrayList<String>(), konteks, null));
		}
		return out;
	}
}

package org.openmrs.module.unifiedsearch.source;

import java.util.List;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/**
 * One source table projected onto virtual documents (component K1).
 * Implementations load in bulk — never row by row.
 */
public interface DocumentSource {
	
	/** The entity name this source produces, e.g. "konsep". */
	String getEntitas();
	
	List<VirtualDocument> load();
}

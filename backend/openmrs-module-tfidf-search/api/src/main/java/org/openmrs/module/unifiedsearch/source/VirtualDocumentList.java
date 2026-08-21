package org.openmrs.module.unifiedsearch.source;

import java.util.Collections;
import java.util.List;

import org.openmrs.module.unifiedsearch.VirtualDocument;

/** The documents produced by one source, kept together with their entity name. */
public class VirtualDocumentList {
	
	private final String entitas;
	
	private final List<VirtualDocument> dokumen;
	
	public VirtualDocumentList(String entitas, List<VirtualDocument> dokumen) {
		this.entitas = entitas;
		this.dokumen = Collections.unmodifiableList(dokumen);
	}
	
	public String getEntitas() {
		return entitas;
	}
	
	public List<VirtualDocument> getDokumen() {
		return dokumen;
	}
}

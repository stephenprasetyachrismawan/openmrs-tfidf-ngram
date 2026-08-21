package org.openmrs.module.unifiedsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of any of the six source tables, projected onto the single shape
 * described in docs/kontrak-data.md (component K1). Field names follow that
 * contract; after this projection the ranking engine no longer knows which
 * table a document came from.
 */
public class VirtualDocument {
	
	private final String entitas;
	
	private final int id;
	
	private final String judul;
	
	private final List<String> alias;
	
	private final List<String> kode;
	
	private final String konteks;
	
	private final Integer tautanKonsep;
	
	public VirtualDocument(String entitas, int id, String judul, List<String> alias, List<String> kode, String konteks,
	    Integer tautanKonsep) {
		this.entitas = entitas;
		this.id = id;
		this.judul = judul;
		this.alias = Collections.unmodifiableList(new ArrayList<String>(alias == null ? Collections
		        .<String> emptyList() : alias));
		this.kode = Collections.unmodifiableList(new ArrayList<String>(kode == null ? Collections.<String> emptyList()
		        : kode));
		this.konteks = konteks == null ? "" : konteks;
		this.tautanKonsep = tautanKonsep;
	}
	
	public String getEntitas() {
		return entitas;
	}
	
	public int getId() {
		return id;
	}
	
	public String getJudul() {
		return judul;
	}
	
	public List<String> getAlias() {
		return alias;
	}
	
	public List<String> getKode() {
		return kode;
	}
	
	/** Display only. Never indexed: it would pollute the IDF with words like "tablet". */
	public String getKonteks() {
		return konteks;
	}
	
	public Integer getTautanKonsep() {
		return tautanKonsep;
	}
	
	/**
	 * Cross-table unique key, "entitas:id" (for example "konsep:5497"). Plain id
	 * collides between tables, and this key is also the sort tie-break.
	 */
	public String getKunci() {
		return entitas + ":" + id;
	}
	
	@Override
	public String toString() {
		return getKunci() + " " + judul;
	}
}

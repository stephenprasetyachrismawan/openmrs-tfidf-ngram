package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads all sources and returns the whole corpus of virtual documents.
 * <p>
 * The result is sorted by entity (in the fixed order used throughout the study)
 * and then by id, so two runs in two processes produce the same list — see
 * CLAUDE.md rule 1.
 */
@Component("unifiedsearch.documentRepository")
public class DocumentRepository {

	/**
	 * Fixed entity order. The first six match ENT in riset/eksperimen2.py exactly
	 * -- that is the corpus every B0/B1/E1/E3 number in docs/ was measured on.
	 * "hasillab" and "kondisi" were added later at the repo owner's explicit
	 * request and have no Python counterpart or baseline comparison; they are
	 * last on purpose so the first six keep their original relative order.
	 */
	public static final List<String> ENTITAS = Collections.unmodifiableList(Arrays.asList("konsep", "obat", "pasien",
	    "form", "lokasi", "provider", "hasillab", "kondisi"));

	private final List<DocumentSource> sources = new ArrayList<DocumentSource>();

	@Autowired
	public DocumentRepository(DbSessionFactory sessionFactory) {
		SqlDocumentSource[] all = new SqlDocumentSource[] { new ConceptSource(), new DrugSource(), new PatientSource(),
		        new FormSource(), new LocationSource(), new ProviderSource(), new HasilLabSource(),
		        new ConditionSource() };
		for (SqlDocumentSource source : all) {
			source.setSessionFactory(sessionFactory);
			sources.add(source);
		}
	}
	
	public List<DocumentSource> getSources() {
		return Collections.unmodifiableList(sources);
	}
	
	@Transactional(readOnly = true)
	public List<VirtualDocumentList> loadPerEntitas() {
		List<VirtualDocumentList> out = new ArrayList<VirtualDocumentList>();
		for (String entitas : ENTITAS) {
			for (DocumentSource source : sources) {
				if (source.getEntitas().equals(entitas)) {
					out.add(new VirtualDocumentList(entitas, source.load()));
				}
			}
		}
		return out;
	}
	
	@Transactional(readOnly = true)
	public List<org.openmrs.module.unifiedsearch.VirtualDocument> loadAll() {
		List<org.openmrs.module.unifiedsearch.VirtualDocument> out = new ArrayList<org.openmrs.module.unifiedsearch.VirtualDocument>();
		for (VirtualDocumentList list : loadPerEntitas()) {
			out.addAll(list.getDokumen());
		}
		Collections.sort(out, new Comparator<org.openmrs.module.unifiedsearch.VirtualDocument>() {
			
			@Override
			public int compare(org.openmrs.module.unifiedsearch.VirtualDocument a,
			        org.openmrs.module.unifiedsearch.VirtualDocument b) {
				int byEntitas = ENTITAS.indexOf(a.getEntitas()) - ENTITAS.indexOf(b.getEntitas());
				return byEntitas != 0 ? byEntitas : Integer.compare(a.getId(), b.getId());
			}
		});
		return out;
	}
}

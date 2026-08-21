package org.openmrs.module.unifiedsearch.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.api.context.Context;
import org.openmrs.module.unifiedsearch.SurfaceForm;
import org.openmrs.module.unifiedsearch.SurfaceFormExtractor;
import org.openmrs.module.unifiedsearch.VirtualDocument;
import org.openmrs.module.unifiedsearch.source.DocumentRepository;
import org.openmrs.module.unifiedsearch.source.VirtualDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Placeholder page. Until the search UI exists it reports the size of the
 * corpus, which is what the K1/K2 acceptance criteria are stated in.
 */
@Controller
public class UnifiedSearchPageController {
	
	private final DocumentRepository repository;
	
	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();
	
	@Autowired
	public UnifiedSearchPageController(DocumentRepository repository) {
		this.repository = repository;
	}
	
	@RequestMapping("/module/unifiedsearch/pencarianTerpadu.form")
	public String showPage(ModelMap model) {
		// The corpus is built from every row, including patients, so the page is
		// never rendered for an anonymous caller. See CLAUDE.md rule 5.
		if (!Context.isAuthenticated()) {
			return "redirect:/login.htm";
		}
		List<VirtualDocumentList> perEntitas = repository.loadPerEntitas();
		
		Map<String, Integer> dokumenPerEntitas = new LinkedHashMap<String, Integer>();
		Map<String, Integer> formPerEntitas = new LinkedHashMap<String, Integer>();
		int totalDokumen = 0;
		int totalForm = 0;
		for (VirtualDocumentList list : perEntitas) {
			List<SurfaceForm> forms = extractor.extractAll(list.getDokumen());
			dokumenPerEntitas.put(list.getEntitas(), Integer.valueOf(list.getDokumen().size()));
			formPerEntitas.put(list.getEntitas(), Integer.valueOf(forms.size()));
			totalDokumen += list.getDokumen().size();
			totalForm += forms.size();
		}
		
		model.addAttribute("dokumenPerEntitas", dokumenPerEntitas);
		model.addAttribute("formPerEntitas", formPerEntitas);
		model.addAttribute("totalDokumen", Integer.valueOf(totalDokumen));
		model.addAttribute("totalForm", Integer.valueOf(totalForm));
		model.addAttribute("contoh", contohAcetaminophen(perEntitas));
		return "/module/unifiedsearch/pencarianTerpadu";
	}
	
	/**
	 * Acetaminophen is the worked example in docs/kontrak-data.md: it is the
	 * concept whose alias count motivates keeping surface forms separate.
	 */
	private List<String> contohAcetaminophen(List<VirtualDocumentList> perEntitas) {
		List<String> out = new ArrayList<String>();
		for (VirtualDocumentList list : perEntitas) {
			if (!"konsep".equals(list.getEntitas())) {
				continue;
			}
			for (VirtualDocument dokumen : list.getDokumen()) {
				if ("Acetaminophen".equals(dokumen.getJudul())) {
					out.add(dokumen.getKunci() + " = " + extractor.extract(dokumen).size() + " surface form");
				}
			}
		}
		return out;
	}
}

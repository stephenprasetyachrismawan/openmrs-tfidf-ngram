package org.openmrs.module.unifiedsearch.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.api.context.Context;
import org.openmrs.module.unifiedsearch.SurfaceForm;
import org.openmrs.module.unifiedsearch.SurfaceFormExtractor;
import org.openmrs.module.unifiedsearch.TfIdfIndex;
import org.openmrs.module.unifiedsearch.TokenFunction;
import org.openmrs.module.unifiedsearch.Tokenizer;
import org.openmrs.module.unifiedsearch.VirtualDocument;
import org.openmrs.module.unifiedsearch.source.DocumentRepository;
import org.openmrs.module.unifiedsearch.source.VirtualDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Placeholder page. Until the search UI exists it reports the size of the
 * corpus (K1/K2) and, since tugas 04, a live check of the character n-gram
 * index (K4): build time for the whole corpus and the score of the two worked
 * examples from tugas/04-kepingan-karakter.md.
 */
@Controller
public class UnifiedSearchPageController {
	
	private static final int NGRAM = 4;

	private final DocumentRepository repository;

	private final SurfaceFormExtractor extractor = new SurfaceFormExtractor();

	private final TokenFunction gramTokenizer = new TokenFunction() {

		@Override
		public List<String> tokenize(String s) {
			return Tokenizer.charGrams(s, NGRAM);
		}
	};
	
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
		List<SurfaceForm> formKonsep = new ArrayList<SurfaceForm>();
		TfIdfIndex indeksKepinganKonsep = null;
		long mulaiBangunKepingan = System.nanoTime();
		int totalKepinganVocab = 0;
		for (VirtualDocumentList list : perEntitas) {
			List<SurfaceForm> forms = extractor.extractAll(list.getDokumen());
			dokumenPerEntitas.put(list.getEntitas(), Integer.valueOf(list.getDokumen().size()));
			formPerEntitas.put(list.getEntitas(), Integer.valueOf(forms.size()));
			totalDokumen += list.getDokumen().size();
			totalForm += forms.size();

			TfIdfIndex indeksKepingan = new TfIdfIndex(gramTokenizer);
			indeksKepingan.build(teks(forms));
			totalKepinganVocab += indeksKepingan.vocabularySize();
			if ("konsep".equals(list.getEntitas())) {
				formKonsep = forms;
				indeksKepinganKonsep = indeksKepingan;
			}
		}
		long durasiBangunKepinganMs = (System.nanoTime() - mulaiBangunKepingan) / 1000000L;

		model.addAttribute("dokumenPerEntitas", dokumenPerEntitas);
		model.addAttribute("formPerEntitas", formPerEntitas);
		model.addAttribute("totalDokumen", Integer.valueOf(totalDokumen));
		model.addAttribute("totalForm", Integer.valueOf(totalForm));
		model.addAttribute("contoh", contohAcetaminophen(perEntitas));
		model.addAttribute("durasiBangunKepinganMs", Long.valueOf(durasiBangunKepinganMs));
		model.addAttribute("totalKepinganVocab", Integer.valueOf(totalKepinganVocab));
		model.addAttribute("contohKepingan", contohKepingan(indeksKepinganKonsep, formKonsep));
		return "/module/unifiedsearch/pencarianTerpadu";
	}

	/**
	 * The two worked examples from tugas/04-kepingan-karakter.md, scored against
	 * the character n-gram index over every "konsep" surface form. Score is the
	 * best surface form per document, as required by docs/kontrak-data.md.
	 */
	private List<String> contohKepingan(TfIdfIndex indeks, List<SurfaceForm> formKonsep) {
		List<String> out = new ArrayList<String>();
		if (indeks == null || formKonsep.isEmpty()) {
			return out;
		}
		out.add(cariSkorTerbaik(indeks, formKonsep, "pulm edem", "Pulmonary edema"));
		out.add(cariSkorTerbaik(indeks, formKonsep, "diabete melitus", "Diabetes mellitus, type 2"));
		return out;
	}

	private String cariSkorTerbaik(TfIdfIndex indeks, List<SurfaceForm> formKonsep, String query, String judulTarget) {
		double[] skor = indeks.search(query);
		Map<String, Double> skorPerDokumen = new LinkedHashMap<String, Double>();
		for (int i = 0; i < formKonsep.size(); i++) {
			String kunci = formKonsep.get(i).getDokumen().getKunci();
			Double sebelumnya = skorPerDokumen.get(kunci);
			if (sebelumnya == null || skor[i] > sebelumnya.doubleValue()) {
				skorPerDokumen.put(kunci, Double.valueOf(skor[i]));
			}
		}
		for (int i = 0; i < formKonsep.size(); i++) {
			if (judulTarget.equals(formKonsep.get(i).getDokumen().getJudul())) {
				String kunci = formKonsep.get(i).getDokumen().getKunci();
				return "\"" + query + "\" -> " + judulTarget + " (" + kunci + ") = "
				        + skorPerDokumen.get(kunci);
			}
		}
		return "\"" + query + "\" -> " + judulTarget + " tidak ditemukan di korpus";
	}

	private static List<String> teks(List<SurfaceForm> forms) {
		List<String> out = new ArrayList<String>(forms.size());
		for (SurfaceForm form : forms) {
			out.add(form.getTeks());
		}
		return out;
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

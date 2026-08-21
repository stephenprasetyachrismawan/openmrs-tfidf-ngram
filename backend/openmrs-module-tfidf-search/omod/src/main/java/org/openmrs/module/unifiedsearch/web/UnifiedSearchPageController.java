package org.openmrs.module.unifiedsearch.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openmrs.api.context.Context;
import org.openmrs.module.unifiedsearch.AlphaConfig;
import org.openmrs.module.unifiedsearch.FusionSearch;
import org.openmrs.module.unifiedsearch.GlobalIndex;
import org.openmrs.module.unifiedsearch.RankedDocument;
import org.openmrs.module.unifiedsearch.RankingEngine;
import org.openmrs.module.unifiedsearch.SearchHit;
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
 * corpus (K1/K2), a live check of the character n-gram index (K4), a live
 * check of the K5 fusion (tugas 05/06), and since tugas 07, a live check of
 * the K6 global index / Weighted RRF.
 */
@Controller
public class UnifiedSearchPageController {

	private static final int NGRAM = 4;

	/** Fixed at the value tools/silang_fusi.py's reference table was computed with — not the operational ALPHA. */
	private static final double ALPHA_ACUAN_VERIFIKASI = 0.45;

	/** dikunci tugas 06b dari sapuan 100 query dev; lihat docs/keputusan.md. */
	private static final double EPS = 0.05;

	private static final int K_RRF = 20;

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
		TfIdfIndex indeksKataKonsep = null;
		Map<String, FusionSearch> lokal = new TreeMap<String, FusionSearch>();
		List<SurfaceForm> semuaForm = new ArrayList<SurfaceForm>();
		long mulaiBangunKepingan = System.nanoTime();
		int totalKepinganVocab = 0;
		for (VirtualDocumentList list : perEntitas) {
			List<SurfaceForm> forms = extractor.extractAll(list.getDokumen());
			dokumenPerEntitas.put(list.getEntitas(), Integer.valueOf(list.getDokumen().size()));
			formPerEntitas.put(list.getEntitas(), Integer.valueOf(forms.size()));
			totalDokumen += list.getDokumen().size();
			totalForm += forms.size();
			semuaForm.addAll(forms);

			TfIdfIndex indeksKepingan = new TfIdfIndex(gramTokenizer);
			indeksKepingan.build(teks(forms));
			totalKepinganVocab += indeksKepingan.vocabularySize();

			TfIdfIndex indeksKata = new TfIdfIndex(Tokenizer::words);
			indeksKata.build(teks(forms));
			lokal.put(list.getEntitas(), new FusionSearch(indeksKata, indeksKepingan, forms));

			if ("konsep".equals(list.getEntitas())) {
				formKonsep = forms;
				indeksKepinganKonsep = indeksKepingan;
				indeksKataKonsep = indeksKata;
			}
		}
		long durasiBangunKepinganMs = (System.nanoTime() - mulaiBangunKepingan) / 1000000L;

		TfIdfIndex globalKata = new TfIdfIndex(Tokenizer::words);
		globalKata.build(teks(semuaForm));
		TfIdfIndex globalKepingan = new TfIdfIndex(gramTokenizer);
		globalKepingan.build(teks(semuaForm));
		GlobalIndex global = new GlobalIndex(globalKata, globalKepingan, semuaForm);
		RankingEngine engine = new RankingEngine(lokal, global, EPS, K_RRF);

		model.addAttribute("dokumenPerEntitas", dokumenPerEntitas);
		model.addAttribute("formPerEntitas", formPerEntitas);
		model.addAttribute("totalDokumen", Integer.valueOf(totalDokumen));
		model.addAttribute("totalForm", Integer.valueOf(totalForm));
		model.addAttribute("contoh", contohAcetaminophen(perEntitas));
		model.addAttribute("durasiBangunKepinganMs", Long.valueOf(durasiBangunKepinganMs));
		model.addAttribute("totalKepinganVocab", Integer.valueOf(totalKepinganVocab));
		model.addAttribute("contohKepingan", contohKepingan(indeksKepinganKonsep, formKonsep));
		model.addAttribute("contohFusi", contohFusi(indeksKataKonsep, indeksKepinganKonsep, formKonsep));
		model.addAttribute("contohRrf", contohRrf(global, engine));
		return "/module/unifiedsearch/pencarianTerpadu";
	}

	/**
	 * K6 worked example from tugas/07-weighted-rrf.md: collection weights for
	 * query "diabete" on the real corpus (the 0,38/0,12 figures in the task were
	 * from the docs/proposal.html mockup — only the relative order is expected to
	 * match on the real corpus, not the exact numbers).
	 * <p>
	 * "diabete" itself does not reorder e1 vs e3 on THIS corpus: the demo data has
	 * zero non-concept rows matching "diabet*" (checked directly against the
	 * database), so only one entity ever has candidates and RRF has nothing to
	 * reweight against. "form" is shown instead to demonstrate e1/e3 diverging on
	 * real data (confirmed first against the Python reference pipeline). RRF is
	 * architectural — it does not claim to rank better than e1, only to merge six
	 * lists into one; see CLAUDE.md rule 3.
	 */
	private List<String> contohRrf(GlobalIndex global, RankingEngine engine) {
		List<String> out = new ArrayList<String>();
		double alpha = AlphaConfig.current();

		Map<String, Double> bobot = global.collectionWeights("diabete", alpha, EPS);
		out.add("bobot koleksi (K6) untuk \"diabete\":");
		for (Map.Entry<String, Double> entry : bobot.entrySet()) {
			out.add("  " + entry.getKey() + " = " + entry.getValue());
		}

		String queryBeda = "form";
		out.add("--- E1 (K5 saja, tanpa RRF) top 3 untuk \"" + queryBeda + "\" ---");
		tambahTop3(out, engine.search("e1", queryBeda, alpha));
		out.add("--- E3 (Weighted RRF) top 3 untuk \"" + queryBeda + "\" ---");
		tambahTop3(out, engine.search("e3", queryBeda, alpha));

		return out;
	}

	private void tambahTop3(List<String> out, List<SearchHit> hasil) {
		for (int i = 0; i < Math.min(3, hasil.size()); i++) {
			SearchHit r = hasil.get(i);
			out.add((i + 1) + ". " + r.getDokumen().getEntitas() + ":" + r.getDokumen().getJudul() + " ("
			        + r.getDokumen().getKunci() + ") = " + r.getSkor());
		}
	}

	/**
	 * K5 worked examples: "panadol" (tugas/05-fusi-k5.md) run at the operational
	 * ALPHA read from the {@code unifiedsearch.alpha} global property (tugas 06),
	 * plus the four reference scores from tools/silang_fusi.py fixed at ALPHA=0.45
	 * — the value that reference table was computed with — used to verify the
	 * fix in tugas/06-alpha-final.md.
	 */
	private List<String> contohFusi(TfIdfIndex indeksKata, TfIdfIndex indeksKepingan, List<SurfaceForm> formKonsep) {
		List<String> out = new ArrayList<String>();
		if (indeksKata == null || indeksKepingan == null || formKonsep.isEmpty()) {
			return out;
		}
		FusionSearch fusi = new FusionSearch(indeksKata, indeksKepingan, formKonsep);

		double alphaOperasional = AlphaConfig.current();
		out.add("ALPHA operasional (unifiedsearch.alpha) = " + alphaOperasional);
		List<RankedDocument> panadol = fusi.search("panadol", alphaOperasional);
		for (int i = 0; i < Math.min(3, panadol.size()); i++) {
			RankedDocument r = panadol.get(i);
			out.add((i + 1) + ". " + r.getDokumen().getJudul() + " (" + r.getDokumen().getKunci() + ") = "
			        + r.getSkor());
		}

		out.add("--- verifikasi acuan tools/silang_fusi.py (ALPHA=0,45, tetap) ---");
		out.add(skorAcuan(fusi, "panadol", "Acetaminophen"));
		out.add(skorAcuan(fusi, "diabete melitus", "Diabetes mellitus"));
		out.add(skorAcuan(fusi, "diabete melitus", "Diabetes mellitus, type 2"));
		out.add(skorAcuan(fusi, "pulm edem", "Pulmonary edema"));
		return out;
	}

	private String skorAcuan(FusionSearch fusi, String query, String judulTarget) {
		List<RankedDocument> hasil = fusi.search(query, ALPHA_ACUAN_VERIFIKASI);
		for (RankedDocument r : hasil) {
			if (judulTarget.equals(r.getDokumen().getJudul())) {
				return "\"" + query + "\" -> " + judulTarget + " = " + r.getSkor();
			}
		}
		return "\"" + query + "\" -> " + judulTarget + " tidak ditemukan (di bawah ambang atau bukan konsep)";
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

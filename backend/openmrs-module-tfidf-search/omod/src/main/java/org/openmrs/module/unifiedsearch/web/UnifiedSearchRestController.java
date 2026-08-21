package org.openmrs.module.unifiedsearch.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openmrs.module.unifiedsearch.EvalService;
import org.openmrs.module.unifiedsearch.Timed;
import org.openmrs.module.unifiedsearch.UnifiedSearchService;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * GET /ws/rest/v1/unifiedsearch and /ws/rest/v1/unifiedsearch/eval (tugas 09).
 *
 * <p>Latency is reported via the {@code X-Unifiedsearch-Waktu-Ms} header, not the response
 * body, so that the same request made twice returns a byte-identical body (CLAUDE.md rule 1 /
 * tugas 09 "Selesai kalau").
 */
@Controller
@RequestMapping("/rest/" + RestConstants.VERSION_1 + "/unifiedsearch")
public class UnifiedSearchRestController extends BaseRestController {

	private static final String HEADER_WAKTU_MS = "X-Unifiedsearch-Waktu-Ms";

	private final UnifiedSearchService searchService;

	private final EvalService evalService;

	@Autowired
	public UnifiedSearchRestController(UnifiedSearchService searchService, EvalService evalService) {
		this.searchService = searchService;
		this.evalService = evalService;
	}

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String q,
	        @RequestParam(value = "mode", required = false, defaultValue = "e3") String mode,
	        @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
	        @RequestParam(value = "entitas", required = false) String entitas) {
		Timed<Map<String, Object>> hasil = searchService.search(q, mode, limit, entitas);
		return withWaktuHeader(hasil);
	}

	@RequestMapping(value = "/eval", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> evaluate(
	        @RequestParam(value = "mode", required = false, defaultValue = "e3") String mode) {
		Timed<Map<String, Object>> hasil = evalService.evaluate(mode);
		return withWaktuHeader(hasil);
	}

	private static ResponseEntity<Map<String, Object>> withWaktuHeader(Timed<Map<String, Object>> hasil) {
		return ResponseEntity.ok().header(HEADER_WAKTU_MS, String.valueOf(hasil.getWaktuMs())).body(hasil.getBody());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public Map<String, Object> badRequest(IllegalArgumentException ex) {
		Map<String, Object> err = new LinkedHashMap<String, Object>();
		err.put("error", ex.getMessage());
		return err;
	}
}

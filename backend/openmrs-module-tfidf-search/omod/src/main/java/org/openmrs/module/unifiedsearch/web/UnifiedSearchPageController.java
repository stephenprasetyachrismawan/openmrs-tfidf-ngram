package org.openmrs.module.unifiedsearch.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Placeholder page. It exists only to prove the module is loaded and its web
 * layer is wired; the real search page arrives with the UI task.
 */
@Controller
public class UnifiedSearchPageController {
	
	@RequestMapping("/module/unifiedsearch/pencarianTerpadu.form")
	public String showPage() {
		return "/module/unifiedsearch/pencarianTerpadu";
	}
}

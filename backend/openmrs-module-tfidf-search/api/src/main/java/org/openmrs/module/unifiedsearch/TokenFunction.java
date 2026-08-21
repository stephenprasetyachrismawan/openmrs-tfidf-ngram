package org.openmrs.module.unifiedsearch;

import java.util.List;

/**
 * How a surface form is cut into terms. {@link Tokenizer#words(String)} is one
 * implementation; character n-grams (task 04) is another — {@link TfIdfIndex}
 * itself does not care which, only the ltc formula does.
 */
public interface TokenFunction {
	
	List<String> tokenize(String s);
}

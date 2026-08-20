package org.openmrs.module.unifiedsearch;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;

/**
 * Module lifecycle hook. The index builder is added in a later task; for now the
 * activator only logs so that a successful start is visible in the server log.
 */
public class UnifiedSearchActivator extends BaseModuleActivator {
	
	private static final Log log = LogFactory.getLog(UnifiedSearchActivator.class);
	
	@Override
	public void started() {
		log.info("Started module " + UnifiedSearchConstants.MODULE_ID);
	}
	
	@Override
	public void stopped() {
		log.info("Stopped module " + UnifiedSearchConstants.MODULE_ID);
	}
}

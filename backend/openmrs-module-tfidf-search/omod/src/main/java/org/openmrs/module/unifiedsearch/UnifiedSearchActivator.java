package org.openmrs.module.unifiedsearch;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.DaemonTokenAware;

/**
 * Module lifecycle hook. The index is built on a daemon thread while the module starts
 * (tugas 09 step 1: "indeks terbangun saat startup, waktunya tercatat di log, &lt; 10 detik"),
 * so the OpenMRS startup thread itself is never blocked and no JDBC connection from the
 * startup transaction is held through minutes of in-memory TF-IDF work.
 *
 * <p>{@link IndexBuilder#ensureBuilt()} stays in place as a safety net for requests that
 * arrive before the background build finishes.
 */
public class UnifiedSearchActivator extends BaseModuleActivator implements DaemonTokenAware {

	private static final Log log = LogFactory.getLog(UnifiedSearchActivator.class);

	private DaemonToken daemonToken;

	@Override
	public void setDaemonToken(DaemonToken token) {
		this.daemonToken = token;
	}

	@Override
	public void started() {
		if (daemonToken == null) {
			log.error("Belum menerima DaemonToken; indeks tidak dibangun saat startup, akan dibangun lazy"
			        + " pada permintaan pertama");
			return;
		}
		Daemon.runInDaemonThreadWithoutResult(new Runnable() {

			@Override
			public void run() {
				List<IndexBuilder> beans = Context.getRegisteredComponents(IndexBuilder.class);
				if (beans.isEmpty()) {
					log.error("Tidak menemukan bean IndexBuilder; indeks tidak dibangun saat startup");
					return;
				}
				try {
					beans.get(0).build();
				}
				catch (RuntimeException e) {
					log.error("Pembangunan indeks saat startup gagal, akan dicoba lagi lazy pada permintaan pertama",
					    e);
				}
			}
		}, daemonToken);
		log.info("Started module " + UnifiedSearchConstants.MODULE_ID + " (index build started on daemon thread)");
	}

	@Override
	public void stopped() {
		log.info("Stopped module " + UnifiedSearchConstants.MODULE_ID);
	}
}

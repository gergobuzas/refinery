/*
 * SPDX-FileCopyrightText: 2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.language.web.xtext;

import org.eclipse.xtext.util.CancelIndicator;
import tools.refinery.generator.web.library.IGenerationWorker;


public class ModelGenerationManager {
	private final Object lockObject = new Object();
	private IGenerationWorker worker;
	private boolean disposed;

	public boolean setActiveModelGenerationWorker(IGenerationWorker worker, CancelIndicator cancelIndicator) {
		synchronized (lockObject) {
			cancel();
			if (disposed || cancelIndicator.isCanceled()) {
				return true;
			}
			this.worker = worker;
		}
		return false;
	}


	public void cancel() {
		synchronized (lockObject) {
			if (worker != null) {
				worker.cancel();
				worker = null;
			}
		}
	}

	public void dispose() {
		synchronized (lockObject) {
			disposed = true;
			cancel();
		}
	}
}

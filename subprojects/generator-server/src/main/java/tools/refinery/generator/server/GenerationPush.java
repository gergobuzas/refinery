package tools.refinery.generator.server;

import org.eclipse.jetty.websocket.api.Session;
import tools.refinery.generator.web.library.IPush;

public class GenerationPush implements IPush {

	private Session session;

	@Override
	public tools.refinery.language.web.generator.ModelGenerationManager getModelGenerationManager() {
		return null;
	}

	@Override
	public void cancelModelGeneration() {

	}

	@Override
	public void dispose() {

	}

	@Override
	public <T extends org.eclipse.xtext.web.server.IServiceResult> void notifyPrecomputationListeners(String serviceName, T result) {

	}

	@Override
	public <T extends org.eclipse.xtext.web.server.IServiceResult> void precomputeServiceResult(org.eclipse.xtext.web.server.model.AbstractCachedService<T> service, String serviceName, org.eclipse.xtext.util.CancelIndicator cancelIndicator, boolean logCacheMiss) {

	}

	@Override
	public void removePrecomputationListener(tools.refinery.language.web.xtext.server.push.PrecomputationListener listener) {

	}

	@Override
	public void addPrecomputationListener(tools.refinery.language.web.xtext.server.push.PrecomputationListener listener) {

	}
}

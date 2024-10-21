package tools.refinery.generator.web.library;

import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.web.server.IServiceResult;
import org.eclipse.xtext.web.server.model.AbstractCachedService;
import tools.refinery.language.web.xtext.ModelGenerationManager;
import tools.refinery.language.web.xtext.server.push.PrecomputationListener;

public interface IPush {

	public ModelGenerationManager getModelGenerationManager();

	public void addPrecomputationListener(PrecomputationListener listener);

	public void removePrecomputationListener(PrecomputationListener listener);

	public <T extends IServiceResult> void precomputeServiceResult(AbstractCachedService<T> service, String serviceName,
																   CancelIndicator cancelIndicator,
																   boolean logCacheMiss);

	public <T extends IServiceResult> void notifyPrecomputationListeners(String serviceName, ModelGenerationResult result);

	public void cancelModelGeneration();

	public void dispose();
}

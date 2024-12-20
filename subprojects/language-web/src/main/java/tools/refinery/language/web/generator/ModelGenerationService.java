/*
 * SPDX-FileCopyrightText: 2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.language.web.generator;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.concurrent.CancelableUnitOfWork;
import org.eclipse.xtext.web.server.model.IXtextWebDocument;
import tools.refinery.language.web.semantics.SemanticsService;
import tools.refinery.language.web.xtext.server.push.PushWebDocument;
import tools.refinery.language.web.xtext.server.push.PushWebDocumentAccess;


@Singleton
public class ModelGenerationService {
	public static final String SERVICE_NAME = "modelGeneration";
	public static final String MODEL_GENERATION_EXECUTOR = "modelGeneration";
	public static final String MODEL_GENERATION_TIMEOUT_EXECUTOR = "modelGenerationTimeout";
	public static  boolean USE_GENERATOR_SERVER = true;

	@Inject
	private OperationCanceledManager operationCanceledManager;

	@Inject
	private Provider<ModelGenerationWorker> workerProvider;

	@Inject
	private Provider<ModelRemoteGenerationWorker> remoteWorkerProvider;

	private final long timeoutSec;

	public ModelGenerationService() {
		timeoutSec = SemanticsService.getTimeout("REFINERY_MODEL_GENERATION_TIMEOUT_SEC").orElse(600L);
		String useGenServer = System.getenv("USE_GENERATOR_SERVER");
		if (useGenServer != null)
			USE_GENERATOR_SERVER = Boolean.parseBoolean(useGenServer);
	}

	private ModelGenerationStartedResult generateModelLocal(PushWebDocumentAccess document, int randomSeed){
		return document.modify(new CancelableUnitOfWork<>() {
			@Override
			public ModelGenerationStartedResult exec(IXtextWebDocument state, CancelIndicator cancelIndicator) {
				var pushState = (PushWebDocument) state;
				var worker = workerProvider.get();
				worker.setState(pushState, randomSeed, timeoutSec);
				var manager = pushState.getModelGenerationManager();
				worker.start();
				boolean canceled = manager.setActiveModelGenerationWorker(worker, cancelIndicator);
				if (canceled) {
					worker.cancel();
					operationCanceledManager.throwOperationCanceledException();
				}
				return new ModelGenerationStartedResult(worker.getUuid());
			}
		});
	}

	private ModelGenerationStartedResult generateModelRemote(PushWebDocumentAccess document, int randomSeed){
		return document.modify(new CancelableUnitOfWork<>() {
			@Override
			public ModelGenerationStartedResult exec(IXtextWebDocument state, CancelIndicator cancelIndicator) {
				var pushState = (PushWebDocument) state;
				var worker = remoteWorkerProvider.get();
				worker.setState(pushState, randomSeed, timeoutSec);
				var manager = pushState.getModelGenerationManager();
				worker.start();
				boolean canceled = manager.setActiveModelGenerationWorker(worker, cancelIndicator);
				if (canceled) {
					worker.cancel();
					operationCanceledManager.throwOperationCanceledException();
				}
				return new ModelGenerationStartedResult(worker.getUuid());
			}
		});
	}

	public ModelGenerationStartedResult generateModel(PushWebDocumentAccess document, int randomSeed) {
		if (USE_GENERATOR_SERVER) {
			return generateModelRemote(document, randomSeed);
		}
		return generateModelLocal(document, randomSeed);
	}

	public ModelGenerationCancelledResult cancelModelGeneration(PushWebDocumentAccess document) {
		document.cancelModelGeneration();
		return new ModelGenerationCancelledResult();
	}
}

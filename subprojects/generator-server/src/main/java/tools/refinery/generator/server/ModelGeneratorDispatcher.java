package tools.refinery.generator.server;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jetty.websocket.api.Session;
import tools.refinery.generator.ModelGenerator;
import tools.refinery.language.ProblemRuntimeModule;
import tools.refinery.language.ProblemStandaloneSetup;
import tools.refinery.language.model.problem.Problem;
import tools.refinery.language.web.ProblemWebModule;
import tools.refinery.language.web.ProblemWebSetup;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
* Singleton class for dispatching the requests on separate threads
 * Make this a singleton, so that no other instances can be made
 * ModelGenerator should be running on separate threads, once started
 * Status updates should be sent through via the session
* */

public class ModelGeneratorDispatcher {
	HashMap<UUID, ModelGeneratorExecutor> threadPool;
	private static ModelGeneratorDispatcher instance = null;

	public static synchronized ModelGeneratorDispatcher getInstance() {
		if (instance == null) {
			instance = new ModelGeneratorDispatcher();
		}
		return instance;
	}

	public void addGenerationRequest(UUID uuid, Long randomSeed, String problemString, Session webSocketSession){
		Injector injector = new ProblemStandaloneSetup().createInjectorAndDoEMFRegistration();
		ModelGeneratorExecutor threadOfExecution = injector.getInstance(ModelGeneratorExecutor.class);
		threadOfExecution.initialize(randomSeed, problemString, webSocketSession);
		threadOfExecution.start();
		threadPool.put(uuid, threadOfExecution);
	}

	public void cancelGenerationRequest(UUID uuid) {
		ModelGeneratorExecutor threadOfExecution = threadPool.get(uuid);
		threadOfExecution.cancel();
	}

	public void disconnect(UUID uuid) {
		ModelGeneratorExecutor threadOfExecution = threadPool.get(uuid);
		threadPool.remove(uuid);
		threadOfExecution.disconnect();
	}

	private ModelGeneratorDispatcher() {
		threadPool = new HashMap<>();
	}
}

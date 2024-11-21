package tools.refinery.language.web.generator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.refinery.generator.ModelGeneratorFactory;
import tools.refinery.generator.ProblemLoader;
import tools.refinery.language.web.semantics.PartialInterpretation2Json;
import tools.refinery.language.web.semantics.metadata.MetadataCreator;
import tools.refinery.language.web.semantics.metadata.NodeMetadata;
import tools.refinery.language.web.semantics.metadata.RelationMetadata;
import tools.refinery.language.web.semantics.metadata.RelationMetadataGson;
import tools.refinery.language.web.xtext.server.ThreadPoolExecutorServiceProvider;
import tools.refinery.language.web.xtext.server.push.PushWebDocument;
import tools.refinery.store.util.CancellationToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class ModelRemoteGenerationWorker implements IGenerationWorker, Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(ModelRemoteGenerationWorker.class);
	private final UUID uuid = UUID.randomUUID();
	private PushWebDocument state;
	private String text;
	private volatile boolean timedOut;
	private volatile boolean cancelled;
	@Inject
	private OperationCanceledManager operationCanceledManager;
	@Inject
	private ProblemLoader problemLoader;
	@Inject
	private ModelGeneratorFactory generatorFactory;
	@Inject
	private MetadataCreator metadataCreator;
	@Inject
	private PartialInterpretation2Json partialInterpretation2Json;
	private final Object lockObject = new Object();
	private ExecutorService executorService;
	private ScheduledExecutorService scheduledExecutorService;
	private int randomSeed;
	private long timeoutSec;
	private Future<?> future;
	private ScheduledFuture<?> timeoutFuture;
	private GeneratorWebSocketEndpoint client;
	private ModelGenerationErrorResult errorResult;

	private final CancellationToken cancellationToken = () -> {
		if (cancelled || Thread.interrupted()) {
			operationCanceledManager.throwOperationCanceledException();
		}
	};

	private void setupClient() throws Exception {
		client = new GeneratorWebSocketEndpoint();
		client.setTimeoutSec(timeoutSec);
		client.setUuidOfWorker(uuid);
		System.out.println(uuid);
	}

	public ModelRemoteGenerationWorker(){
		try {
			setupClient();
		}
		catch (Exception e) {
			e.printStackTrace();
			LOG.error("Couldn't setup client!");
		}
	}

	@Inject
	public void setExecutorServiceProvider(ThreadPoolExecutorServiceProvider provider) {
		executorService = provider.get(ModelGenerationService.MODEL_GENERATION_EXECUTOR);
		scheduledExecutorService = provider.getScheduled(ModelGenerationService.MODEL_GENERATION_TIMEOUT_EXECUTOR);
	}

	private void notifyResult(ModelGenerationResult result) {
		state.notifyPrecomputationListeners(ModelGenerationService.SERVICE_NAME, result);
	}

	private boolean checkForResultAndNotifyResult() throws InterruptedException {
		var modelResult = client.getResult();
		notifyResult(modelResult);
		if (modelResult instanceof ModelGenerationErrorResult) {
			errorResult = (ModelGenerationErrorResult) modelResult;
			return false;
		}
		return true;
	}

	private List<NodeMetadata> checkForNodesMetadata() throws InterruptedException {
		return client.getNodesMetaData();
	}

	private List<RelationMetadata> checkForRelationsMetadata() throws InterruptedException {
		return client.getRelationsMetaData();
	}

	private JsonObject checkForPartialInterpretation() throws InterruptedException {
		return client.getPartialInterpretaiton();
	}

	@Override
	public void setState(PushWebDocument state, int randomSeed, long timeoutSec) {
		this.state = state;
		this.randomSeed = randomSeed;
		this.timeoutSec = timeoutSec;
		this.text = state.getText();
		client.setTimeoutSec(timeoutSec);
	}

	@Override
	public UUID getUuid() {
		return uuid;
	}

	@Override
	public void start() {
		synchronized (lockObject) {
			LOG.debug("Enqueueing remote model generation: {}", uuid);
			future = executorService.submit(this);
		}
	}

	@Override
	public void startTimeout() {
		synchronized (lockObject) {
			LOG.debug("Starting model generation: {}", uuid);
			cancellationToken.checkCancelled();
			timeoutFuture = scheduledExecutorService.schedule(() -> cancel(true), timeoutSec, TimeUnit.SECONDS);
		}
	}

	@Override
	public ModelGenerationResult doRun() throws Exception {
		System.out.println("doRun()");
		cancellationToken.checkCancelled();
		try {
			boolean success = client.sendGenerationRequest(text, randomSeed);
			if (!success && client != null)
				throw new RuntimeException("Some problem occured...");

			int NUMBER_OF_SERVER_RESPONSES = 3;
			// Validation ok,
			// Generating model,
			// satisfiable ok, --- wasn't sent so far, can be added tho in the future
			// saving generated model
			for (int i = 0; i < NUMBER_OF_SERVER_RESPONSES; ++i) {
				cancellationToken.checkCancelled();
				boolean passed = checkForResultAndNotifyResult();
				if (!passed)
					return errorResult;
			}
			System.out.println("End of the SERVER_RESPONSEs");
			//Getting nodes metadata
			var nodesMetaData = checkForNodesMetadata();
			cancellationToken.checkCancelled();
			//Getting relations metadata
			var relationsMetaData = checkForRelationsMetadata();
			cancellationToken.checkCancelled();
			//Getting partial Interpretation
			var partialInterpretation = checkForPartialInterpretation();
			LOG.info("Received all generation metadata!....");

			client.close();
			return new ModelGenerationSuccessResult(uuid, nodesMetaData, relationsMetaData, partialInterpretation);
		}
		catch (Exception e) {
			LOG.error(e.getMessage(), e);
			e.printStackTrace();
			client.close();
			return new ModelGenerationErrorResult(uuid, "Error: " + e.getMessage());
		}
	}


	@Override
	public void run(){
		System.out.println("run()");
		startTimeout();
		notifyResult(new ModelGenerationStatusResult(uuid, "Initializing model generator"));
		ModelGenerationResult result;
		try {
			result = doRun();
		} catch (Throwable e) {
			if (operationCanceledManager.isOperationCanceledException(e)) {
				var message = timedOut ? "Model generation timed out" : "Model generation cancelled";
				LOG.debug("{}: {}", message, uuid);
				notifyResult(new ModelGenerationErrorResult(uuid, message));
			} else if (e instanceof Error error) {
				// Make sure we don't try to recover from any fatal JVM errors.
				throw error;
			} else {
				LOG.debug("Model generation error", e);
				notifyResult(new ModelGenerationErrorResult(uuid, e.toString()));
			}
			return;
		}
		System.out.println(result);
		notifyResult(result);
		//Thread.currentThread().interrupt();
	}

	@Override
	public void cancel() {
		cancel(false);
	}

	@Override
	public void cancel(boolean timedOut) {
		synchronized (lockObject) {
			LOG.trace("Cancelling model generation: {}", uuid);
			try {
				if (this.client != null)
					client.sendCancelRequest();
			} catch (Exception e) {
				LOG.debug(e.getMessage(), e);
				System.out.println("Could not cancel model generation of uuid: " + uuid);
			}
			// TODO maybe we need this later
			//notifyResult(new ModelGenerationCancelledResult());
			this.timedOut = timedOut;
			cancelled = true;
			if (future != null) {
				future.cancel(true);
				future = null;
			}
			if (timeoutFuture != null) {
				timeoutFuture.cancel(true);
				timeoutFuture = null;
			}
		}
	}
}

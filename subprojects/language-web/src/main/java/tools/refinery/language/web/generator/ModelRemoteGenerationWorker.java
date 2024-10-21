package tools.refinery.language.web.generator;

import com.google.gson.JsonObject;
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
import tools.refinery.generator.web.library.IGenerationWorker;
import tools.refinery.language.web.semantics.PartialInterpretation2Json;
import tools.refinery.language.web.semantics.metadata.MetadataCreator;
import tools.refinery.language.web.semantics.metadata.NodeMetadata;
import tools.refinery.language.web.semantics.metadata.RelationMetadata;
import tools.refinery.language.web.xtext.server.ThreadPoolExecutorServiceProvider;
import tools.refinery.language.web.xtext.server.push.PushWebDocument;
import tools.refinery.store.util.CancellationToken;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class ModelRemoteGenerationWorker implements IGenerationWorker, Runnable {
	//TODO WebSocket client
	@WebSocket
	private class GeneratorWebSocketEndpoint {
		private static final Logger LOG = LoggerFactory.getLogger(GeneratorWebSocketEndpoint.class);
		private LinkedBlockingQueue<ModelGenerationResult> responseQueue;
		private LinkedBlockingQueue<List<NodeMetadata>> nodesMetaDataQueue = new LinkedBlockingQueue<>();
		private LinkedBlockingQueue<List<RelationMetadata>> relationsMetadataQueue = new LinkedBlockingQueue<>();
		private LinkedBlockingQueue<JsonObject> partialInterpretaitonQueue = new LinkedBlockingQueue<>();
		private final URI uri = URI.create("ws://localhost:8080");
		private UUID uuidOfWorker;
		private final WebSocketClient client;
		private Session session;
		private long timeoutSec;

		public GeneratorWebSocketEndpoint() throws Exception {
			client = new WebSocketClient();

			try {
				client.start();
			}
			catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
			finally {
				client.stop();
			}
		}


		public void setTimeoutSec(long timeoutSec) {
			this.timeoutSec = timeoutSec;
		}

		public void setUuidOfWorker(UUID uuid) {
			this.uuidOfWorker = uuid;
		}

		private void addResultToQueue(ModelGenerationResult result) throws InterruptedException {
			if (responseQueue != null) {
				responseQueue.offer(result, timeoutSec, TimeUnit.SECONDS);
			}
		}

		public ModelGenerationResult getResult() throws InterruptedException {
			return responseQueue.poll(timeoutSec, TimeUnit.SECONDS);
		}

		public JsonObject getPartialInterpretaiton() throws InterruptedException {
			return partialInterpretaitonQueue.poll(timeoutSec, TimeUnit.SECONDS);
		}

		public List<NodeMetadata> getNodesMetaData() throws InterruptedException {
			return nodesMetaDataQueue.poll(timeoutSec, TimeUnit.SECONDS);
		}

		public List<RelationMetadata> getRelationsMetaData() throws InterruptedException {
			return relationsMetadataQueue.poll(timeoutSec, TimeUnit.SECONDS);
		}

		public void connect() throws IOException, ExecutionException, InterruptedException, TimeoutException {
			ClientUpgradeRequest customRequest = new ClientUpgradeRequest();
			customRequest.setHeader("UUID", uuidOfWorker.toString());
			Future<Session> fut = client.connect(this, uri, customRequest);
			session = fut.get(timeoutSec, TimeUnit.SECONDS);
		}

		public void sendGenerationRequest(String problemText, int randomSeed) throws InterruptedException, IOException, ExecutionException, TimeoutException {
			if (!session.isOpen())
				connect();

			var type = "generationRequest";
			var uuid = this.uuidOfWorker.toString();
			JsonObject jsonToSend = new JsonObject();
			jsonToSend.addProperty("type", type);
			jsonToSend.addProperty("uuid", uuid);
			JsonObject generationData = new JsonObject();
			generationData.addProperty("randomSeed", randomSeed);
			generationData.addProperty("problem", problemText);
			// TODO might cause problem the toString of the generationData
			jsonToSend.addProperty("generationDetails", generationData.toString());

			System.out.println("Sending out json..." + jsonToSend.toString());
			session.sendText(jsonToSend.toString(), Callback.NOOP);
		}

		public void sendCancelRequest() throws IOException, ExecutionException, InterruptedException, TimeoutException {
			if (!session.isOpen())
				connect();

			var type = "cancelRequest";
			var uuid = this.uuidOfWorker.toString();
			JsonObject jsonToSend = new JsonObject();
			jsonToSend.addProperty("type", type);
			jsonToSend.addProperty("uuid", uuid);

			session.sendText(jsonToSend.toString(), Callback.NOOP);
		}

		@OnWebSocketClose
		public void onClose(int statusCode, String reason)
		{
			LOG.info("WebSocket Close: {} - {}",statusCode,reason);
		}

		@OnWebSocketOpen
		public void onOpen(Session session)
		{
			LOG.info("WebSocket Open: {}", session);
		}

		@OnWebSocketError
		public void onError(Throwable cause)
		{
			LOG.warn("WebSocket Error",cause);
		}

		@OnWebSocketMessage
		public void onText(String message) throws InterruptedException {
			LOG.info("Text Message [{}]",message);
			System.out.println("Received message: " + message);
			//TODO parse the result of the server
			if (message == "Error"){
				//TODO extract the exact error instead of the whole message
				ModelGenerationErrorResult result = new ModelGenerationErrorResult(uuid, message);
				addResultToQueue(result);
			}
			//TODO rest of the messages
		}

		public void close() throws Exception {
			client.stop();
		}

	}
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
	}

	public ModelRemoteGenerationWorker(){
		try {
			setupClient();
		}
		catch (Exception e) {
			e.printStackTrace();
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
	public ModelGenerationResult doRun() throws IOException {
		cancellationToken.checkCancelled();
		try {
			client.sendGenerationRequest(text, randomSeed);
			int NUMBER_OF_SERVER_RESPONSES = 4;
			// Validation ok,
			// Generating model,
			// satisfiable ok,
			// saving generated model
			for (int i = 0; i < NUMBER_OF_SERVER_RESPONSES; ++i) {
				cancellationToken.checkCancelled();
				boolean passed = checkForResultAndNotifyResult();
				if (!passed)
					return errorResult;
			}
			//Getting nodes metadata
			var nodesMetaData = checkForNodesMetadata();
			cancellationToken.checkCancelled();
			//Getting relations metadata
			var relationsMetaData = checkForRelationsMetadata();
			cancellationToken.checkCancelled();
			//Getting partial Interpretation
			var partialInterpretation = checkForPartialInterpretation();

			return new ModelGenerationSuccessResult(uuid, nodesMetaData, relationsMetaData, partialInterpretation);
		}
		catch (Exception e) {
			LOG.error(e.getMessage(), e);
			e.printStackTrace();
			return new ModelGenerationErrorResult(uuid, "Error: " + e.getMessage());
		}
		//TODO these should be put into network communication too!
		/*
		metadataCreator.setProblemTrace(generator.getProblemTrace());
		var nodesMetadata = metadataCreator.getNodesMetadata(generator.getModel(), Concreteness.CANDIDATE);
		cancellationToken.checkCancelled();
		var relationsMetadata = metadataCreator.getRelationsMetadata();
		cancellationToken.checkCancelled();
		var partialInterpretation = partialInterpretation2Json.getPartialInterpretation(generator, cancellationToken);
		return new ModelGenerationSuccessResult(uuid, nodesMetadata, relationsMetadata, partialInterpretation);
		 */
	}


	@Override
	public void run(){
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
		notifyResult(result);
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
				client.sendCancelRequest();
			} catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
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

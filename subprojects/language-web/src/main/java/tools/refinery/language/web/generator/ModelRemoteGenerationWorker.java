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
	@WebSocket
	public class GeneratorWebSocketEndpoint {
		private static final Logger LOG = LoggerFactory.getLogger(GeneratorWebSocketEndpoint.class);
		private LinkedBlockingQueue<ModelGenerationResult> responseQueue = new LinkedBlockingQueue<>();
		private LinkedBlockingQueue<List<NodeMetadata>> nodesMetaDataQueue = new LinkedBlockingQueue<>();
		private LinkedBlockingQueue<List<RelationMetadata>> relationsMetadataQueue = new LinkedBlockingQueue<>();
		private LinkedBlockingQueue<JsonObject> partialInterpretaitonQueue = new LinkedBlockingQueue<>();
		private URI uri;
		private UUID uuidOfWorker;
		private final WebSocketClient client;
		private Session session;
		private long timeoutSec;

		private void getGeneratorUri(){
			String portStr = System.getenv("REFINERY_GENERATOR_WS_PORT");
			boolean portIsSet = portStr != null && !portStr.isEmpty();
			int port;
			if (portIsSet){
				port = Integer.parseInt(portStr);
			} else {
				port = 1314; //The default port
			}

			String host = System.getenv("REFINERY_GENERATOR_WS_HOST");
			if (host == null || host.isEmpty()){
				System.out.println("NO HOSTNAME SET (REFINERY_GENERATOR_WS_HOST)... localhost set");
				host = "localhost";
			}

			uri = URI.create("ws://" + host + ":" + port);
			System.out.println("URI:" + uri);
		}

		public GeneratorWebSocketEndpoint() throws Exception {
			getGeneratorUri();
			client = new WebSocketClient();
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

		public void connect() throws Exception {
			ClientUpgradeRequest customRequest = new ClientUpgradeRequest();
			customRequest.setHeader("UUID", uuidOfWorker.toString());
			System.out.println("Before client start");
			client.start();
			System.out.println("SEND:" + this.uri);
			System.out.println("Before connect");
			LOG.info("Connecting to '" + this.uri + "' with UUID:" + this.uuidOfWorker);
			Future<Session> fut = client.connect(this, uri, customRequest);
			System.out.println("After connect");
			session = fut.get(timeoutSec, TimeUnit.SECONDS);
		}

		public void sendGenerationRequest(String problemText, int randomSeed) throws Exception {
			if (session == null || !session.isOpen()) {
				connect();
			}

			var type = "generationRequest";
			var uuid = this.uuidOfWorker.toString();
			JsonObject jsonToSend = new JsonObject();
			jsonToSend.addProperty("type", type);
			jsonToSend.addProperty("uuid", uuid);
			JsonObject generationData = new JsonObject();
			generationData.addProperty("randomSeed", randomSeed);
			generationData.addProperty("problem", problemText);
			jsonToSend.addProperty("generationDetails", generationData.toString());

			session.sendText(jsonToSend.toString(), Callback.NOOP);
		}

		public void sendCancelRequest() throws Exception {
			if (session == null || !session.isOpen())
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
			LOG.info("WebSocket Close for UUID:{}: Status:{} - Reason:{}",uuidOfWorker, statusCode,reason);
			try {
				//this.close();
			} catch (Exception e) {
				LOG.error("Couldn't close connection");
				throw new RuntimeException(e);
			}
		}

		@OnWebSocketOpen
		public void onOpen(Session session)
		{
			LOG.info("WebSocket Open Session:{}", session);
		}

		@OnWebSocketError
		public void onError(Throwable cause)
		{
			LOG.warn("WebSocket Error",cause);
		}

		@OnWebSocketMessage
		public void onText(String message) throws InterruptedException {
			LOG.info("Text Message [{}]",message);
			System.out.println("Client onText:\n" + message);

			//Parsing the received message
			JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
			var type = jsonMessage.get("type").getAsString();

			if (type.equals("result")){
				var resultMessage = jsonMessage.get("message").getAsString();
				var modelGenerationResult = new ModelGenerationStatusResult(uuidOfWorker, resultMessage);
				responseQueue.offer(modelGenerationResult);
			}

			if (type.equals("error")){
				var resultMessage = jsonMessage.get("message").getAsString();
				var modelGenerationResult = new ModelGenerationErrorResult(uuidOfWorker, resultMessage);
				responseQueue.offer(modelGenerationResult);
			}

			if (type.equals("nodesMetadata")){
				var nodesMetaDataArray = jsonMessage.get("object").getAsJsonArray();
				Type listType = new TypeToken<List<NodeMetadata>>(){}.getType();
				List<NodeMetadata> nodesMetaDataObject = new Gson().fromJson(nodesMetaDataArray, listType);
				nodesMetaDataQueue.offer(nodesMetaDataObject);
			}

			if (type.equals("relationsMetadata")){
				var relationsMetadataArray = jsonMessage.get("object").getAsJsonArray();
				Type listType = new TypeToken<List<RelationMetadata>>(){}.getType();
				List<RelationMetadata> relationsMetadataObject =  RelationMetadataGson.createGson().fromJson(relationsMetadataArray, listType);
				relationsMetadataQueue.offer(relationsMetadataObject);
			}

			if (type.equals("partialInterpretation")){
				var partialInterpretaitonString = jsonMessage.get("object").getAsString();
				var partialInterpretaiton = JsonParser.parseString(partialInterpretaitonString).getAsJsonObject();
				var partialInterpretaitonObject = new Gson().fromJson(partialInterpretaiton, JsonObject.class);
				System.out.println(partialInterpretaitonObject);
				partialInterpretaitonQueue.offer(partialInterpretaitonObject);
			}
		}

		public void close() throws Exception {
			boolean canBeClosed = false;
			while (!canBeClosed)
			{
				System.out.println(relationsMetadataQueue.size());
				System.out.println(responseQueue.size());
				System.out.println(nodesMetaDataQueue.size());
				System.out.println(partialInterpretaitonQueue.size());
				canBeClosed = relationsMetadataQueue.isEmpty() &&
								nodesMetaDataQueue.isEmpty() &&
								partialInterpretaitonQueue.isEmpty() &&
								responseQueue.isEmpty();
			}
			session.close(1000, uuidOfWorker.toString(), Callback.from( () -> {
				try {
					client.stop();
				} catch (Exception e) {
					LOG.error("Couldn't stop client!");
					throw new RuntimeException(e.getMessage());
				}
			}, this::closeFailed));

		}

		public void closeFailed(Throwable x) {
			LOG.error("closeFailed - couldn't stop websocket client for some reason", x);
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
	private boolean finished = false;

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
		//client.setTimeoutSec(timeoutSec);
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
		cancellationToken.checkCancelled();
		try {
			client.sendGenerationRequest(text, randomSeed);
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
		if (finished) {
			LOG.info("Finished value: {}", finished);
			return;
		}
		try {
			setupClient();
		}
		catch (Exception e) {
			e.printStackTrace();
			LOG.error("Couldn't setup client!");
		}
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
		finished = true;
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

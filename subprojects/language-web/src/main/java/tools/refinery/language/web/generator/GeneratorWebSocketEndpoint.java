package tools.refinery.language.web.generator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.refinery.language.web.semantics.metadata.NodeMetadata;
import tools.refinery.language.web.semantics.metadata.RelationMetadata;
import tools.refinery.language.web.semantics.metadata.RelationMetadataGson;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@WebSocket
public class GeneratorWebSocketEndpoint {
	private static final Logger LOG = LoggerFactory.getLogger(GeneratorWebSocketEndpoint.class);
	private LinkedBlockingQueue<ModelGenerationResult> responseQueue = new LinkedBlockingQueue<>();
	private LinkedBlockingQueue<List<NodeMetadata>> nodesMetaDataQueue = new LinkedBlockingQueue<>();
	private LinkedBlockingQueue<List<RelationMetadata>> relationsMetadataQueue = new LinkedBlockingQueue<>();
	private LinkedBlockingQueue<JsonObject> partialInterpretaitonQueue = new LinkedBlockingQueue<>();
	private URI uri;
	private UUID uuidOfWorker;
	private WebSocketClient client;
	private Session session;
	private long timeoutSec;
	private boolean finished;

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
		finished = false;
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

	public void connect(){
		ClientUpgradeRequest customRequest = new ClientUpgradeRequest();
		customRequest.setHeader("UUID", uuidOfWorker.toString());
		System.out.println("Before client start");
		try{
			client.start();
		} catch (Exception e) {
			LOG.error("Couldn't start the client for Generation!!");
		}
		System.out.println("SEND:" + this.uri);
		System.out.println("Before connect");
		LOG.info("Connecting to '" + this.uri + "' with UUID:" + this.uuidOfWorker);
		try {
			Future<Session> fut = client.connect(this, uri, customRequest);
			System.out.println("After connect sent");
			session = fut.get(timeoutSec, TimeUnit.SECONDS);
		} catch (Exception e) {
			LOG.error("Couldn't create session upon connection to URL!!!");
			System.out.println("Couldn't create session upon connection to URL!!!");
			throw new RuntimeException("Couldn't create session upon connection to URL!!!");
		}
	}

	public boolean sendGenerationRequest(String problemText, int randomSeed){
		if (finished)
			return false;
		if (session == null || !session.isOpen()) {
			connect();
		}
		if (session == null) {
			return false;
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
		return true;
	}

	public void sendCancelRequest(){
		System.out.println("Trying to cancel UUID:" + uuidOfWorker);
		if (finished)
			return;
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
		//This is not needed, as the Client (ModelRemoteGenerationWorker) initiates the
		/*
		try {
			//this.close();
		} catch (Exception e) {
			LOG.error("Couldn't close connection");
			throw new RuntimeException(e);
		}
		 */
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
		session.close(1000, uuidOfWorker.toString(), Callback.from( () -> {
			try {
				client.stop();
				client = null;
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

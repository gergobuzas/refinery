package tools.refinery.generator.server;

import com.google.gson.*;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.refinery.generator.GeneratorResult;
import tools.refinery.generator.ModelGenerator;
import tools.refinery.generator.ModelGeneratorFactory;
import tools.refinery.generator.ProblemLoader;
import tools.refinery.language.model.problem.Problem;
import tools.refinery.language.web.semantics.PartialInterpretation2Json;
import tools.refinery.language.web.semantics.metadata.MetadataCreator;
import tools.refinery.language.web.semantics.metadata.NodeMetadata;
import tools.refinery.language.web.semantics.metadata.RelationMetadata;
import tools.refinery.store.reasoning.literal.Concreteness;

import java.io.IOException;
import java.util.List;

/**
 * This class should implement runnable
 * The whole model generation should be done here
 * It should be called from the GenerationDispatcher
 * There should be queue given to the constructor, so that the dispatcher and the Generation Status-es can be sent
 * through to the clients
 * */
public class ModelGeneratorExecutor extends Thread {
	private static final Logger LOG = LoggerFactory.getLogger(ModelGeneratorExecutor.class);

	@Inject
	private ModelGeneratorFactory factory;
	@Inject
	private ProblemLoader problemLoader;
	@Inject
	private MetadataCreator metadataCreator;
	@Inject
	private PartialInterpretation2Json partialInterpretation2Json;
	private ModelGenerator modelGenerator;
	private Problem problem;
	private volatile boolean cancelled = false;
	Session session;
	Long randomSeed;

	public void initialize(Long randomSeed, String problemString, Session session){
		try {
			this.problem = problemLoader.loadString(problemString);
		}
		catch(IOException e){
			e.printStackTrace();
			LOG.error("Problem loading problem: " + problemString);
		}
		this.session = session;
		this.randomSeed = randomSeed;
	}

	public void cancel() {
		cancelled = true;
	}

	private void sendResult(String message){
		JsonObject objectToSend = new JsonObject();
		objectToSend.addProperty("type", "result");
		objectToSend.addProperty("message", message);
		var jsonStringToSend = objectToSend.toString();
		System.out.println(jsonStringToSend);
		session.sendText(jsonStringToSend, Callback.NOOP);
	}

	private void sendError(String message){
		JsonObject objectToSend = new JsonObject();
		objectToSend.addProperty("type", "error");
		objectToSend.addProperty("message", message);
		session.sendText(objectToSend.getAsString(), Callback.NOOP);
	}

	private void sendNodesMetadata(List<NodeMetadata> metadata) {
		JsonObject objectToSend = new JsonObject();
		objectToSend.addProperty("type", "nodesMetadata");

		Gson gson = new Gson();
		var metaDataJson = gson.toJson(metadata);
		JsonArray metaDataArray = JsonParser.parseString(metaDataJson).getAsJsonArray();
		objectToSend.add("object", metaDataArray);

		var objectToSendString = objectToSend.toString();
		session.sendText(objectToSendString, Callback.NOOP);
	}

	private void sendRelationsMetadata(List<RelationMetadata> relationsMetadata){
		System.out.println(relationsMetadata);
		//TODO debug this part
		JsonObject objectToSend = new JsonObject();
		objectToSend.addProperty("type", "relationsMetadata");

		Gson gson = new Gson();
		var metaDataJson = gson.toJson(relationsMetadata);
		JsonArray metaDataArray = JsonParser.parseString(metaDataJson).getAsJsonArray();
		objectToSend.add("object", metaDataArray);

		var objectToSendString = objectToSend.toString();
		System.out.println(objectToSendString);
		session.sendText(objectToSendString, Callback.NOOP);
	}

	private void sendPartialInterpretation(JsonObject partialInterpretation){
		//TODO send to the session
		JsonObject objectToSend = new JsonObject();
		objectToSend.addProperty("type", "partialInterpretation");
		Gson gson = new Gson();
		var metaDataJson = gson.toJson(partialInterpretation);
		objectToSend.addProperty("object", metaDataJson);

		var partialInterpretationString = objectToSend.toString();
		session.sendText(partialInterpretationString, Callback.NOOP);
	}

	@Override
	public void run() {
		if (!cancelled) {
			try {
				modelGenerator = factory.createGenerator(problem);
			}
			catch (Exception e){
				sendError("Validation failed: " + e.getMessage());
				return;
			}
			sendResult("Validation ok!...");
		}

		if (!cancelled) {
			sendResult("Generating model...");
			modelGenerator.setRandomSeed(randomSeed);
			if (modelGenerator.tryGenerate() != GeneratorResult.SUCCESS) {
				//return new ModelGenerationErrorResult(uuid, "Problem is unsatisfiable");
				sendError("Problem is unsatisfiable");
				return;
			}
			sendResult("Saving generated problem...");
		}

		if (!cancelled){
			metadataCreator.setProblemTrace(modelGenerator.getProblemTrace());
			var nodesMetadata = metadataCreator.getNodesMetadata(modelGenerator.getModel(), Concreteness.CANDIDATE);
			sendNodesMetadata(nodesMetadata);
		}

		if (!cancelled){
			var relationsMetadata = metadataCreator.getRelationsMetadata();
			sendRelationsMetadata(relationsMetadata);
		}

		if (!cancelled){
			var partialInterpretation = partialInterpretation2Json.getPartialInterpretation(modelGenerator, () -> {
				if (cancelled || Thread.interrupted()) {
					OperationCanceledManager operationCanceledManager = new OperationCanceledManager();
					operationCanceledManager.throwOperationCanceledException();
				}
			});
			sendPartialInterpretation(partialInterpretation);
		}
	}
}

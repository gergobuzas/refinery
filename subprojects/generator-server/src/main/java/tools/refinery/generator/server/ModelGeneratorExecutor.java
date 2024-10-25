package tools.refinery.generator.server;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.refinery.generator.GeneratorResult;
import tools.refinery.generator.ModelGenerator;
import tools.refinery.generator.ModelGeneratorFactory;
import tools.refinery.generator.ProblemLoader;
import tools.refinery.language.model.problem.Problem;
import tools.refinery.language.web.semantics.PartialInterpretation2Json;
import tools.refinery.language.web.semantics.metadata.MetadataCreator;
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
	private Provider<ModelGeneratorFactory> factoryProvider;
	@Inject
	private Provider<ProblemLoader> problemLoaderProvider;
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
			this.problem = problemLoaderProvider.get().loadString(problemString);
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
		//TODO send to the session, the generation result
	}

	private void sendError(String message){
		//TODO send to the session
	}

	private void sendNodesMetadata(List<tools.refinery.language.web.semantics.metadata.NodeMetadata> metadata) {
		//TODO send to the session
	}

	private void sendRelationsMetadata(List<RelationMetadata> relationsMetadata){
		//TODO send to the session
	}

	private void sendPartialInterpretation(JsonObject partialInterpretation){
		//TODO send to the session
	}

	@Override
	public void run() {
		if (!cancelled) {
			try {
				modelGenerator = factoryProvider.get().createGenerator(problem);
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
			var partialInterpretation = partialInterpretation2Json.getPartialInterpretation(modelGenerator, null);
			sendPartialInterpretation(partialInterpretation);
		}
	}
}

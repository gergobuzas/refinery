package tools.refinery.generator.web.library;

import tools.refinery.language.web.generator.ModelGenerationResult;
import tools.refinery.language.web.xtext.server.push.PushWebDocument;

import java.io.IOException;
import java.util.UUID;

public interface IGenerationWorker {
	public void setState(PushWebDocument state, int randomSeed, long timeoutSec);

	public UUID getUuid();

	public void start();

	public void startTimeout();

	public ModelGenerationResult doRun() throws IOException;

	public void cancel();

	public void cancel(boolean timedOut);

}

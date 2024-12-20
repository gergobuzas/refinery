package tools.refinery.language.web.generator;

import tools.refinery.language.web.xtext.server.push.PushWebDocument;

import java.io.IOException;
import java.util.UUID;

public interface IGenerationWorker {
	public void setState(PushWebDocument state, int randomSeed, long timeoutSec);

	public UUID getUuid();

	public void start();

	public void startTimeout();

	public ModelGenerationResult doRun() throws Exception;

	public void cancel();

	public void cancel(boolean timedOut);

}

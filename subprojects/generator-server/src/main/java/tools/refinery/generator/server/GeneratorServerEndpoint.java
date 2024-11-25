package tools.refinery.generator.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.UUID;


@WebSocket(autoDemand = true)
public class GeneratorServerEndpoint {
	private static final Logger LOG = LoggerFactory.getLogger(GeneratorServerEndpoint.class);
	private Session session;

	@OnWebSocketOpen
	public void onWebSocketOpen(Session session)
	{
		LOG.debug("WebSocket session open! {}", session);
		this.session = session;
	}

	@OnWebSocketMessage
	public void onWebSocketText(String message)
	{
		//Parsing json message
		JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
		var type = jsonMessage.get("type").getAsString();
		var uuid = UUID.fromString(jsonMessage.get("uuid").getAsString());

		if (type.equals("generationRequest")){
			var generationDetailsString = jsonMessage.get("generationDetails").getAsString();
			var generationDetails = JsonParser.parseString(generationDetailsString).getAsJsonObject();
			var randomSeed = generationDetails.get("randomSeed").getAsLong();
			var problemString = generationDetails.get("problem").getAsString();
			ModelGeneratorDispatcher.getInstance().addGenerationRequest(uuid, randomSeed, problemString, session);
		}

		if (type.equals("cancel")){
			ModelGeneratorDispatcher.getInstance().cancelGenerationRequest(uuid);
		}
	}

	@OnWebSocketError
	public void onWebSocketError(Throwable cause)
	{
		// The WebSocket endpoint failed.
		LOG.error("Websocket Error - The cause:", cause);

		// You may log the error.
		cause.printStackTrace();
	}

	@OnWebSocketClose
	public void onWebSocketClose(int statusCode, String reason)
	{
		LOG.debug("WebSocket close! Status:{} - Reason(UUID):{}", statusCode, reason);
		ModelGeneratorDispatcher.getInstance().disconnect(UUID.fromString(reason));
	}
}

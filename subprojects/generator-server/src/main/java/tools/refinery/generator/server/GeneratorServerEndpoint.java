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
		// The WebSocket endpoint has been opened.
		System.out.println("onOpen");

		// Store the session to be able to send data to the remote peer.
		this.session = session;

		// You may immediately send a message to the remote peer.
		//session.sendText("Connected", Callback.NOOP);
	}

	@OnWebSocketMessage
	public void onWebSocketText(String message)
	{
		System.out.println("onTextMessage");

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

		// A WebSocket text message is received.
		//session.sendText(message, Callback.NOOP);

		// Discard the message, and demand for more events.
		//session.demand();
	}

	@OnWebSocketMessage
	public void onWebSocketBinary(ByteBuffer payload, Callback callback)
	{
		// A WebSocket binary message is received.

		// Save only PNG images.
		boolean isPNG = true;
		byte[] pngBytes = new byte[]{(byte)0x89, 'P', 'N', 'G'};
		for (int i = 0; i < pngBytes.length; ++i)
		{
			if (pngBytes[i] != payload.get(i))
			{
				// Not a PNG image.
				isPNG = false;
				break;
			}
		}


		// Complete the callback to release the payload ByteBuffer.
		callback.succeed();

		// Demand for more events.
		//session.demand();
	}

	@OnWebSocketError
	public void onWebSocketError(Throwable cause)
	{
		// The WebSocket endpoint failed.

		// You may log the error.
		cause.printStackTrace();

		// You may dispose resources.
		//disposeResources();
	}

	@OnWebSocketClose
	public void onWebSocketClose(int statusCode, String reason)
	{
		// The WebSocket endpoint has been closed.

		// You may dispose resources.
		//disposeResources();
	}
}

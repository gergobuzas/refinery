package tools.refinery.generator.server;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;


@WebSocket(autoDemand = true)
public class GeneratorServerEndpoint {
	private static final Logger LOG = LoggerFactory.getLogger(GeneratorServerEndpoint.class);
	private HashMap<UUID, Session> sessionHashMap = new HashMap<>();

	@OnWebSocketOpen
	public void onWebSocketOpen(Session session)
	{
		// The WebSocket endpoint has been opened.
		System.out.println("onOpen");

		// Store the session to be able to send data to the remote peer.
		String uuidOfWorkerString = session.getUpgradeRequest().getHeader("UUID");
		UUID uuidOfWorker = UUID.fromString(uuidOfWorkerString);
		sessionHashMap.put(uuidOfWorker, session);

		// You may immediately send a message to the remote peer.
		sessionHashMap.get(uuidOfWorker).sendText("Connected", Callback.NOOP);
	}

	@OnWebSocketMessage
	public void onWebSocketText(String message)
	{
		//TODO Gson read of json, then put the uuid of document to session hashMap with session
		System.out.println("onTextMessage");
		// A WebSocket text message is received.

		// You may echo it back if it matches certain criteria.
		if (message.startsWith("echo:"))
		{
			// Only demand for more events when sendText() is completed successfully.
			//Callback.from(session::demand, Throwable::printStackTrace)
			//session.sendText(message.substring("echo:".length()), Callback.NOOP);
		}
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

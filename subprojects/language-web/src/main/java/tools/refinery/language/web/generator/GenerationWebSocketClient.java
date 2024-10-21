package tools.refinery.language.web.generator;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class GenerationWebSocketClient extends WebSocketClient {

	/*
	* Overloading the initial connect request, so that the Server receives the UUID
	* of the remote worker
	* */
	@Override
	public CompletableFuture<Session> connect(Object websocket, URI toUri) throws IOException {


		return super.connect(websocket, toUri);
	}
}

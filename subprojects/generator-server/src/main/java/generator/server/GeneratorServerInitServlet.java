package generator.server;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.List;

//@ServerEndpoint("/ctx")
public class GeneratorServerInitServlet extends HttpServlet {
	@Override
	public void init() throws ServletException
	{
		try
		{
			// Retrieve the ServerContainer from the ServletContext attributes.
			ServerContainer container = (ServerContainer)getServletContext().getAttribute(ServerContainer.class.getName());

			// Configure the ServerContainer.
			container.setDefaultMaxTextMessageBufferSize(128 * 1024);

			// Simple registration of your WebSocket endpoints.
			container.addEndpoint(GeneratorServerInitServlet.class);

			// Advanced registration of your WebSocket endpoints.
			container.addEndpoint(
					ServerEndpointConfig.Builder.create(GeneratorServerEndpoint.class, "/ws")
							.subprotocols(List.of("my-ws-protocol"))
							.build()
			);
		}
		catch (DeploymentException x)
		{
			throw new ServletException(x);
		}
	}
}

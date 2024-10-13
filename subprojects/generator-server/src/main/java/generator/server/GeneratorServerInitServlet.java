package generator.server;


import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;


public class GeneratorServerInitServlet extends JettyWebSocketServlet {
	@Override
	public void configure(JettyWebSocketServletFactory factory) {
		factory.register(GeneratorServerEndpoint.class);
	}
}

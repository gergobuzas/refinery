package tools.refinery.generator.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

class HealthCheckServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		response.setContentType("text/plain");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().println("Server works!");
	}
}

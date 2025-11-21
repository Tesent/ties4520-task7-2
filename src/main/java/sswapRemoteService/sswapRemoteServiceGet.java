package sswapRemoteService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;

@WebServlet("/rdg")
public class sswapRemoteServiceGet extends HttpServlet {

    // Path to your RDG TTL file relative to project root or classpath
    private static final String RDG_CLASSPATH = "/ontology/booking-service-rdg.ttl";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/turtle; charset=UTF-8");

        // Load RDG from classpath
        InputStream inputStream = getClass().getResourceAsStream(RDG_CLASSPATH);

        if (inputStream == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "RDG file not found in classpath");
            return;
        }

        try (InputStream in = inputStream;
             OutputStream out = response.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
    }
}

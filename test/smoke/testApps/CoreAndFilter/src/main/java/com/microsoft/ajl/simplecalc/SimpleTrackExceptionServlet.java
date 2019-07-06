package com.microsoft.ajl.simplecalc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;

/**
 * Servlet Servlet implementation class SimpleTrackExceptionServlet
 */
@WebServlet(description = "Performs given calculation", urlPatterns = { "/trackException" })
public class SimpleTrackExceptionServlet extends HttpServlet {
    private static final long serialVersionUID = 9009843523432371365L;
    private TelemetryClient client = new TelemetryClient();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletFuncs.geRrenderHtml(request, response);

        Exception exception = new Exception("This is track exception.");

        Map<String, String> properties = new HashMap<String, String>() {
            {
                put("key", "value");
            }
        };
        Map<String, Double> metrics = new HashMap<String, Double>() {
            {
                put("key", 1d);
            }
        };

        client.trackException(exception, properties, metrics);
    }

}

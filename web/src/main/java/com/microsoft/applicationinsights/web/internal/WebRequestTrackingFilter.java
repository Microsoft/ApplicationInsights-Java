/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.web.internal;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.extensibility.ContextInitializer;
import com.microsoft.applicationinsights.web.internal.config.WebReflectionUtils;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.util.ThreadLocalCleaner;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebAppNameContextInitializer;
import com.microsoft.applicationinsights.web.internal.httputils.AIHttpServletListener;
import com.microsoft.applicationinsights.web.internal.httputils.ApplicationInsightsServletExtractor;
import com.microsoft.applicationinsights.web.internal.httputils.HttpServerHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by yonisha on 2/2/2015.
 * <p>You may choose to override the urlPatterns using web.xml</p>
 * <p>For example:</p>
 *
 * {@code
 *  <filter-mapping>
 *      <!-- you must use the same filterName -->
 *      <filter-name>ApplicationInsightsWebFilter</filter-name>
 *      <url-pattern>/onlyTrackThisPath/*</url-pattern>
 *  </filter-mapping>
 * }
 */
public final class WebRequestTrackingFilter implements Filter {
    static {
        WebReflectionUtils.initialize();
    }
    // region Members
    // Visible for testing
    final static String FILTER_NAME = "ApplicationInsightsWebFilter";
    private final static String WEB_INF_FOLDER = "WEB-INF/";

    private WebModulesContainer<HttpServletRequest, HttpServletResponse> webModulesContainer;
    private TelemetryClient telemetryClient;
    private final List<ThreadLocalCleaner> cleaners = new LinkedList<ThreadLocalCleaner>();
    private String appName;
    private String filterName = FILTER_NAME;

    /**
     * Constant for marking already processed request
     */
    private final String ALREADY_FILTERED = "AI_FILTER_PROCESSED";

    /**
     * Utility handler used to instrument request start and end
     */
    HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;


    /**
     * Used to indicate if agent is capture servlet requests.
     */
    private boolean agentCapturingServletRequests;

    // endregion Members

    // region Public

    /**
     * Processing the given request and response.
     *
     * @param req The servlet request.
     * @param res The servlet response.
     * @param chain The filters chain
     * @throws IOException Exception that can be thrown from invoking the filters chain.
     * @throws ServletException Exception that can be thrown from invoking the filters chain.
     */
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {

        if (!(req instanceof HttpServletRequest) || !(res instanceof HttpServletResponse)) {
            // we are only interested in Http Requests. Keep all other untouched.
            chain.doFilter(req, res);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) req;
        HttpServletResponse httpResponse = (HttpServletResponse) res;
        boolean hasAlreadyBeenFiltered = httpRequest.getAttribute(ALREADY_FILTERED) != null;

        // Prevent duplicate Telemetry creation
        if (hasAlreadyBeenFiltered) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        httpRequest.setAttribute(ALREADY_FILTERED, Boolean.TRUE);

        if (agentCapturingServletRequests) {
            handler.handleStartUnderAgent(httpRequest, httpResponse);
            chain.doFilter(httpRequest, httpResponse);
        } else {
            RequestTelemetryContext requestTelemetryContext = handler.handleStart(httpRequest, httpResponse);
            AIHttpServletListener aiHttpServletListener = new AIHttpServletListener(handler, requestTelemetryContext);
            try {
                chain.doFilter(httpRequest, httpResponse);
            } catch (ServletException | IOException | RuntimeException e) {
                handler.handleException(e);
                throw e;
            } finally {
                if (httpRequest.isAsyncStarted()) {
                    AsyncContext context = httpRequest.getAsyncContext();
                    context.addListener(aiHttpServletListener, httpRequest, httpResponse);
                } else {
                    handler.handleEnd(httpRequest, httpResponse, requestTelemetryContext);
                }
            }
        }
    }

    public WebRequestTrackingFilter(String appName) {
        this.appName = appName;
    }

    /**
     * Initializes the filter from the given config.
     *
     * @param config The filter configuration.
     */
    public void init(FilterConfig config) {
        try {
            long start = System.currentTimeMillis();
            appName = extractAppName(config.getServletContext());
            agentCapturingServletRequests = isAgentCapturingServletRequests();
            TelemetryConfiguration configuration = TelemetryConfiguration.getActive();
            if (configuration == null) {
                InternalLogger.INSTANCE.error(
                    "Java SDK configuration cannot be null. Web request tracking filter will be disabled.");
                return;
            }
            configureWebAppNameContextInitializer(appName, configuration);
            telemetryClient = new TelemetryClient(configuration);
            webModulesContainer = new WebModulesContainer<>(configuration);
            // Todo: Should we provide this via dependency injection? Can there be a scenario where user
            // can provide his own handler?
            handler = new HttpServerHandler<>(new ApplicationInsightsServletExtractor(), webModulesContainer,
                                                cleaners, telemetryClient);
            if (StringUtils.isNotEmpty(config.getFilterName())) {
                this.filterName = config.getFilterName();
            }
            long end = System.currentTimeMillis();
            InternalLogger.INSTANCE.trace("Initialized Application Insights Filter in %.3fms", (end - start));
        } catch (Exception e) {
            String filterName = this.getClass().getSimpleName();
            InternalLogger.INSTANCE.info(
                "Application Insights filter %s has failed to initialized.\n" +
                    "Web request tracking filter will be disabled. Exception: %s", filterName, ExceptionUtils.getStackTrace(e));
        }
    }

    private void configureWebAppNameContextInitializer(String appName, TelemetryConfiguration configuration) {
        for (ContextInitializer ci : configuration.getContextInitializers()) {
            if (ci instanceof WebAppNameContextInitializer) {
                ((WebAppNameContextInitializer)ci).setAppName(appName);
                return;
            }
        }
    }

    /**
     * Destroy the filter by releases resources.
     */
    public void destroy() {
    }

    public WebRequestTrackingFilter() {}

    // endregion Public

    // region Private

    private boolean isAgentCapturingServletRequests() {
        Class<?> clazz;
        try {
            clazz = Class.forName("com.microsoft.applicationinsights.agent.internal.utils.Global");
        } catch (ClassNotFoundException e) {
            return false;
        }
        try {
            Method method = clazz.getMethod("isServletInstrumentationEnabled");
            return (Boolean) method.invoke(null);
        } catch (Exception e) {
            InternalLogger.INSTANCE.info(ExceptionUtils.getStackTrace(e));
            return false;
        }
    }

    private String extractAppName(ServletContext context) {
        if (appName != null) {
            return appName;
        }
        String name = null;
        try {
            String contextPath = context.getContextPath();
            if (CommonUtils.isNullOrEmpty(contextPath)) {
                URL[] jarPaths = ((URLClassLoader) (this.getClass().getClassLoader())).getURLs();
                for (URL url : jarPaths) {
                    String urlPath = url.getPath();
                    int index = urlPath.lastIndexOf(WEB_INF_FOLDER);
                    if (index != -1) {
                        urlPath = urlPath.substring(0, index);
                        String[] parts = urlPath.split("/");
                        if (parts.length > 0) {
                            name = parts[parts.length - 1];
                            break;
                        }
                    }
                }
            } else {
                name = contextPath.substring(1);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                InternalLogger.INSTANCE.error("Exception while fetching WebApp name: '%s'", ExceptionUtils.getStackTrace(t));
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }
        return name;
    }
}
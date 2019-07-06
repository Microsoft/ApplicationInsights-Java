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

package com.microsoft.applicationinsights.internal.agent;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.agent.internal.coresync.AgentNotificationsHandler;
import com.microsoft.applicationinsights.agent.internal.coresync.InstrumentedClassType;
import com.microsoft.applicationinsights.agent.internal.coresync.impl.ImplementationsCoordinator;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.schemav2.DependencyKind;
import com.microsoft.applicationinsights.internal.util.LocalStringsUtils;
import com.microsoft.applicationinsights.internal.util.ThreadLocalCleaner;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetryOptions;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.LinkedList;

/**
 * The Core's implementation: the methods are called for instrumented methods.
 * The implementation can measure time in nano seconds, fetch Sql/Http data and report exceptions
 *
 * Created by gupele on 5/7/2015.
 */
final class CoreAgentNotificationsHandler implements AgentNotificationsHandler {

    private final static String EXCEPTION_THROWN_ID = "__java_sdk__exceptionThrown__";

    /**
     * The class holds the data gathered on a method
     */
    private static class MethodData {
        public String name;
        public Object[] arguments;
        public long interval;
        public String type;
        public Object result;
    }

    private static class ThreadData {
        public final LinkedList<MethodData> methods = new LinkedList<MethodData>();
    }

    static final class ThreadLocalData extends ThreadLocal<ThreadData> {
        private ThreadData threadData;

        @Override
        protected ThreadData initialValue() {
            threadData = new ThreadData();
            return threadData;
        }
    };

    private final ThreadLocalCleaner cleaner = new ThreadLocalCleaner() {
        @Override
        public void clean() {
            threadDataThreadLocal.remove();
        }
    };

    private ThreadLocalData threadDataThreadLocal = new ThreadLocalData();

    private TelemetryClient telemetryClient = new TelemetryClient();

    private final String name;

    public ThreadLocalCleaner getCleaner() {
        return cleaner;
    }

    public CoreAgentNotificationsHandler(String name) {
        this.name = name;
    }

    @Override
    public void exceptionCaught(String classAndMethodNames, Throwable throwable) {
        try {
            if (throwable instanceof Exception) {
                telemetryClient.trackException((Exception)throwable);
            }
        } catch (ThreadDeath td) {
        	throw td;
        } catch (Throwable t) {
        }
    }

    @Override
    public void httpMethodStarted(String classAndMethodNames, String url) {
        startMethod(InstrumentedClassType.HTTP.toString(), name, url);
    }

    @Override
    public void sqlStatementExecuteQueryPossibleQueryPlan(String name, Statement statement, String sqlStatement) {
        startSqlMethod(statement, sqlStatement, null);
    }

    @Override
    public void preparedStatementMethodStarted(String classAndMethodNames, PreparedStatement statement, String sqlStatement, Object[] args) {
        startSqlMethod(statement, sqlStatement, args);
    }

    @Override
    public void sqlStatementMethodStarted(String name, Statement statement, String sqlStatement) {
        startSqlMethod(statement, sqlStatement, null);
    }

    @Override
    public void preparedStatementExecuteBatchMethodStarted(String classAndMethodNames, PreparedStatement statement, String sqlStatement, int batchCounter) {
        final String batchData = String.format("Batch of %d", batchCounter);
        startSqlMethod(statement, sqlStatement, new Object[]{batchData});
    }

    public String getName() {
        return name;
    }

    @Override
    public void httpMethodFinished(String identifier, String method, String correlationId, String uri, String target, int result, long deltaInNS)
		throws URISyntaxException {
        if (!LocalStringsUtils.isNullOrEmpty(uri) && (uri.startsWith("https://dc.services.visualstudio.com") || uri.startsWith("https://rt.services.visualstudio.com"))) {
            return;
        }
        long deltaInMS = nanoToMilliseconds(deltaInNS);
        URI uriObject = convertUriStringToUrl(uri);
        String name = method + " " + getRelativePath(uriObject);
        if (target == null) {
        	target = getTargetFromUri(uriObject);
		}

        RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(name, uri, new Duration(deltaInMS), (result < 400));
		Date dependencyStartTime = new Date(System.currentTimeMillis() - deltaInMS);
		telemetry.setTimestamp(dependencyStartTime);
        telemetry.setId(correlationId);
        telemetry.setResultCode(Integer.toString(result));
        telemetry.setType("Http (tracked component)");

        // For Backward Compatibility
		telemetry.getContext().getProperties().put("URI", uri);
		telemetry.getContext().getProperties().put("Method", method);

        if (target != null && !target.isEmpty()) {
            // AI correlation expects target to be of this format.
            target = createTarget(uriObject, target);
            if (telemetry.getTarget() == null) {
                telemetry.setTarget(target);
            } else {
                telemetry.setTarget(telemetry.getTarget() + " | " + target);
            }
        }
       
        InternalLogger.INSTANCE.trace("'%s' sent an HTTP method: '%s', uri: '%s', duration=%s ms", identifier, method, uri, deltaInMS);
        telemetryClient.track(telemetry);
    }

    /**
     * This is used to create Target string to be set in the RDD Telemetry
     * According to spec, we do not include port 80 and 443 in target
     * @param uriObject
     * @return
     */
    private String createTarget(URI uriObject, String incomingTarget) {
        assert uriObject != null;
        String target = uriObject.getHost();
        if (uriObject.getPort() != 80 && uriObject.getPort() != 443) {
            target += ":" + uriObject.getPort();
        }
        target += " | " + incomingTarget;
        return target;
    }

    @Override
    public void jedisMethodStarted(String name) {
        int index = name.lastIndexOf('#');
        if (index != -1) {            
            name = name.substring(0, index);
        }

        startMethod(InstrumentedClassType.Redis.toString(), name, new String[]{});
    }

    @Override
    public void methodStarted(String name) {
        int index = name.lastIndexOf('#');
        String classType;
        if (index != -1) {
            classType = name.substring(index + 1);
            name = name.substring(0, index);
        } else {
            classType = InstrumentedClassType.OTHER.toString();
        }
        startMethod(classType, name, new String[]{});
    }

    @Override
    public void methodFinished(String name, Throwable throwable) {
        if (!finalizeMethod(0, null, throwable)) {
            InternalLogger.INSTANCE.error("Agent has detected a 'Finish' method '%s' with exception '%s' event without a 'Start'",
                    name, throwable == null ? "unknown" : throwable.getClass().getName());
        }
    }

    public void methodFinished(String name, String type, long thresholdInMS) {
        if (!finalizeMethod(thresholdInMS, null, null)) {
            InternalLogger.INSTANCE.error("Agent has detected a 'Finish' method ('%s') event without a 'Start'", name);
        }
    }

    @Override
    public void methodFinished(String name, long thresholdInMS) {
        if (!finalizeMethod(thresholdInMS, null, null)) {
            InternalLogger.INSTANCE.error("Agent has detected a 'Finish' method ('%s') event without a 'Start'", name);
        }
    }

    @Override
    public void methodFinished(String classAndMethodNames, long deltaInNS, Object[] args, Throwable throwable) {
        long durationInMS = nanoToMilliseconds(deltaInNS);
        Duration duration = new Duration(durationInMS);
        Date dependencyStartTime = new Date(System.currentTimeMillis() - durationInMS);
        RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(classAndMethodNames, null, duration, throwable == null);
        telemetry.setTimestamp(dependencyStartTime);
        telemetry.setDependencyKind(DependencyKind.Other);


        if (args != null) {
            String argsAsString = new ArgsFormatter().format(args);
            telemetry.getContext().getProperties().put("Args", argsAsString);
        }

        InternalLogger.INSTANCE.trace("Sending RDD event for '%s', duration=%s ms", classAndMethodNames, durationInMS);

        telemetryClient.track(telemetry);
        if (throwable != null) {
            telemetryClient.trackException(throwable);
        }
    }

    @Override
    public void exceptionThrown(Exception e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void exceptionThrown(Exception e, Integer maxStackSize, Integer maxExceptionTraceLength) {
        ThreadData localData = threadDataThreadLocal.get();
        MethodData methodData = null;
        try {
            if (localData.methods != null && !localData.methods.isEmpty()) {
                for (MethodData m : localData.methods) {
                    if (EXCEPTION_THROWN_ID.equals(m.name)) {
                        return;
                    }
                }
            }

            methodData = new MethodData();
            methodData.interval = 0;
            methodData.type = InstrumentedClassType.OTHER.toString();
            methodData.arguments = null;
            methodData.name = EXCEPTION_THROWN_ID;
            localData.methods.addFirst(methodData);

            ExceptionTelemetry et = new ExceptionTelemetry(e,
                    new ExceptionTelemetryOptions(maxStackSize, maxExceptionTraceLength));

            telemetryClient.track(et);
        } catch (ThreadDeath td) {
        	throw td;
        } catch (Throwable t) {
        }
        if (methodData != null) {
            localData.methods.remove(methodData);
        }
    }

    private URI convertUriStringToUrl(String uri) throws URISyntaxException{
    	return new URI(uri);
	}

	private String getRelativePath(URI uri) {
    	if (uri == null) {
    		return null;
		}
		return uri.getPath();
	}

	private String getTargetFromUri(URI uri) {
    	if (uri == null) {
    		return null;
		}
		return uri.getHost();
	}

    private void startSqlMethod(Statement statement, String sqlStatement, Object[] additionalArgs) {

        try {
            Connection connection = null;
            DatabaseMetaData metaData;
            String url = null;
            if (statement != null) {
                try {
                    connection = statement.getConnection();
                    if (connection != null) {
                        metaData = connection.getMetaData();

                        if (metaData != null) {
                            url = metaData.getURL();
                        }
                    }
                } catch (Throwable t) {
                    try { // can assignment actually throw here?
                        url = "jdbc:Unknown DB URL (failed to fetch from connection)";
                    } catch (ThreadDeath td) {
                        throw td;
                    } catch (Throwable t2) {
                        // chomp
                    }
                }
            }

            Object[] sqlMetaData;
            if (additionalArgs == null) {
                sqlMetaData = new Object[] {url, sqlStatement, connection};
            } else {
                sqlMetaData = new Object[] {url, sqlStatement, connection, additionalArgs};
            }
            startSqlMethod(InstrumentedClassType.SQL.toString(), name, sqlMetaData);
            ThreadData localData = threadDataThreadLocal.get();

        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
        }
    }

    private void startMethod(String type, String name, String... arguments) {
        long start = System.nanoTime();

        ThreadData localData = threadDataThreadLocal.get();
        MethodData methodData = new MethodData();
        methodData.interval = start;
        methodData.type = type;
        methodData.arguments = arguments;
        methodData.name = name;
        localData.methods.addFirst(methodData);
    }

    private void startSqlMethod(String type, String name, Object... arguments) {
        long start = System.nanoTime();

        ThreadData localData = threadDataThreadLocal.get();
        MethodData methodData = new MethodData();
        methodData.interval = start;
        methodData.type = type;
        methodData.arguments = arguments;
        methodData.name = name;
        localData.methods.addFirst(methodData);
    }

    private boolean finalizeMethod(long thresholdInMS, Object result, Throwable throwable) {
        long finish = System.nanoTime();

        ThreadData localData = threadDataThreadLocal.get();
        if (localData.methods == null || localData.methods.isEmpty()) {
            return false;
        }

        MethodData methodData = localData.methods.removeFirst();
        if (methodData == null) {
            return true;
        }

        methodData.interval = finish - methodData.interval;
        if (throwable == null && thresholdInMS > 0) {
            long asMS = nanoToMilliseconds(methodData.interval);
            if (asMS < thresholdInMS){
                return true;
            }
        }
        methodData.result = result;

        report(methodData, throwable);

        return true;
    }

    private void report(MethodData methodData, Throwable throwable) {
        if ("SQL".equalsIgnoreCase(methodData.type)) {
            sendSQLTelemetry(methodData, throwable);
        } else if ("HTTP".equalsIgnoreCase(methodData.type)) {
            sendHTTPTelemetry(methodData, throwable);
        } else {
            sendInstrumentationTelemetry(methodData, throwable);
        }
    }

    private void sendInstrumentationTelemetry(MethodData methodData, Throwable throwable) {
        long durationInMilliSeconds = nanoToMilliseconds(methodData.interval);
        RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(methodData.name, null, new Duration(durationInMilliSeconds), throwable == null);
        telemetry.setType(methodData.type);
        Date dependencyStartDate = new Date(System.currentTimeMillis() - durationInMilliSeconds);
        telemetry.setTimestamp(dependencyStartDate);
        InternalLogger.INSTANCE.trace("Sending RDD event for '%s'", methodData.name);

        telemetryClient.track(telemetry);
        if (throwable != null) {
            telemetryClient.trackException(throwable);
        }
    }

    private void sendHTTPTelemetry(MethodData methodData, Throwable throwable) {
        if (methodData.arguments != null && methodData.arguments.length == 1) {
            String url = methodData.arguments[0].toString();
            long durationInMilliSeconds = nanoToMilliseconds(methodData.interval);
            Duration duration = new Duration(durationInMilliSeconds);

            InternalLogger.INSTANCE.trace("Sending HTTP RDD event, URL: '%s', duration=%s ms", url, durationInMilliSeconds);

            RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(url, null, duration, throwable == null);
            telemetry.setDependencyKind(DependencyKind.Http);
            Date dependencyStartDate = new Date(System.currentTimeMillis() - durationInMilliSeconds);
            telemetry.setTimestamp(dependencyStartDate);
            telemetryClient.trackDependency(telemetry);
            if (throwable != null) {
                telemetryClient.trackException(throwable);
            }
        }
    }

    private void sendSQLTelemetry(MethodData methodData, Throwable throwable) {

        if (methodData.arguments == null || methodData.arguments.length == 0) {
            InternalLogger.INSTANCE.error("sendSQLTelemetry: no arguments found.");
            return;
        }
  
        try {
            String dependencyName = "";
            if (methodData.arguments[0] != null) {
                dependencyName = methodData.arguments[0].toString();
            }   

            String commandName = "";
            if (methodData.arguments.length > 1 && methodData.arguments[1] != null) {
                commandName = methodData.arguments[1].toString();
            }
  
            
            long durationInMilliSeconds = nanoToMilliseconds(methodData.interval);
            Duration duration = new Duration(durationInMilliSeconds);

            RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(
                    dependencyName,
                    commandName,
                    duration,
                    throwable == null);
            telemetry.setDependencyKind(DependencyKind.SQL);
            Date dependencyStartTime = new Date(System.currentTimeMillis() - durationInMilliSeconds);
            telemetry.setTimestamp(dependencyStartTime);
  
            StringBuilder sb = null;
            if (methodData.arguments.length > 3) {
                sb = formatAdditionalSqlArguments(methodData);
                if (sb != null) {
                    telemetry.getContext().getProperties().put("Args", sb.toString());
                }
            } else {
                if (durationInMilliSeconds > ImplementationsCoordinator.INSTANCE.getQueryPlanThresholdInMS()) {
                    sb = fetchExplainQuery(commandName, methodData.arguments[2]);
                    if (sb != null) {
                        telemetry.getContext().getProperties().put("Query Plan", sb.toString());
                    }
                }
            }

            InternalLogger.INSTANCE.trace("Sending Sql RDD event for '%s', command: '%s', duration=%s ms", dependencyName, commandName, durationInMilliSeconds);

            telemetryClient.track(telemetry);
            if (throwable != null) {
                InternalLogger.INSTANCE.trace("Sending Sql exception");
                telemetryClient.trackException(throwable);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                t.printStackTrace();
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }

    }

    private static long nanoToMilliseconds(long nanoSeconds) {
        return nanoSeconds / 1000000;
    }

    private StringBuilder formatAdditionalSqlArguments(MethodData methodData) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(" [");
            Object[] args = (Object[])methodData.arguments[3];
            if (args != null && args.length > 0) {
                for (Object arg : args) {
                    if (arg == null) {
                        sb.append("null,");
                    } else {
                        sb.append(arg.toString());
                        sb.append(',');
                    }
                }
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append(']');
            return sb;
        } catch (Exception e) {
            return null;
        }
    }

    private StringBuilder fetchExplainQuery(String commandName, Object object) {
        StringBuilder explainSB = null;
        Statement explain = null;
        ResultSet rs = null;
        try {
            if (commandName.toLowerCase().startsWith("select ")) {
                Connection connection = (Connection)object;
                if (connection == null) {
                    return explainSB;
                }
                explain = connection.createStatement();
                rs = explain.executeQuery("EXPLAIN " + commandName);
                explainSB = new StringBuilder();
                while (rs.next()) {
                    explainSB.append('[');
                    int columns = rs.getMetaData().getColumnCount();
                    if (columns == 1) {
                        explainSB.append(rs.getString(1));
                    } else {
                        for (int i1 = 1; i1 < rs.getMetaData().getColumnCount(); ++i1) {
                            explainSB.append(rs.getMetaData().getColumnName(i1));
                            explainSB.append(':');
                            Object obj = rs.getObject(i1);
                            explainSB.append(obj == null ? "" : obj.toString());
                            explainSB.append(',');
                        }
                        explainSB.deleteCharAt(explainSB.length() - 1);
                    }
                    explainSB.append("],");
                }
                explainSB.deleteCharAt(explainSB.length() - 1);
            }
        } catch (Throwable t) { // FIXME can this be SQLException?
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                }
            }
            if (explain != null) {
                try {
                    explain.close();
                } catch (SQLException e) {
                }
            }
        }

        return explainSB;
    }
}

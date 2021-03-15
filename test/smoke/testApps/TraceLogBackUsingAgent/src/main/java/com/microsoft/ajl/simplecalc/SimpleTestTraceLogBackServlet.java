package com.microsoft.ajl.simplecalc;

import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/traceLogBack")
public class SimpleTestTraceLogBackServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ServletFuncs.geRrenderHtml(request, response);

        Logger logger = LoggerFactory.getLogger("root");
        logger.trace("This is logback trace.");
        logger.debug("This is logback debug.");
        logger.info("This is logback info.");
        logger.warn("This is logback warn.");
        logger.error("This is logback error.");
    }
}
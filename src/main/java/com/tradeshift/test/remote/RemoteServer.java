package com.tradeshift.test.remote;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import com.tradeshift.test.remote.internal.LineBreakingStream;
import com.tradeshift.test.remote.internal.RedirectingStream;
import com.tradeshift.test.remote.internal.Utils;

public class RemoteServer {

    private static RedirectingStream out;
    private static RedirectingStream err;

    public static void main(String[] args) throws Exception {
        out = new RedirectingStream(System.out);
        System.setOut(new PrintStream(out));
        err = new RedirectingStream(System.err);
        System.setErr(new PrintStream(err));
        
		Options opts = new Options();
		CmdLineParser parser = new CmdLineParser(opts);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
			System.exit(-1);
		}

        Server server = new Server(opts.getPort());

        
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, final HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                try {
                    final Class<?> testClass = getTestClass(request);

                    String method = request.getMethod();
                    final ServletOutputStream pw = response.getOutputStream();
                    if ("POST".equalsIgnoreCase(method)) {
                        response.setStatus(200);
                        
                        final Runner runner = Utils.createRunner(request.getParameter("runner"), testClass);
                        if (request.getParameter("method") != null) {
                            try {
                                Utils.filter(runner, Filter.matchMethodDescription(Description.createTestDescription(testClass, request.getParameter("method"))));
                            } catch (NoTestsRemainException e) {
                                pw.println("RERRORNo tests remaining");
                                return;
                            }
                        }
                        try {
                            TestListener listener = new TestListener();
                            final RunNotifier notifier = new RunNotifier();
                            notifier.addListener(listener);
                            withStream(pw, new Task() {
                                @Override
                                public void run() {
                                    runner.run(notifier);
                                }
                            });
                            pw.println(listener.getResult());
                        } catch (Exception e1) {
                            pw.println("RERROR" + e1);
                        }
                    }

                } catch (ClassNotFoundException e) {
                    response.sendError(500, e.getMessage());
                } finally {
                    baseRequest.setHandled(true);
                }
                
            }

        });
        
        server.start();
        
        System.out.println("Server running at http://localhost:" + opts.getPort());
        server.join();
    }
    
    private static void withStream(final OutputStream os, Task task)  throws Exception{
        try {
            out.setRedirector(new LineBreakingStream('O', os));
            err.setRedirector(new LineBreakingStream('E', os));
            task.run();
        } finally {
            out.setRedirector(null);
            err.setRedirector(null);
        }
        
    }
    
    private static Class<?> getTestClass(HttpServletRequest request) throws ClassNotFoundException {
        String testClassName = request.getPathInfo().substring(request.getPathInfo().lastIndexOf('/') + 1);
        Class<?> testClass = Class.forName(testClassName);
        return testClass;
    }
    
    
    private static class TestListener extends RunListener {
        
        private String result = "RSUCCESS";
        
        public String getResult() {
            return result;
        }
        
        @Override
        public void testFailure(Failure failure) throws Exception {
            result = "RERROR" + failure.getTrace();
        }
    }
    
    private interface Task {
        public void run() throws Exception;
    }
}

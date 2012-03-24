package com.tradeshift.test.remote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.AssertionFailedError;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.test.remote.internal.Utils;

public class RemoteTestRunner extends Runner implements Filterable, Sortable {
    
    private static final Logger log = LoggerFactory.getLogger(RemoteTestRunner.class);

    private Runner delegate;

    private String endpoint;
    private Class<? extends Runner> remoteRunnerClass;

    public RemoteTestRunner(Class<?> clazz) throws InitializationError {
        Remote remote = Utils.findAnnotation(clazz, Remote.class);
        if (remote != null) {
            endpoint = remote.endpoint();
            remoteRunnerClass = remote.runnerClass();
        } else {
            endpoint = "http://localhost:4578/";
            remoteRunnerClass = BlockJUnit4ClassRunner.class;
        }
        log.debug("Trying remote server {} with runner {}", endpoint, remoteRunnerClass.getName());
        URI uri = URI.create(endpoint);
        
        try {
            Socket s = new Socket(uri.getHost(), uri.getPort());
            s.close();
            
            try {
                delegate = new InternalRemoteRunner(clazz);
                log.debug("Using remote server at {}", endpoint);
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            }
        } catch (IOException e) {
            delegate = Utils.createRunner(remoteRunnerClass, clazz);
        }
        
    }
    

    @Override
    public Description getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void run(RunNotifier notifier) {
        delegate.run(notifier);
    }
    
    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        Utils.filter(delegate, filter);
    }
    
    @Override
    public void sort(Sorter sorter) {
        Utils.sort(delegate, sorter);
    }
    
    private class InternalRemoteRunner extends Runner implements Sortable, Filterable {

        private Description description;
        private Map<Description, String> methodNames = new HashMap<Description, String>();
        private final Class<?> testClass;

        public InternalRemoteRunner(Class<?> testClass) {
            this.testClass = testClass;
            TestClass tc = new TestClass(testClass);
            
            description = Description.createTestDescription(testClass, tc.getName(), tc.getAnnotations());
            
            for (FrameworkMethod method : tc.getAnnotatedMethods(Test.class)) {
                String methodName = method.getName();
                Description child = Description.createTestDescription(testClass, methodName, method.getAnnotations());
        
                methodNames.put(child, methodName);
                description.addChild(child);
            }

        }
        
        @Override
        public void filter(Filter filter) throws NoTestsRemainException {
            List<Description> children = description.getChildren();

            Iterator<Description> itr = children.iterator();
            while (itr.hasNext()) {
                Description child = itr.next();
                if (!filter.shouldRun(child)) {
                    itr.remove();
                    methodNames.remove(child);
                }
            }

            if (children.isEmpty()) {
                throw new NoTestsRemainException();
            }
        }

        @Override
        public void sort(Sorter sorter) {
            Collections.sort(description.getChildren(), sorter);
        }

        @Override
        public Description getDescription() {
            return description;
        }

        @Override
        public void run(RunNotifier notifier) {
            notifier.fireTestRunStarted(description);
            ArrayList<Description> children = description.getChildren();
            for (Description description : children) {
                notifier.fireTestStarted(description);
                
                String methodName = methodNames.get(description);
                HttpURLConnection connection = getUrl(methodName, "POST");
                try {
                    connection.connect();
                    handleError(connection);

                    String enc = connection.getContentEncoding();
                    if (enc == null) enc = "ISO-8859-1";
                    InputStream is = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName(enc)));
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("E")) {
                            System.err.println(line.substring(1).trim());
                        } else if (line.startsWith("O")) {
                            System.out.println(line.substring(1).trim());
                        } else if (line.startsWith("RSUCCESS")) {
                            break;
                        } else if (line.startsWith("RERROR")) {
                            StringBuilder error = new StringBuilder(line.substring(6));
                            while ((line = reader.readLine()) != null) {
                                error.append(line).append("\n");
                            }
                            throw new AssertionFailedError(error.toString());
                        } else {
                            log.error("Protocol error in response: {}", line);
                        }
                    }
                    is.close();
                } catch (Throwable e) {
                    notifier.fireTestFailure(new Failure(description, e));
                } finally {
                    notifier.fireTestFinished(description);
                }
                
            }
            
        }
        
        private void handleError(HttpURLConnection connection) throws IOException{
            if (connection.getResponseCode() != 200) {
                String error = null;
                InputStream err = connection.getErrorStream();
                if (err != null) {
                    error = Utils.toString(err);
                }
                if (error == null) {
                    error = connection.getResponseMessage();
                }
                throw new RuntimeException("Unable to send request to " + endpoint + ": " + error);
            }
        }
        
        private HttpURLConnection getUrl(String methodName, String httpMethod) {
            String ep = endpoint;
            if (!ep.endsWith("/")) {
                ep = ep + "/";
            }
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(ep + testClass.getName() + "?method=" + methodName + "&runner=" + remoteRunnerClass.getName()).openConnection();
                connection.setReadTimeout(120000);
                connection.setAllowUserInteraction(false);
                connection.setUseCaches(false);
                connection.setRequestMethod(httpMethod);
                return connection;
            } catch (MalformedURLException e) {
                throw new RuntimeException("Unable to create remote url", e);
            } catch (IOException e) {
                throw new RuntimeException("Unable to connect", e);
            }
        }
        
    }
    

}

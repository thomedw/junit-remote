package com.tradeshift.test.remote.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalRemoteRunner extends Runner implements Sortable, Filterable {
	
	private static final Logger log = LoggerFactory.getLogger(InternalRemoteRunner.class);

	private static List<String> endpoints = new ArrayList<String>();
	private static int currentEndpoint = 0;
    private Description description;
    private Map<Description, String> methodNames = new HashMap<Description, String>();
    private final Class<?> testClass;
    private Class<? extends Runner> remoteRunnerClass;
    private static ExecutorService executorService;
    static {
    	Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				executorService.shutdown();
				try {
					executorService.awaitTermination(1, TimeUnit.MINUTES);
					System.out.println("done");
				} catch (InterruptedException e) {}
			}
		}));
    }

    public InternalRemoteRunner(Class<?> testClass, String endpoint, Class<? extends Runner> remoteRunnerClass) {
        this.testClass = testClass;
		this.remoteRunnerClass = remoteRunnerClass;
        TestClass tc = new TestClass(testClass);
        
        description = Description.createTestDescription(testClass, tc.getName(), tc.getAnnotations());
        
        for (FrameworkMethod method : tc.getAnnotatedMethods(Test.class)) {
            String methodName = method.getName();
            Description child = Description.createTestDescription(testClass, methodName, method.getAnnotations());
    
            methodNames.put(child, methodName);
            description.addChild(child);
        }

        if (executorService == null) {
        	String ep = System.getProperty("junit.remote.endpoint");
        	if (ep == null) {
        		ep = endpoint;
        	}
        	
        	for (String e : ep.split(",")) {
        		if (e.trim().equals("")) continue;
        		endpoints.add(e.trim());
        	}
        	executorService = Executors.newFixedThreadPool(endpoints.size());
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
    public void run(final RunNotifier notifier) {
        notifier.fireTestRunStarted(description);
        ArrayList<Description> children = description.getChildren();
        final CountDownLatch latch = new CountDownLatch(children.size());
        for (final Description description : children) {
            executorService.submit(new Runnable() {
				@Override
				public void run() {
					try {
						notifier.fireTestStarted(description);
						String methodName = methodNames.get(description);
						HttpURLConnection connection = getUrl(methodName, "POST");
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
						connection.disconnect();
					} catch (Throwable e) {
						e.printStackTrace();
						notifier.fireTestFailure(new Failure(description, e));
					} finally {
						notifier.fireTestFinished(description);
						latch.countDown();
					}
				}
			});
        }
        try {
			latch.await(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {}
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
            throw new RuntimeException("Unable to send request to " + connection.getURL() + ": " + error);
        }
    }
    
    private HttpURLConnection getUrl(String methodName, String httpMethod) {
        String ep = endpoints.get(currentEndpoint++ % endpoints.size());
        if (!ep.endsWith("/")) {
            ep = ep + "/";
        }
        try {
        	
            HttpURLConnection connection = (HttpURLConnection) new URL(ep + testClass.getName() + "?method=" + methodName + "&runner=" + remoteRunnerClass.getName()).openConnection();
            connection.setReadTimeout(120000);
            connection.setAllowUserInteraction(false);
            connection.setUseCaches(false);
            connection.setRequestMethod(httpMethod);
            connection.setRequestProperty("Connection", "close");
            return connection;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to create remote url", e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect", e);
        }
    }

}


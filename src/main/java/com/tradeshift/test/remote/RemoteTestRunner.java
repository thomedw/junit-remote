package com.tradeshift.test.remote;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.test.remote.internal.InternalRemoteRunner;
import com.tradeshift.test.remote.internal.Utils;

public class RemoteTestRunner extends Runner implements Filterable, Sortable {
    
    private static final Logger log = LoggerFactory.getLogger(RemoteTestRunner.class);

    private Runner delegate;

//    private String endpoint;
//    private Class<? extends Runner> remoteRunnerClass;

    public RemoteTestRunner(Class<?> clazz) throws InitializationError {
        Remote remote = Utils.findAnnotation(clazz, Remote.class);
        String endpoint;
        Class<? extends Runner> remoteRunnerClass;
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
                delegate = new InternalRemoteRunner(clazz, endpoint, remoteRunnerClass);
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
    

}

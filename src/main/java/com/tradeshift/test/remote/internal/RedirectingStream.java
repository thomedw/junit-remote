package com.tradeshift.test.remote.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class RedirectingStream extends OutputStream {
    
    private final PrintStream delegate;
    private OutputStream redirector;

    public RedirectingStream(PrintStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
        if (redirector != null) {
            redirector.write(b);
        }
    }
    
    public void setRedirector(OutputStream redirector) {
        this.redirector = redirector;
    }
}



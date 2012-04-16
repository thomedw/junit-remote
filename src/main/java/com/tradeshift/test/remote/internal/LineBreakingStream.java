package com.tradeshift.test.remote.internal;

import java.io.IOException;
import java.io.OutputStream;

public class LineBreakingStream extends OutputStream {
    private boolean newline = true;
    
    private OutputStream delegate;
    private final char prefix;
    
    public LineBreakingStream(char prefix, OutputStream delegate) {
        this.prefix = prefix;
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        if (newline && b != '\n' && b != '\r') {
            newline = false;
            delegate.write(prefix);
        }
        delegate.write(b);
        if (b == '\n' || b == '\r') {
            newline = true;
            delegate.flush();
        }
    }
    
}

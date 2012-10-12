package com.tradeshift.test.remote;

import org.kohsuke.args4j.Option;

public class Options {

	@Option(name="-p", usage="Port to listen on")
	private int port = 4578;

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	
}



import huck.simplehttp.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExampleServer {
	public static void main(String... args) throws Exception {
		AtomicBoolean stopSignal = new AtomicBoolean(false);
		int port = 80;
		ExampleHttpProcessor processor = new ExampleHttpProcessor();
		HttpServer server = new HttpServer(processor, 4, new InetSocketAddress(port));
		server.runServer(stopSignal);
	}
}

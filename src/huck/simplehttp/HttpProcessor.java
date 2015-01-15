package huck.simplehttp;

import java.nio.channels.WritableByteChannel;

public interface HttpProcessor {
	public HttpResponse process(HttpRequest req) throws HttpException, Exception;
	public WritableByteChannel getBodyProcessor(HttpRequest req);
}

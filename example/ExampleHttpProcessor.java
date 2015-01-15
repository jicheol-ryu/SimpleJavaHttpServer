import huck.simplehttp.HttpException;
import huck.simplehttp.HttpProcessor;
import huck.simplehttp.HttpRequest;
import huck.simplehttp.HttpResponse;

import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;

public class ExampleHttpProcessor implements HttpProcessor {

	@Override
	public WritableByteChannel getBodyProcessor(HttpRequest req) {
		return null;
	}
	
	@Override
	public HttpResponse process(HttpRequest req) throws HttpException, Exception {
		if( req.getRequestPath().startsWith("/request/") ) {
			StringBuffer buf = new StringBuffer();
			buf.append("Method: ").append(req.getMethod()).append("\n");
			buf.append("Path: ").append(req.getRequestPath()).append("\n");
			buf.append("Query String: ").append(req.getQueryString()).append("\n");
			buf.append("Version: ").append(req.getVersion()).append("\n");
			buf.append("\n");
			for( Map.Entry<String, List<String>> entry : req.getHeaderMap().entrySet() ) {
				for( String value : entry.getValue() ) {
					buf.append(entry.getKey()).append(": ").append(value).append("\n");		
				}
			}
			HttpResponse res = new HttpResponse(HttpResponse.Status.OK, buf.toString().getBytes("UTF-8"));
			res.setHeader("Content-Type", "text/plain; charset=utf8");
			return res;
		}
		File root = new File("example_resources");
		File file = new File(root, req.getRequestPath());
		if( !file.getCanonicalPath().startsWith(root.getCanonicalPath()) || !file.isFile() ) {
			throw new HttpException(HttpResponse.Status.NOT_FOUND, "Not Found: " + req.getRequestPath());
		}
		
		String extension = "";
		int dotIdx = file.getName().lastIndexOf(".");
		if( 0 <= dotIdx ) {
			extension = file.getName().substring(dotIdx);
		}
		String contentType;
		switch( extension ) {
		case "txt": contentType = "text/plain"; break;
		case "html": contentType = "text/html"; break;
		case "htm": contentType = "text/html"; break;
		case "jpg": contentType = "image/jpg"; break;
		case "png": contentType = "image/png"; break;
		case "gif": contentType = "image/gif"; break;
		default: contentType = "application/octet-stream"; break;
		}
		HttpResponse res = new HttpResponse(HttpResponse.Status.OK, ()->new FileInputStream(file).getChannel());
		res.setHeader("ContentType", contentType);
		return res;
	}
}


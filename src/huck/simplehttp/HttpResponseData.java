package huck.simplehttp;

import java.util.List;
import java.util.Map;

class HttpResponseData {
	public String statusCode;
	public String statusString;
	public boolean keepAlive;
	public Map<String, List<String>> headerMap;

	public long contentLength;
	public HttpResponse.BodySupplier bodySupplier;

}

package huck.simplehttp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class HttpRequest {
	// connection data
	public int getPort() {
		return port;
	}
	
	// request line
	public String getMethod() {
		return method;
	}
	public String getRequestURI() {
		return requestURI;
	}
	public String getVersion() {
		return version;
	}
	
	// request line parsed
	public String getHost() {
		return host;
	}
	public String getRequestPath() {
		return requestPath;
	}
	public String getQueryString() {
		return queryString;
	}	
	public String getParameter(String name) {
		if( null == paramMap ) {
			parseQueryString();
		}
		return paramMap.get(name);
	}

	// from header
	public int getContentLength() {
		return contentLength;
	}	
	public List<String> getHeaderList(String name) {
		return header.get(name);	
	}
	public String getHeader(String name) {
		List<String> valueList = header.get(name);
		if( null != valueList && !valueList.isEmpty() ) {
			return valueList.get(0);
		} else {
			return null;
		}
	}
	public Map<String, List<String>> getHeaderMap() {
		return header;
	}	
	
	// cookies
	public List<String> getCookieList(String name) {
		return cookie.get(name);	
	}
	public String getCookie(String name) {
		List<String> valueList = cookie.get(name);
		if( null != valueList && !valueList.isEmpty() ) {
			return valueList.get(0);
		} else {
			return null;
		}
	}
	public Map<String, List<String>> getCookieMap() {
		return cookie;
	}
	
	// attributes
	public Object getAttribute(String name) {
		return attribute.get(name);
	}	
	public Object setAttribute(String name, Object value) {
		return attribute.put(name, value);
	}

	private int port;
	private String method;
	private String requestURI;
	private String version;
	
	private String host;
	private String requestPath;
	private String queryString;
	private Map<String, String> paramMap;
	
	private int contentLength;
	private Map<String, List<String>> header;
	private Map<String, List<String>> cookie;
	
	private HashMap<String, Object> attribute;
	
	public HttpRequest(HttpRequestData parseData) {
		this.port = parseData.port;
		this.method = parseData.method;
		this.requestURI = parseData.uri;
		this.version = parseData.version;
		
		this.host = parseData.host;
		this.requestPath = parseData.path;
		this.queryString = parseData.queryString;
		this.paramMap = null;
		
		this.contentLength = parseData.contentLength;
		
		this.header = null;
		this.cookie = null;
		this.attribute = new HashMap<>();
		
		this.header = new HashMap<String, List<String>>();
		for( Map.Entry<String, ArrayList<String>> entry : parseData.header.entrySet() ) {
			this.header.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		this.header = Collections.unmodifiableMap(this.header);
		
		this.cookie = new HashMap<String, List<String>>();
		for( Map.Entry<String, ArrayList<String>> entry : parseData.cookie.entrySet() ) {
			this.cookie.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		this.cookie = Collections.unmodifiableMap(this.cookie);
	}
	
	private void parseQueryString() {
		paramMap = new HashMap<>();
		if( null == queryString || queryString.isEmpty() ) {
			return;
		}		
		String[] params = queryString.split("&");		
		for( String param : params ) {
			if( null == param || param.isEmpty() ) {
				continue;
			}
			int delimIdx = param.indexOf("=");
			if( 0 > delimIdx ) {
				paramMap.put(param, "");
			} else if (delimIdx > 0) {
				paramMap.put(param.substring(0, delimIdx), param.substring(delimIdx + 1));
			} else { // == 0
				continue;
			}
		}
	}
	
}

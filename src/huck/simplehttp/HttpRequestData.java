package huck.simplehttp;

import java.util.ArrayList;
import java.util.HashMap;

class HttpRequestData {
	public int port = -1;
	public String method = null;
	public String uri = null;
	public String version = null;
	public String host = null;
	public String path = null;
	public String queryString = null;
	public int contentLength = 0;
	
	public HashMap<String, ArrayList<String>> header = new HashMap<>();
	public HashMap<String, ArrayList<String>> cookie = new HashMap<>();
}
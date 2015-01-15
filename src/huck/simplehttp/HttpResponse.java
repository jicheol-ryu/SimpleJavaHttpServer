package huck.simplehttp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HttpResponse {
	public static interface BodySupplier {
		public ReadableByteChannel get() throws Exception;
	}
	public static enum Status {
		OK("200", "OK"),
		PARTIAL_CONTENT("206", "Partial Content"),
		
		MOVED_PERMANENTLY("301", "Moved Permanently"),

		BAD_REQUEST("400", "Bad Request"),
		NOT_FOUND("404", "Not Found"),
		METHOD_NOT_ALLOWED("405", "Method Not Allowd"),
		REQUEST_ENTITY_TOO_LARGE("413", "Request Entity Too Large"),
		REQUESTED_RANGE_NOT_SATISFIABLE("416", "Requested range not satisfiable"),
		
		INTERNAL_SERVER_ERROR("500", "Internal Server Error"),
		HTTP_VERSION_NOT_SUPPORTED("505", "HTTP Version Not Supported"),
		;		
		private String code;
		private String message;
		private Status(String code, String message) {
			this.code = code;
			this.message = message;
		}
		public String code() {
			return code;
		}
		public String message() {
			return message;
		}
	}
	public static class Cookie {
		private String name;
		private String value;
		private String domain;
		private String path;
		private ZonedDateTime expire;
		
		public static Cookie expireCookie(String name) {
			return new Cookie(name, "", Instant.ofEpochMilli(0).atOffset(ZoneOffset.UTC).toZonedDateTime());
		}
		public static Cookie expireCookie(String name, String domain, String path) {
			return new Cookie(name, "", domain, path, Instant.ofEpochMilli(0).atOffset(ZoneOffset.UTC).toZonedDateTime());
		}
		public Cookie(String name, String value) {
			this(name, value, null, null, null);
		}
		public Cookie(String name, String value, ZonedDateTime expire) {
			this(name, value, null, null, expire);
		}
		public Cookie(String name, String value, String domain, String path) {
			this(name, value, domain, path, null);			
		}
		public Cookie(String name, String value, String domain, String path, ZonedDateTime expire) {
			if( null == name || null == value ) {
				throw new NullPointerException();
			}
			this.name = name;
			this.value = value;
			this.domain = domain;
			this.path = path;
			this.expire = expire;
		}
		public String getName() {
			return name;
		}
		public String getValue() {
			return value;
		}
		public String getDomain() {
			return domain;
		}
		public String getPath() {
			return path;
		}
		public ZonedDateTime getExpire() {
			return expire;
		}
		private String getCookieString() {
			StringBuffer buf = new StringBuffer();
			buf.append(name).append("=").append(value).append(";");
			if( null != domain ) {
				buf.append(" domain=").append(domain).append(";");	
			}
			if( null != path ) {
				buf.append(" path=").append(path).append(";");
			}
			if( null != expire ) {
				expire = expire.withZoneSameInstant(ZoneId.of("GMT"));
				buf.append(" expires=").append(expire.format(DateTimeFormatter.RFC_1123_DATE_TIME)).append(";");
			}
			return buf.toString();
		}
	}
	
	private String statusCode;
	private String statusString;
	
	private HashMap<String, ArrayList<String>> headerMap;

	private long contentLength;
	private BodySupplier bodySupplier;
	
	private boolean keepAlive;

	public HttpResponse(Status status) {
		this.headerMap = new HashMap<>();
		this.contentLength = 0;
		this.bodySupplier = null;
		this.keepAlive = true;
		setStatus(status);
	}
	public HttpResponse(Status status, byte[] bodyBytes) {
		this(status);
		setBody(bodyBytes);
	}	
	public HttpResponse(Status status, File file) {
		this(status);
		setBody(file);
	}	
	public HttpResponse(Status status, BodySupplier bodySupplier) {
		this(status);
		setBodySupplier(bodySupplier);
	}
	
	public void setStatus(Status status) {
		this.statusCode = status.code();
		this.statusString = status.message();
	}	
	
	public void disableKeepAlive() {
		keepAlive = false;
	}

	private boolean checkHeader(String key, String value) {
		switch(key.toLowerCase()) {
		case "connection":
		case "content-length":
		case "transfer-encoding":
			return false;
		default:
			return true;
		}
	}
	public void setHeader(String key, String value) {
		if( !checkHeader(key, value) ) {
			return;
		}
		ArrayList<String> valueList = new ArrayList<>();
		valueList.add(value);
		headerMap.put(key, valueList);
	}
	public void addHeader(String key, String value) {
		if( !checkHeader(key, value) ) {
			return;
		}
		ArrayList<String> valueList = headerMap.get(key);
		if( null == valueList ) {
			setHeader(key, value);
		} else {
			valueList.add(value);
		}
	}
	public void addCookie(Cookie cookie) {
		addHeader("Set-Cookie", cookie.getCookieString());
	}

	public void removeBody() {
		this.bodySupplier = null;
		this.contentLength = 0;
	}
	
	public void setBody(byte[] bodyBytes, int offset, int len) {
		removeBody();
		
		byte[] copy = new byte[len];
		System.arraycopy(bodyBytes, offset, copy, 0, len);
		this.bodySupplier = () -> Channels.newChannel(new ByteArrayInputStream(copy));
		this.contentLength = len;
	}
	public void setBody(byte[] bodyBytes) {
		setBody(bodyBytes, 0, bodyBytes.length);
	}
	
	public void setBody(File bodyFile) {
		removeBody();
		this.bodySupplier = () -> new FileInputStream(bodyFile).getChannel();
		this.contentLength = -1;
	}
	
	public void setBodySupplier(BodySupplier bodySupplier) {
		removeBody();
		this.bodySupplier = bodySupplier;
		this.contentLength = -1;
	}
	
	public HttpResponseData getResponseData() {
		HttpResponseData data = new HttpResponseData();
		data.statusCode = statusCode;
		data.statusString = statusString;
		data.keepAlive = keepAlive;
		HashMap<String, List<String>> tmp = new HashMap<>();
		for( Map.Entry<String, ArrayList<String>> entry : headerMap.entrySet() ) {
			tmp.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		data.headerMap = Collections.unmodifiableMap(tmp);

		if( null == bodySupplier ) {
			data.bodySupplier = () -> Channels.newChannel(new ByteArrayInputStream(new byte[0]));
			data.contentLength = 0;
		} else {
			data.contentLength = contentLength;
			data.bodySupplier = bodySupplier;
		}
		return data;
	}
	
	
	static class ReadableByteArrayChannel implements ReadableByteChannel {
		private byte[] src;
		private int position;
		private boolean open;
		public ReadableByteArrayChannel(byte[] src) {
			this.src = src;
			this.position = 0;
			this.open = true;
		}
		@Override
		public boolean isOpen() {
			return open;
		}
		@Override
		public void close() throws IOException {
			open = false;
		}
		@Override
		public int read(ByteBuffer dst) throws IOException {
			int remaining = src.length - position;
			if( 0 >= remaining ) {
				return -1;
			}
			if( 0 == dst.remaining() ) {
				return 0;
			}
			int len = Math.min(dst.remaining(), remaining);
			dst.put(src, position, len);
			position += len;
			return len;
		}
	}
}


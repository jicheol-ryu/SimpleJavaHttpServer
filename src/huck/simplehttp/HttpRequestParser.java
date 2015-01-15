package huck.simplehttp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashSet;

class HttpRequestParser {
	private enum ParsingPhase {
		REQUEST_LINE, HEADER, MESSAGE_BODY
	}
	
	private HttpProcessor bodyProcessorSupplier;
	private int port;
	private LineByteBuffer lineByteBuffer;
	private ParsingPhase phase;
	private HttpRequestData reqParseData;
	private HttpRequest request;
	
	private int bodyProcessedBytes;
	private WritableByteChannel bodyProcessor;
	
	public HttpRequestParser(HttpProcessor bodyProcessorSupplier, int port, int maxDataBufferSize) {
		this.bodyProcessorSupplier = bodyProcessorSupplier;
		this.port = port;
		this.lineByteBuffer = new LineByteBuffer(maxDataBufferSize);		
		this.phase = ParsingPhase.REQUEST_LINE;
		this.reqParseData = null;
		this.request = null;
		
		this.bodyProcessedBytes = 0;
		this.bodyProcessor = null;
	}
	
	public HttpRequest addBytes(byte[] src, int offset, int srcLen) throws HttpException, IOException {
		if( !lineByteBuffer.addBytes(src, offset, srcLen) ) {
			throw new HttpException(HttpResponse.Status.BAD_REQUEST, "Bad Request");
		}
		
		String line = null;
		if( null == reqParseData ) {
			phase = ParsingPhase.REQUEST_LINE;
			
			reqParseData = new HttpRequestData();
			reqParseData.port = port;
			request = null;
			bodyProcessedBytes = 0;
			bodyProcessor = null;
		}
		
		while(true) {
			switch(phase) {
			case REQUEST_LINE :
				if( null == (line=lineByteBuffer.getLine()) ) {
					return null;
				} else {
					parseRequestLine(line);
					phase = ParsingPhase.HEADER;
				}
				break;
			case HEADER :
				if( null == (line=lineByteBuffer.getLine()) ) {
					return null;
				} else {
					if( line.isEmpty() ) {
						phase = ParsingPhase.MESSAGE_BODY;
					} else {
						parseHeader(line);
					}
				}
				break;
			case MESSAGE_BODY :
				if( null == request ) {
					request = new HttpRequest(reqParseData);
				}				
				boolean finished = true;
				int contentLength = reqParseData.contentLength;
				if( bodyProcessedBytes < contentLength ) {
					if( null == bodyProcessorSupplier ) {
						throw new HttpException(HttpResponse.Status.REQUEST_ENTITY_TOO_LARGE, "body messege is not allowed");
					} else if( null == bodyProcessor ) {
						bodyProcessor = bodyProcessorSupplier.getBodyProcessor(request);
					}
					if( null == bodyProcessor ) {
						throw new HttpException(HttpResponse.Status.REQUEST_ENTITY_TOO_LARGE, "body messege is not allowed");
					}
					ByteBuffer bodyPieceBuf = lineByteBuffer.asReadOnlyByteBuffer(contentLength-bodyProcessedBytes);
					while( 0 < bodyPieceBuf.remaining() ) {
						bodyProcessor.write(bodyPieceBuf);
					}
					bodyProcessedBytes += bodyPieceBuf.limit();
					finished = bodyProcessedBytes >= contentLength;
				}
				if( finished ) {
					if( null != bodyProcessor ) {
						bodyProcessor.close();
					}
					reqParseData = null;
					return request;
				} else {
					return null;
				}
			}
		}
	}
	
	private static HashSet<String> supportMethodSet = new HashSet<>();
	private static HashSet<String> supportVersionSet = new HashSet<>();
	static {
		supportMethodSet.add("GET");
		supportMethodSet.add("POST");
		
		supportVersionSet.add("HTTP/1.1");
		supportVersionSet.add("HTTP/1.0");
	}
	
	private void parseRequestLine(String line) throws HttpException {
		int firstSpaceIdx = line.indexOf(' ');
		int lastSpaceIdx = line.lastIndexOf(' ');
		
		if( 0 >= firstSpaceIdx || 0 >= lastSpaceIdx || firstSpaceIdx == lastSpaceIdx || line.length() <= lastSpaceIdx-1 ) {
			throw new HttpException(HttpResponse.Status.BAD_REQUEST, "Bad Request");
		}
		String method = line.substring(0, firstSpaceIdx).trim();
		String uri = line.substring(firstSpaceIdx+1, lastSpaceIdx).trim();
		String version = line.substring(lastSpaceIdx+1).trim();
		
		if( !supportMethodSet.contains(method) ) {
			throw new HttpException(HttpResponse.Status.METHOD_NOT_ALLOWED, method + " is not supported");
		}
		if( !supportVersionSet.contains(version) ) {
			throw new HttpException(HttpResponse.Status.HTTP_VERSION_NOT_SUPPORTED,  version + " is not supported");
		}
		
		String path;
		String host;
		if( uri.startsWith("/") ) {
			host = null;
			path = uri;			
		} else if( uri.startsWith("http://") ) { 
			String tmp = uri.substring("http://".length());
			int firstSlashIdx = tmp.indexOf('/');
			if( 0 > firstSlashIdx ) {
				host = tmp;
				path = "/";
			} else {
				host = tmp.substring(0, firstSlashIdx);
				path = tmp.substring(firstSlashIdx);
			}
			if( host.trim().isEmpty() ) {
				throw new HttpException(HttpResponse.Status.BAD_REQUEST,  "invalid host:"+host);
			}
		} else {
			throw new HttpException(HttpResponse.Status.BAD_REQUEST, "Bad Request " + uri);
		}
		
		String queryString;
		int queryIdx = path.indexOf('?');
		if( 0 <= queryIdx ) {
			queryString = path.substring(queryIdx+1).trim();
			if( queryString.isEmpty() ) {
				queryString = null;
			}
			path = path.substring(0, queryIdx);			
		} else {
			queryString = null;
		}
		reqParseData.method = method;
		reqParseData.uri = uri;
		reqParseData.version = version;
		reqParseData.host = host;
		reqParseData.path = path;
		reqParseData.queryString = queryString;
	}
	
	private void parseHeader(String line) throws HttpException {
		int idx = line.indexOf(':');
		if( 0 >= idx || line.length()-1 <= idx ) {
			throw new HttpException(HttpResponse.Status.BAD_REQUEST, "Bad Request : header");
		}
		String name = line.substring(0, idx).trim();
		String value = line.substring(idx+1).trim();
		
		if( null == value ) return;
		ArrayList<String> valueList = reqParseData.header.get(name);
		if( null == valueList ) {
			valueList = new ArrayList<>();
			reqParseData.header.put(name, valueList);
		}
		valueList.add(value);
		
		// special cases
		if( "Content-Length".equalsIgnoreCase(name) ) {
			try{
				reqParseData.contentLength = Integer.parseInt(value);
			} catch(NumberFormatException ignore){}
		}
		if( "Host".equalsIgnoreCase(name) ) {
			if( null == reqParseData.host ) {
				if( value.trim().isEmpty() ) {
					throw new HttpException(HttpResponse.Status.BAD_REQUEST,  "invalid host:"+value);
				}
				reqParseData.host = value;
			}
		}
		if( "Cookie".equalsIgnoreCase(name) ) {
			int start = 0;
			while( start < value.length() ) {
				int keyvalueIdx = value.indexOf(';', start);
				String cookie;
				if( 0 > keyvalueIdx ) {
					cookie = value.substring(start);
					start = value.length();
				} else {
					cookie = value.substring(start,keyvalueIdx);
					start = keyvalueIdx+1;
				}
				
				int equalIdx = cookie.indexOf("=");
				if( 0 >= equalIdx || value.length()-1 <= equalIdx ) {
					continue;
				}
				String cookieName = cookie.substring(0, equalIdx).trim();
				String cookieValue = cookie.substring(equalIdx+1).trim(); 
				if( null == cookieValue ) continue;
				ArrayList<String> cookieValueList = reqParseData.cookie.get(name);
				if( null == cookieValueList ) {
					cookieValueList = new ArrayList<>();
					reqParseData.cookie.put(cookieName, cookieValueList);
				}
				cookieValueList.add(cookieValue);
			}
		}
	}
	
	private static class LineByteBuffer {
		private byte[] buf;
		private int beginPos;
		private int endPos;
		private int length;
		
		public LineByteBuffer(int capacity) {
			this.buf = new byte[capacity];
			this.beginPos = this.endPos = this.length = 0;
		}
		public boolean addBytes(byte[] src, int offset, int srcLen) {
			int available = buf.length - length;
			if( srcLen > available ) {
				return false;
			}
			if( endPos + srcLen >= buf.length ) {
				System.arraycopy(buf, beginPos, buf, 0, length);
				endPos = length;
				beginPos = 0;
			}
			
			System.arraycopy(src, offset, buf, endPos, srcLen);
			endPos += srcLen;
			length += srcLen;
			return true;
		}
		public String getLine() {
			if( 0 >= length ) return null;
			int linefeedPos = beginPos;
			while( linefeedPos < endPos ) {
				if(buf[linefeedPos] == '\n') {
					break;
				}
				linefeedPos++;
			}
			if( linefeedPos >= endPos ) {
				return null;
			}			
			int lineLength = linefeedPos - beginPos;
			if( linefeedPos > beginPos && '\r' == buf[linefeedPos-1] ) {
				lineLength -= 1;
			}
			
			String line = null;
			if( 0 == lineLength ) {
				line = "";
			} else {
				line = new String(buf, beginPos, lineLength);
			}
			
			length -= (linefeedPos - beginPos + 1);
			beginPos = linefeedPos + 1;
			return line;
		}
		public ByteBuffer asReadOnlyByteBuffer(int maxLen) {
			int bufLength = Math.min(maxLen, length);
			ByteBuffer result = ByteBuffer.wrap(buf, beginPos, bufLength).asReadOnlyBuffer();
			beginPos += bufLength;
			length -= bufLength;
			return result;
		}
	}
}

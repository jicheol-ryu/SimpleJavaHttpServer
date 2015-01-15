package huck.simplehttp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;

public class HttpResponseWriter {
	private HttpResponseData resData;
	private ByteBuffer buffer;
	private ReadableByteChannel srcChannel;
	
	public HttpResponseWriter(HttpResponseData resData, ByteBuffer buffer) throws Exception {
		this.resData = resData;
		this.buffer = buffer;
		this.srcChannel = getResponseOutputChannel();
		buffer.clear();
		buffer.limit(0);
	}
	
	public boolean process(WritableByteChannel output) throws IOException {
		if( 0 >= buffer.remaining() ) {
			buffer.clear();
			int readLen = srcChannel.read(buffer);
			if( 0 <= readLen ) {
				buffer.flip();
			} else {
				return false;
			}
		}
		output.write(buffer);
		return true;
	}
	
	public void close() throws IOException {
		if( null != srcChannel ) {
			srcChannel.close();
			srcChannel = null;
		}
	}
	

	
	private ReadableByteChannel getBodyOutput() throws Exception {
		ReadableByteChannel srcChannel = resData.bodySupplier.get();
		if( null != srcChannel ) {
			if( 0 <= resData.contentLength ) {
				return srcChannel;
			} else {
				return new ChunkedReadableByteChannel(srcChannel);		
			}
		} else {
			throw new Exception();
		}
	}
	
	private byte[] getHeaderOutput() {
		StringBuffer buf = new StringBuffer();
		buf.append("HTTP/1.1 ").append(resData.statusCode).append(" ").append(resData.statusString).append("\r\n");
		if( !resData.keepAlive ) {
			buf.append("Connection: close").append("\r\n");
		}

		if( 0 <= resData.contentLength ) {
			buf.append("Content-Length: ").append(resData.contentLength).append("\r\n");;
		} else {
			buf.append("Transfer-Encoding: chunked").append("\r\n");;
		}
		for( Map.Entry<String, List<String>> entry : resData.headerMap.entrySet() ) {
			for( String value : entry.getValue() ) {
				buf.append(entry.getKey()).append(": ").append(value).append("\r\n");;
			}
		}
		buf.append("\r\n");
		try {
			return buf.toString().getBytes("ISO8859-1");
		} catch (UnsupportedEncodingException ignore) {
			return null;
		}
	}
	
	ReadableByteChannel getResponseOutputChannel() throws Exception {
		final ReadableByteChannel bodyChannel = getBodyOutput();
		final ReadableByteChannel headerChannel = Channels.newChannel(new ByteArrayInputStream(getHeaderOutput()));		
		return new ReadableByteChannel() {
			@Override
			public boolean isOpen() {
				return headerChannel.isOpen() || (bodyChannel.isOpen());
			}
			@Override
			public void close() throws IOException {
				if(headerChannel.isOpen()) {
					headerChannel.close();
				}
				if( bodyChannel.isOpen() ) {
					bodyChannel.close();
				}
			}
			@Override
			public int read(ByteBuffer dst) throws IOException {
				if(headerChannel.isOpen()) {
					int readLen = headerChannel.read(dst);
					if( 0 <= readLen ) {
						return readLen;
					}
					headerChannel.close();
				}
				return bodyChannel.read(dst);
			}
		};
	}
	
	private static class ChunkedReadableByteChannel implements ReadableByteChannel {
		private ReadableByteChannel src;
		private boolean finished;
		public ChunkedReadableByteChannel(ReadableByteChannel src) {
			this.src = src;
			this.finished = false;
		}
		@Override
		public boolean isOpen() {
			return src.isOpen();
		}
		@Override
		public void close() throws IOException {
			src.close();
			finished = true;
		}
		@Override
		public int read(ByteBuffer dst) throws IOException {
			if( finished ) {
				return -1;
			}
			if( 16 > dst.remaining() ) {
				throw new BufferOverflowException();
			}
			int orgPos = dst.position();
			dst.position(orgPos+8);
			dst.limit(dst.limit()-2);
			int readLen = src.read(dst);
			if( 0 > readLen ) {
				dst.position(orgPos);
				dst.limit(dst.limit()+2);
				dst.put((byte)'0');
				dst.put((byte)'\r');
				dst.put((byte)'\n');
				dst.put((byte)'\r');
				dst.put((byte)'\n');
				finished = true;
				return 5;
			} else if( 0 == readLen ) {
				dst.position(orgPos);
				dst.limit(dst.limit()+2);
				return 0;
			} else {
				int readPos = dst.position();
				dst.position(orgPos);
				String hexLen = String.format("%06x\r\n", readLen);
				dst.put(hexLen.getBytes("ISO8859-1"));
				dst.position(readPos);
				dst.limit(dst.limit()+2);
				dst.put((byte)'\r');
				dst.put((byte)'\n');
				return readLen + 10;
			}
		}
	}
}

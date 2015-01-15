package huck.simplehttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

public class HttpServer {
	private HttpProcessor processor;
	private int maxConnection;
	private InetSocketAddress serviceAddr;
	
	public HttpServer(HttpProcessor processor, int maxConnection, InetSocketAddress serviceAddr) throws IOException {
		this.processor = processor;
		this.maxConnection = maxConnection;
		this.serviceAddr = serviceAddr;
	}
	
	public void runServer(AtomicBoolean stopSignal) throws IOException {
		Selector selector = Selector.open();
		try( ServerSocketChannel serverSockCh = ServerSocketChannel.open() ) {
			serverSockCh.configureBlocking(false);
			serverSockCh.socket().bind(serviceAddr);
			serverSockCh.register(selector, SelectionKey.OP_ACCEPT);
			
			int currentConnectionCnt = 0;
			while( !stopSignal.get() ) {
				int selected = selector.select(1000);
				if( 0 >= selected ) {
					continue;
				}
				Iterator<SelectionKey> keyIt = selector.selectedKeys().iterator();
				while( keyIt.hasNext() ) {
					SelectionKey key = keyIt.next();
					keyIt.remove();
					if( key.isAcceptable() ) {
						if( currentConnectionCnt < maxConnection ) {
							ServerSocketChannel ch = (ServerSocketChannel)key.channel();
							SocketChannel connection = ch.accept();
							if( null != connection ) {
								connection.configureBlocking(false);
								ConnectionData connectionData = new ConnectionData();
								connectionData.parser = new HttpRequestParser(processor, connection.socket().getLocalPort(), 10240);
								connectionData.buffer = ByteBuffer.allocate(1024);
								connectionData.resWriter = null;
								SelectionKey newKey = connection.register(selector, SelectionKey.OP_READ);
								newKey.attach(connectionData);
								currentConnectionCnt += 1;
							}
						}
					} else {
						try {
							ConnectionData connData = (ConnectionData)key.attachment();
							SocketChannel sockCh = (SocketChannel)key.channel();
							
							int nextOp;
							if( key.isReadable() ) nextOp = processRead(sockCh, connData);
							else if( key.isWritable() ) nextOp = processWrite(key);
							else throw new Exception("unknown op");
							switch( nextOp ) {
							case SelectionKey.OP_READ:
							case SelectionKey.OP_WRITE:
								key.interestOps(nextOp);
								break;
							default:
								key.cancel();
								sockCh.close();
								if( null != connData.resWriter ) {
									connData.resWriter.close();
								}
								currentConnectionCnt -= 1;
							}
						} catch( Exception ex ) {
							ex.printStackTrace();
							key.cancel();
							key.channel().close();
							
							ConnectionData connData = (ConnectionData)key.attachment();
							if( null != connData.resWriter ) {
								connData.resWriter.close();
							}
							currentConnectionCnt -= 1;
						}
					}
					
				}
			}
		}
	}
	
	private static class ConnectionData {
		HttpRequestParser parser;
		ByteBuffer buffer;
		HttpResponseWriter resWriter;
	}
	
	private int processRead(SocketChannel sockCh, ConnectionData connData) throws IOException {
		HttpRequestParser parser = connData.parser;
		ByteBuffer buffer = connData.buffer;
		
		HttpResponseWriter resWriter = null;
		try {
			buffer.clear();
			int readLen = sockCh.read(buffer);
			if( 0 <= readLen ) {
				buffer.flip();
				HttpRequest req = parser.addBytes(buffer.array(), 0, buffer.limit());
				if( null != req ) {
					Logger.getLogger("http").info("ACCESS: " + req.getMethod() + " " + req.getRequestURI() + "\t" + req.getContentLength() + " bytes");
					HttpResponse res = processor.process(req);
					if( null == res ) {
						throw new HttpException(HttpResponse.Status.NOT_FOUND, "Not Found: " + req.getRequestPath());
					}
					resWriter = new HttpResponseWriter(res.getResponseData(), buffer);
				}
			} else {
				return -1;
			}
		} catch(HttpException ex) {
			Logger.getLogger("http").info(ex.getStatus() + "\t" + ex.getMessage());
			HttpResponse res = new HttpResponse(ex.getStatus(), ex.getMessage().getBytes("UTF-8"));
			res.setHeader("Content-Type", "text/plain; charset=utf-8");
			res.disableKeepAlive();
			try {
				resWriter = new HttpResponseWriter(res.getResponseData(), buffer);
			} catch (Exception ignore) {
				return -1;
			}
		} catch(Exception ex) {
			Logger.getLogger("http").fatal(ex, ex);
			String message = "INTERNAL_SERVER_ERROR: " + ex.getClass().getName();
			if( null != ex.getMessage() ) {
				message += " - " + ex.getMessage();
			}
			HttpResponse res = new HttpResponse(HttpResponse.Status.INTERNAL_SERVER_ERROR, message.getBytes("UTF-8"));
			res.setHeader("Content-Type", "text/plain; charset=utf-8");
			res.disableKeepAlive();
			try {
				resWriter = new HttpResponseWriter(res.getResponseData(), buffer);
			} catch (Exception ignore) {
				return -1;
			}
		}
		if( null != resWriter ) {
			connData.resWriter = resWriter;
			return SelectionKey.OP_WRITE;
		} else {
			return SelectionKey.OP_READ;
		}
	}
	
	private int processWrite(SelectionKey key) throws IOException {
		ConnectionData connData = (ConnectionData)key.attachment();
		HttpResponseWriter resWriter = connData.resWriter;
		SocketChannel sockCh = (SocketChannel)key.channel();
		if( !resWriter.process(sockCh) ) {
			resWriter.close();
			connData.resWriter = null;
			// disconnect. don't support keep-alive mode;
			return -1;
		} else {
			return SelectionKey.OP_WRITE;
		}
	}
}

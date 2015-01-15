package huck.simplehttp;

public class HttpException extends Exception {
	private static final long serialVersionUID = 3635885066540483200L;
	
	private HttpResponse.Status status;
	
	public HttpException(HttpResponse.Status status, String msg) {
		super(msg);
		this.status = status; 
	}
	
	public HttpResponse.Status getStatus() {
		return this.status;
	}
}

package com.coltla.event;

import java.util.EventObject;

public class MessageEvent extends EventObject {

	private String msg;
	
	public MessageEvent(Object source, String message) {
		super(source);
		
		msg = message;
	}

	public String getMessage() {
		return msg;
	}
}

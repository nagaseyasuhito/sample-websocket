package com.github.nagaseyasuhito.sample.websocket;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/echo")
public class EchoEndpoint {
	private static final Logger log = Logger.getLogger(EchoEndpoint.class.getCanonicalName());

	private static final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private Session session;

	private int latestNumber;

	@OnOpen
	public void onOpen(Session session) {
		this.session = session;

		sessions.add(this.session);
	}

	@OnClose
	public void onClose() {
		sessions.remove(this.session);
	}

	@OnMessage
	public void onMessage(String message) {
		this.latestNumber = Integer.parseInt(message);
		try {
			this.session.getBasicRemote().sendText(Integer.toString(this.latestNumber));
		} catch (IOException e) {
			// session closed
			System.out.println(e.getMessage());
		}

		// for (Iterator<Session> iterator = sessions.iterator(); iterator.hasNext();) {
		// Session session = iterator.next();
		//
		// try {
		// synchronized (session) {
		// if (session.isOpen()) {
		// session.getBasicRemote().sendText("server-" + session.getId());
		// }
		// }
		// } catch (IOException e) {
		// iterator.remove();
		// }
		// }
		//
		// try {
		// synchronized (this.session) {
		// this.session.getBasicRemote().sendText(String.format("server-%04d", args));
		// }
		// } catch (IOException e) {
		// }
	}

	@OnError
	public void onError(Throwable t) {
		if (t instanceof IOException) {
			System.out.println(this.latestNumber);
			// session closed
		} else {
			t.printStackTrace();
		}
	}
}

package pb.client;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.ProtocolAlreadyRunning;
import pb.Utils;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Protocol;
import pb.protocols.keepalive.KeepAliveProtocol;
import pb.protocols.session.SessionProtocol;

/**
 * Manages the connection to the server and the client's state.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @author aaron
 *
 */
public class ClientManager extends Manager {
	private static Logger log = Logger.getLogger(ClientManager.class.getName());
	private SessionProtocol sessionProtocol;
	private KeepAliveProtocol keepAliveProtocol;
	private Socket socket;
	private Endpoint endpoint;
	private int reconnectCounter = 0;
	private int flag = 0;
	
	public ClientManager(String host,int port) throws UnknownHostException, IOException {
		try {
			socket=new Socket(InetAddress.getByName(host),port);
			endpoint = new Endpoint(socket,this);
			endpoint.start();
		} catch(ConnectException e){
			e.printStackTrace();
			flag = 1;
		}
		
		// simulate the client shutting down after 2mins
		// this will be removed when the client actually does something
		// controlled by the user
		Utils.getInstance().setTimeout(()->{
			try {
				sessionProtocol.stopSession();
			} catch (EndpointUnavailable e) {
				//ignore...
			}
		}, 120000);
		
		while (reconnectCounter < 10) {
			if (flag != 1) {
				try {
					// just wait for previous thread to terminate
					endpoint.join();
				} catch (InterruptedException e) {
					// just make sure the ioThread is going to terminate
					endpoint.close();
				}
			}
			
			if (flag == 1) {
				log.severe("Connection was refused, flag 1 raised!!");
				log.severe("Reconnecting after 5 seconds...");
				try
			    {
			        Thread.sleep(5000);
			    }
			    catch(InterruptedException e)
			    {
			        Thread.currentThread().interrupt();
			    }

				try {
					log.severe("Attempt:" + Integer.toString(reconnectCounter + 1));
					reconnectCounter++;
					if (reconnectCounter == 10) {
						log.severe("Attempted 10 times, giving up...");
					}
					flag = 0;
					socket=new Socket(InetAddress.getByName(host),port);
					endpoint = new Endpoint(socket,this);
					endpoint.start();
					// if connection was unsucessful, ends here and catch exception
					log.severe("Reconnection successful, resetting attempts!");
					reconnectCounter = 0;
				} catch(ConnectException e){
					e.printStackTrace();
					flag = 1;
				}
			} else if (flag == 2) {
				log.severe("Connection dropped unexpectedly, flag 2 raised!");
				log.severe("Attempt:" + Integer.toString(reconnectCounter));
				log.severe("Reconnecting after 5 seconds...");
				try
			    {
			        Thread.sleep(5000);
			    }
			    catch(InterruptedException e)
			    {
			        Thread.currentThread().interrupt();
			    }

				try {
					flag = 0;
					socket=new Socket(InetAddress.getByName(host),port);
					endpoint = new Endpoint(socket,this);
					endpoint.start();
					log.severe("Connection not refused, resetting attempts!");
					reconnectCounter = 0;
				} catch(ConnectException e){
					e.printStackTrace();
					flag = 1;
				}
				
			} else { // connection without a problem, flag resetted to 0
				break;
			}
		}
		
		try {
			// just wait for previous thread to terminate
			endpoint.join();
		} catch (InterruptedException e) {
			// just make sure the ioThread is going to terminate
			endpoint.close();
		}
		
		Utils.getInstance().cleanUp();
		log.severe("Cleaning up complete!");
	}
	
	/**
	 * The endpoint is ready to use.
	 * @param endpoint
	 */
	@Override
	public void endpointReady(Endpoint endpoint) {
		log.info("connection with server established");
		sessionProtocol = new SessionProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
		keepAliveProtocol = new KeepAliveProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
	}
	
	/**
	 * The endpoint close() method has been called and completed.
	 * @param endpoint
	 */
	public void endpointClosed(Endpoint endpoint) {
		log.info("connection with server terminated");
	}
	
	/**
	 * The endpoint has abruptly disconnected. It can no longer
	 * send or receive data.
	 * @param endpoint
	 */
	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		log.severe("connection with server terminated abruptly");
		flag = 2;
		endpoint.close();
	
	}

	/**
	 * An invalid message was received over the endpoint.
	 * @param endpoint
	 */
	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		log.severe("server sent an invalid message");
		flag = 2;
		endpoint.close();
	}
	

	/**
	 * The protocol on the endpoint is not responding.
	 * @param endpoint
	 */
	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
		log.severe("server has timed out");
		flag = 2;
		endpoint.close();
	}

	/**
	 * The protocol on the endpoint has been violated.
	 * @param endpoint
	 */
	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
		log.severe("protocol with server has been violated: "+protocol.getProtocolName());
		flag = 2;
		endpoint.close();
	}

	/**
	 * The session protocol is indicating that a session has started.
	 * @param endpoint
	 */
	@Override
	public void sessionStarted(Endpoint endpoint) {
		log.info("session has started with server");
		
		// we can now start other protocols with the server
	}

	/**
	 * The session protocol is indicating that the session has stopped. 
	 * @param endpoint
	 */
	@Override
	public void sessionStopped(Endpoint endpoint) {
		log.info("session has stopped with server");
		flag = 2;
		endpoint.close(); // this will stop all the protocols as well
	}
	

	/**
	 * The endpoint has requested a protocol to start. If the protocol
	 * is allowed then the manager should tell the endpoint to handle it
	 * using {@link pb.Endpoint#handleProtocol(Protocol)}
	 * before returning true.
	 * @param protocol
	 * @return true if the protocol was started, false if not (not allowed to run)
	 */
	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		// the only protocols in this system are this kind...
		try {
			((IRequestReplyProtocol)protocol).startAsClient();
			endpoint.handleProtocol(protocol);
			return true;
		} catch (EndpointUnavailable e) {
			// very weird... should log this
			return false;
		} catch (ProtocolAlreadyRunning e) {
			// even more weird... should log this too
			return false;
		}
	}

}


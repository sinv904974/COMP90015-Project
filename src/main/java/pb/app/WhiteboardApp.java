package pb.app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.codec.binary.Base64;

import pb.IndexServer;
import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());
	
	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";
	
	/**
	 * White board map from board name to board object 
	 */
	Map<String,Whiteboard> whiteboards;
	
	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;
	
	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version
	
	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */
	
	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;
	
	/**
	 * Initialize the white board app.
	 * @throws InterruptedException 
	 * @throws UnknownHostException 
	 */
	
	private PeerManager peerManager;
	
	// client manager to server
	private ClientManager clientManagerServer;
	
	// endpoint to server
	private Endpoint serverEndpoint;
	
	// ip:port of this client
	private String ipPort;
	
	// maps whiteboard boardid to set of endpoints of peers (for host)
	private Map<String, Set<Endpoint>> whiteboardListeners = new HashMap<>();
	
	// maps whiteboard name peer is currently listening to set of endpoints of hosts (for peer)
	private Map<String, Endpoint> whiteboardHost = new HashMap<>();
	
	public WhiteboardApp(int peerPort,String whiteboardServerHost, 
			int whiteboardServerPort) throws UnknownHostException, InterruptedException {
		whiteboards=new HashMap<>();

		show(peerport);
		
		// peer manager for this whiteboard
		this.peerManager = new PeerManager(peerPort);
		// client manager handle connection with whiteboard server
		clientManagerServer = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
		
		clientManagerServer.on(PeerManager.peerStarted, (args)->{
			this.serverEndpoint = (Endpoint)args[0];
			log.info("Session with whiteboard server started: "+serverEndpoint.getOtherEndpointId());
			
			// sharingBoard event from server
			this.serverEndpoint.on(WhiteboardServer.sharingBoard, (args2)->{
				String peerName = (String)args2[0];
				String[] parts = peerName.split(":");
				String peerIpPort = parts[0] + ":" + parts[1];
				if (!ipPort.equals(peerIpPort)) {
					log.info("Not local, connecting to: " + peerIpPort);
					// connect to peerIpPort
					try {
						this.peerListen(parts[0], Integer.parseInt(parts[1]), parts[2]);
					} catch (NumberFormatException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				};	
			// unsharingBoard event from server	
			}).on(WhiteboardServer.unsharingBoard, (args2)->{
				String peerName = (String)args2[0];
				// check if board exists, delete if true
				if (whiteboards.containsKey(peerName)) {
					Endpoint hostEndpoint = whiteboardHost.get(peerName);
					hostEndpoint.localEmit(boardDeleted, peerName);
				};
			});
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			log.info("Session with whiteboard server ended: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
        	log.warning("Session with whiteboard server ended in error: "+endpoint.getOtherEndpointId());
		});
		
		// incoming connection from other peers
		peerManager.on(PeerManager.peerStarted, (args)->{
			Endpoint client = (Endpoint)args[0];
			log.info("Session with client started: "+client.getOtherEndpointId());
			
			// listenBoard event
			client.on(listenBoard, (eventArgs)->{
				String requestedBoardName = (String)eventArgs[0];
				log.info("Peer request listening on board: " + requestedBoardName);
				String[] parts = requestedBoardName.split(":");
				
				// update list of listeners endpoints for board
				synchronized(whiteboardListeners) {
					if(!whiteboardListeners.containsKey(parts[2])) {
						whiteboardListeners.put(parts[2], new HashSet<Endpoint>());
					}
					Set<Endpoint> possiblepeers=whiteboardListeners.get(parts[2]);
					possiblepeers.add(client);
				}
				log.info(whiteboardListeners.toString());
			// unlistenBoard event
			}).on(unlistenBoard, (eventArgs)->{
				String requestedBoardName = (String)eventArgs[0];
				log.info("Peer request unlistening on board: " + requestedBoardName);
				String[] parts = requestedBoardName.split(":");
				
				// update list of listeners endpoints for board after removing
				synchronized(whiteboardListeners) {
					if(whiteboardListeners.containsKey(parts[2])) {
						Set<Endpoint> listeners = whiteboardListeners.get(parts[2]);
						listeners.remove(client);
						whiteboardListeners.put(parts[2], listeners);
					}
				}
			// getBoardData event
			}).on(getBoardData, (eventArgs)->{
				String requestedBoardName = (String)eventArgs[0];
				log.info("Peer request to get board data: " + requestedBoardName);
				// get the requestedBoard and send data to client
				String[] parts = requestedBoardName.split(":");
				Whiteboard requestedBoard = whiteboards.get("standalone:" + parts[2]);
				String formattedData = parts[0]+":"+parts[1]+":"+requestedBoard.toString().split(":")[1];
				client.emit(boardData, formattedData);
			// boardPathUpdate event
			}).on(boardPathUpdate, (eventArgs)->{
				String boardPath = (String)eventArgs[0];
				log.info("Received request for board path update from peer!");
				// check version
				String[] parts = boardPath.split("%");
				Whiteboard hostBoard = whiteboards.get("standalone:" + parts[0].split(":")[2]);
				// if equal emit boardPathAccepted and broadcast boardData
				if (hostBoard.getVersion() == Long.parseLong(parts[1])) {
					hostBoard.addPath(new WhiteboardPath(parts[2]), Long.parseLong(parts[1]));
					String formattedData = ipPort + ":" + hostBoard.toString().split(":")[1];
					// refresh drawArea if selected
					if (selectedBoard.equals(hostBoard)) {
						drawSelectedWhiteboard();
					}
					client.emit(boardPathAccepted, boardPath);
					Set<Endpoint> endpoints = whiteboardListeners.get(hostBoard.getName().split(":")[1]);
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardData, formattedData);
					}
				}else {
					// if unequal emit boardError
					client.emit(boardError, "Some error occured at path update.");
				}
			// boardUndoUpdate event
			}).on(boardUndoUpdate, (eventArgs)->{
				String boardUndo = (String)eventArgs[0];
				log.info("Received request for board undo update from peer!");
				// check version
				String[] parts = boardUndo.split("%");
				Whiteboard hostBoard = whiteboards.get("standalone:" + parts[0].split(":")[2]);
				// if equal emit boardUndoAccepted and broadcast boardData
				if (hostBoard.getVersion() == Long.parseLong(parts[1])) {
					hostBoard.undo(Long.parseLong(parts[1]));
					String formattedData = ipPort + ":" + hostBoard.toString().split(":")[1];
					// refresh drawArea if selected
					if (selectedBoard.equals(hostBoard)) {
						drawSelectedWhiteboard();
					}
					client.emit(boardUndoAccepted, boardUndo);
					Set<Endpoint> endpoints = whiteboardListeners.get(hostBoard.getName().split(":")[1]);
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardData, formattedData);
					}
				}else {
					// if unequal emit boardError
					client.emit(boardError, "Some error occured at undo update.");
				}		
			})
			// boardClearUpdate event
			.on(boardClearUpdate, (eventArgs)->{
				String boardClear = (String)eventArgs[0];
				log.info("Received request for board clear update from peer!");
				// check version
				String[] parts = boardClear.split("%");
				Whiteboard hostBoard = whiteboards.get("standalone:" + parts[0].split(":")[2]);
				// if equal emit boardClearAccepted and broadcast boardData
				if (hostBoard.getVersion() == Long.parseLong(parts[1])) {
					hostBoard.clear(Long.parseLong(parts[1]));
					String formattedData = ipPort + ":" + hostBoard.toString().split(":")[1];
					// refresh drawArea if selected
					if (selectedBoard.equals(hostBoard)) {
						drawSelectedWhiteboard();
					}
					client.emit(boardClearAccepted, boardClear);
					Set<Endpoint> endpoints = whiteboardListeners.get(hostBoard.getName().split(":")[1]);
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardData, formattedData);
					}
				}else {
					// if unequal emit boardError
					client.emit(boardError, "Some error occured at clear update.");
				}		
			});
		});
		peerManager.on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			log.info("Session with peer ended: "+endpoint.getOtherEndpointId());
		});
		peerManager.on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
        	log.warning("Session with peer ended in error: "+endpoint.getOtherEndpointId());
		});
		peerManager.on(PeerManager.peerServerManager, (args)->{
			ServerManager serverManager = (ServerManager)args[0];
			serverManager.on(IOThread.ioThread, (args2)->{
				this.ipPort = (String)args2[0];
			});
		});
		
		peerManager.start();		
		clientManagerServer.start();
		
		// wait for everything to close gracefully
		waitToFinish();
	}
	
	/******
	 * 
	 * Utility methods to extract fields from argument strings.
	 * 
	 ******/
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer:port:boardid
	 */
	public static String getBoardName(String data) {
		String[] parts=data.split("%",2);
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return boardid%version%PATHS
	 */
	public static String getBoardIdAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts=data.split("%",3);
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer
	 */
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return port
	 */
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}
	
	/******
	 * 
	 * Methods called from events.
	 * 
	 ******/
	
	// From whiteboard server
	public void shareBoard() {
		// emit event shareBoard to server
		String[] parts = selectedBoard.getName().split(":");
		String name =  this.ipPort + ":" + parts[1];
		this.serverEndpoint.emit(WhiteboardServer.shareBoard, name);
	}
	
	public void unshareBoard() {
		// emit event unshareBoard to server
		String[] parts = selectedBoard.getName().split(":");
		String name =  this.ipPort + ":" + parts[1];
		this.serverEndpoint.emit(WhiteboardServer.unshareBoard, name);
	}
	
	// From whiteboard peer
	// request to listen to peer host (from client-side)
	public void peerListen(String ip, int port, String boardid) throws UnknownHostException, InterruptedException {
		ClientManager clientManager = peerManager.connect(port, ip);
		clientManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			
			// add host endpoint into map
			String hostName = ip + ":" + Integer.toString(port) + ":" + boardid;
			if(!whiteboardHost.containsKey(hostName)) {
				whiteboardHost.put(hostName, endpoint);
			}
			
			log.info("Session with host started: "+endpoint.getOtherEndpointId());
			
			// subscribe to be a listener
			endpoint.emit(listenBoard, hostName);
			// emit event getBoardData to get initial board data
			endpoint.emit(getBoardData, hostName);
			
			// boardData event
			endpoint.on(boardData, (eventArgs)->{
				String formattedData = (String)eventArgs[0];
				log.info("Received board data from peer: " + formattedData);
				// convert the board data to a board
				receiveRemoteBoard(formattedData, endpoint);
			}).on(boardPathAccepted, (eventArgs)->{
				log.info("Board path has been accepted!");
			}).on(boardUndoAccepted, (eventArgs)->{
				log.info("Board undo has been accepted!");
			}).on(boardClearAccepted, (eventArgs)->{
				log.info("Board clear has been accepted!");
			}).on(boardDeleted, (eventArgs)->{
				log.info("Deleting board...");
				String boardToDelete = (String)eventArgs[0];
				deleteBoard(boardToDelete);
				
				// shutdown connection when peer delete the board
				clientManager.shutdown();
				whiteboardHost.remove(ip + ":" + Integer.toString(port) + ":" + boardid);
			}).on(boardError, (eventArgs)->{
				log.info((String)eventArgs[0]);
			});
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			log.info("Session with host ended: "+endpoint.getOtherEndpointId());
			clientManager.shutdown();
			whiteboardHost.remove(ip + ":" + Integer.toString(port) + ":" + boardid);
		}).on(PeerManager.peerError, (args)->{
        	clientManager.shutdown();
        	whiteboardHost.remove(ip + ":" + Integer.toString(port) + ":" + boardid);
		});
		
		clientManager.start();
	}
	
	// update/add remote board, refresh drawArea if selected
	private void receiveRemoteBoard(String data, Endpoint endpoint) {
		String[] parts = data.split("%", 2);
		Whiteboard newBoard = new Whiteboard(parts[0], true);
		newBoard.whiteboardFromString(parts[0], parts[1]);
		newBoard.setHostEndpoint(endpoint);
		this.addBoard(newBoard, false);
		if (selectedBoard.getName().equals(newBoard.getName())) drawSelectedWhiteboard();
	}
	
	/******
	 * 
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 * 
	 ******/
	
	/**
	 * Wait for the peer manager to finish all threads.
	 * @throws InterruptedException 
	 */
	public void waitToFinish() throws InterruptedException {
		peerManager.join();
		peerManager.joinWithClientManagers();
		log.info("Peer Manager gracefully closed!");
		
		clientManagerServer.join(); 
		log.info("Connection with server closed!");
	}
	
	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}
	
	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
				// if host, emit boardDeleted to listeners
				String[] parts = boardname.split(":");
				if (parts[0].equals("standalone")) {
					// if shared, emit boardDeleted to listeners
					if (whiteboard.isShared()) {
						Set<Endpoint> endpoints = whiteboardListeners.get(parts[1]);
						for (Endpoint endpoint: endpoints) {
							endpoint.emit(boardDeleted, ipPort + ":" + parts[1]);
						}
						// remove whiteboard from list
						whiteboardListeners.remove(parts[1]);
						// emit unshareBoard to server
						serverEndpoint.emit(WhiteboardServer.unshareBoard, ipPort + ":" + parts[1]);
					}
				}else { // not host, emit unlistenBoard to unsubscribe
					Endpoint hostEndpoint = whiteboard.getHostEndpoint();
					hostEndpoint.emit(unlistenBoard, boardname);	
				}
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);
	}
	
	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}
	
	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed					
				// if host, broadcast to all client entrypoints
				String[] parts = selectedBoard.getName().split(":");
				if (parts[0].equals("standalone")) {
					if (!selectedBoard.isShared()) return;
					
					// broadcast to all clients using whiteboardListeners
					Set<Endpoint> endpoints = whiteboardListeners.get(parts[1]);
					String formattedData = ipPort + ":" + selectedBoard.toString().split(":")[1];
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardData, formattedData);
					}
				} else { // emit boardPathUpdate to host as remote peer
					Endpoint hostEndpoint = selectedBoard.getHostEndpoint();
					if (!hostEndpoint.equals(null)) {
						String formattedData = selectedBoard.getNameAndVersion() + "%" + currentPath.toString();
						hostEndpoint.emit(boardPathUpdate, formattedData);
					}
				}
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// was accepted locally, so do remote stuff if needed	
				// if host, broadcast to all client entrypoints
				String[] parts = selectedBoard.getName().split(":");
				if (parts[0].equals("standalone")) {
					if (!selectedBoard.isShared()) return;
					
					// broadcast to all clients using whiteboardListeners
					Set<Endpoint> endpoints = whiteboardListeners.get(parts[1]);
					String formattedData = ipPort + ":" + selectedBoard.toString().split(":")[1];
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardData, formattedData);
					}
				} else { // emit boardClearUpdate to host as remote peer
					Endpoint hostEndpoint = selectedBoard.getHostEndpoint();
					if (!hostEndpoint.equals(null)) {
						String formattedData = selectedBoard.getNameAndVersion() + "%";
						hostEndpoint.emit(boardClearUpdate, formattedData);
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// if host, broadcast to all client entrypoints				
				String[] parts = selectedBoard.getName().split(":");
				if (parts[0].equals("standalone")) {
					if (!selectedBoard.isShared()) return;
					
					// broadcast to all clients using whiteboardListeners
					Set<Endpoint> endpoints = whiteboardListeners.get(parts[1]);
					String formattedData = ipPort + ":" + selectedBoard.toString().split(":")[1];
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardData, formattedData);
					}
				} else { // emit boardUndoUpdate to host as remote peer
					Endpoint hostEndpoint = selectedBoard.getHostEndpoint();
					if (!hostEndpoint.equals(null)) {
						String formattedData = selectedBoard.getNameAndVersion() + "%";
						hostEndpoint.emit(boardUndoUpdate, formattedData);
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("undo without a selected board");
		}
	}
	
	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName());
	}
	
	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
        	selectedBoard.setShared(share);
        	
        	if (share == true) {
        		this.shareBoard();
        	}else {
        		this.unshareBoard();
        	}
        	
        } else {
        	log.severe("there is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		log.info("GUI shutting down...");		
		
		// do some final cleanup
        Iterator < Map.Entry<String, Whiteboard> > iterator = whiteboards.entrySet().iterator(); 
	    // Iterate over the HashMap 
	    while (iterator.hasNext()) { 
	
	        // Get the entry at this iteration 
	        Map.Entry<String, Whiteboard> entry = iterator.next(); 
	        
	        String whiteboardKey = entry.getKey();
			String name = whiteboardKey;
    		String[] parts = name.split(":");
    		if (parts[0].equals("standalone")) {
    			if (whiteboards.get(whiteboardKey).isShared()) {
    				log.info("Deleting board: " + whiteboardKey);
    				// emit boardDeleted to listeners
    				Set<Endpoint> endpoints = whiteboardListeners.get(parts[1]);
					for (Endpoint endpoint: endpoints) {
						endpoint.emit(boardDeleted, ipPort + ":" + parts[1]);
					}
					
    				// emit unshareServer to server
					serverEndpoint.emit(WhiteboardServer.unshareBoard, ipPort + ":" + parts[1]);
    			}
    		}	
	    } 
    	
    	// stop hosting
		peerManager.shutdown();
		
		// stop connection with server
		clientManagerServer.shutdown();
	}
	
	

	/******
	 * 
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 * 
	 ******/
	
	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}
	
	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if(modifyingComboBox) return;
					if(boardComboBox.getSelectedIndex()==-1) return;
					String selectedBoardName=(String) boardComboBox.getSelectedItem();
					if(whiteboards.get(selectedBoardName)==null) {
						log.severe("selected a board that does not exist: "+selectedBoardName);
						return;
					}
					selectedBoard = whiteboards.get(selectedBoardName);
					// remote boards can't have their shared status modified
					if(selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
					} else {
						modifyingCheckBox=true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox=false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to delete");
						return;
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};
		
		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {    
	         public void itemStateChanged(ItemEvent e) { 
	            if(!modifyingCheckBox) setShare(e.getStateChange()==1);
	         }    
	      }); 
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
		

		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);
		
		
		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);

		frame.setSize(600, 600);
		
		// create an initial board
		createBoard();
		
		// closing the application
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosing(WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, 
		            "Are you sure you want to close this window?", "Close Window?", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
		        {
		        	guiShutdown();
		            frame.dispose();
		        }
		    }
		});
		
		// show the swing paint result
		frame.setVisible(true);
		
	}
	
	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 * 
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox=true;
				boardComboBox.removeAllItems();
				int anIndex=-1;
				synchronized(whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for(int i=0;i<boards.size();i++) {
						String boardname=boards.get(i);
						boardComboBox.addItem(boardname);
						if(select!=null && select.equals(boardname)) {
							anIndex=i;
						} else if(anIndex==-1 && selectedBoard!=null && 
								selectedBoard.getName().equals(boardname)) {
							anIndex=i;
						} 
					}
				}
				modifyingComboBox=false;
				if(anIndex!=-1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if(whiteboards.size()>0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}
				
			}
		});
	}
	
}

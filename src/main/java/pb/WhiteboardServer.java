package pb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pb.managers.IOThread;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());
	
	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";
	
	// tiffo
	public static final Map<String,Set<String>> sharingMap=new HashMap<>();
	public static final List<Endpoint> endpoints = new ArrayList<Endpoint>();
	
	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;
	
	// tiffo
	private static void initializeConnection(Endpoint endpoint) {
		synchronized(endpoints) {
			endpoints.add(endpoint);
		}
		synchronized(sharingMap) {
			for (String peerport : sharingMap.keySet()) {
				for (String boardid : sharingMap.get(peerport)) {
					endpoint.emit(sharingBoard, peerport+":"+boardid);
				}
			}
		}
	}
	private static void finalizeConnection(Endpoint endpoint) {
		String peerport = endpoint.getOtherEndpointId().replace("/", "");
		synchronized(endpoints) {
			// remove endpoint from list
			endpoints.remove(endpoint);
			synchronized(sharingMap) {
				// for each connecting peer
				//for (Endpoint e : endpoints) {
					// for each board created by this endpoint
					//for (String boardid : sharingMap.get(peerport)) {
						// emit unshare to all connecting peers
						//e.emit(unsharingBoard, peerport+":"+boardid);
					//}
				//}
				// remove this peer from sharingMap
				sharingMap.remove(peerport);
			}
		}
	}
	private static void shareBoard(String peerport, String boardid) {
		synchronized(sharingMap) {
			if(!sharingMap.containsKey(peerport)) {
				sharingMap.put(peerport, new HashSet<String>());
			}
			Set<String> boards=sharingMap.get(peerport);
			boards.add(boardid);
		}
		synchronized(endpoints) {
			for (Endpoint endpoint : endpoints) {
				endpoint.emit(sharingBoard, peerport+":"+boardid);
			}
		}	
	}
	private static void unshareBoard(String peerport, String boardid) {
		synchronized(sharingMap) {
			Set<String> boards=sharingMap.get(peerport);
			boards.remove(boardid);
		}
		synchronized(endpoints) {
			for (Endpoint endpoint : endpoints) {
				endpoint.emit(unsharingBoard, peerport+":"+boardid);
			}
		}	
	}
	
	private static void help(Options options){
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("password",true,"password for server");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;
        
        if(cmd.hasOption("password")) {
        	serverManager = new ServerManager(port,cmd.getOptionValue("password"));
        } else {
        	serverManager = new ServerManager(port);
        }
        
        /**
         * TODO: Put some server related code here.
         */
        
        // start tiffo        
        serverManager.on(ServerManager.sessionStarted, (eventArgs) -> {
        	Endpoint endpoint = (Endpoint)eventArgs[0];
        	log.info("Client session started: "+endpoint.getOtherEndpointId());
        	initializeConnection(endpoint);
        	endpoint.on(shareBoard, (eventArgs2)->{
        		String update = (String) eventArgs2[0];
        		log.info("Received share board: "+update);
        		String[] parts=update.split(":",3);
        		if(parts.length!=3) {
        			endpoint.emit(error,update);
        		} else {
	        		String peerport = parts[0]+":"+parts[1];
	        		shareBoard(peerport, parts[2]);
        		}
        	}).on(unshareBoard, (eventArgs2) -> {
        		String update = (String) eventArgs2[0];
        		log.info("Received unshare board: "+update);
        		String[] parts=update.split(":",3);
        		if(parts.length!=3) {
        			endpoint.emit(error,update);
        		} else {
	        		String peerport = parts[0]+":"+parts[1];
	        		unshareBoard(peerport, parts[2]);
        		}
        	});
        }).on(ServerManager.sessionStopped, (eventArgs) -> {
        	Endpoint endpoint = (Endpoint)eventArgs[0];
        	finalizeConnection(endpoint);
        	log.info("Client session ended: "+endpoint.getOtherEndpointId());
        }).on(ServerManager.sessionError, (eventArgs)->{
        	Endpoint endpoint = (Endpoint)eventArgs[0];
        	log.warning("Client session ended in error: "+endpoint.getOtherEndpointId());
        }).on(IOThread.ioThread, (eventArgs)->{
        	String peerport = (String) eventArgs[0];
        	// we don't need this info, but let's log it
        	log.info("using Internet address: "+peerport);
        });
        // end tiffo
        
        // start up the server
        log.info("Whiteboard Server starting up");
        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();
        
    }

}

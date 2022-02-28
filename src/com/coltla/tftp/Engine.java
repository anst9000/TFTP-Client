package com.coltla.tftp;

import java.util.List;

import org.apache.logging.log4j.core.net.DatagramOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import com.coltla.event.MessageEvent;
import com.coltla.event.MessageListener;
import com.coltla.utils.DataPacket;
import com.coltla.utils.DataParser;

public class Engine {

	private List<MessageListener> msgListeners = new ArrayList<>();
	
	
	private InetAddress serverIP;
	private Direction direction;
	private Mode mode;
	private String fileName;
	
	public enum Direction {
		GET, PUT;
	}
	
	public enum Mode {
		NETASCII, OCTET;
	}

	private static final String ERROR_WRITING_FILE = "-->\tError writing to file. Terminating.";
	private static final String ERROR_CLOSING_FILE = "-->\tError closing file.";

	private static final String ERROR_SENDING_PACKET = "-->\ttError sending data patcket to the server.";
	private static final String ERROR_STREAMING_DATA = "-->\tUnable to stream data.";
	private static final String ERROR_GETTING_SERVER_CONNECTION = "-->\tError getting connection to server.";

	private static final String ERROR_FILE_NOT_FOUND = "-->\tFile not found.";
	private static final String ERROR_WRITE_TO_TEMP_FILE = "-->\tCould not write to temporary file.";
	private static final String ERROR_READING_PACKET = "-->\tError reading data packet.";

	private static final String ERROR_SERVER_REPORTED_ERROR = "-->\tServer reported error.";
	private static final String ERROR_NO_FREE_PORT_FOUND = "-->\tNo free port found.";

	private static final String INFO_PROCESSING_REQUEST = "-->\tProcessing request.";
	private static final String INFO_CREATING_TEMP_FILE = "-->\tCreating temporary file.";
	private static final String INFO_DELETING_TEMP_FILE = "-->\tDeleting temporary file.";

	private static final String INFO_CONNECTING_TO_SERVER = "-->\tConnecting to server...";
	private static final String INFO_CONNECTED_SUCCESS = "-->\tConnected to server.";
	private static final String INFO_CONVERTING_TO_NETASCII = "-->\tConverting to NETASCII...";
	private static final String INFO_CONVERTED_TO_NETASCII = "-->\tConverted to NETASCII.";

	private static final String INFO_READING_DATA_INTO_TEMP_FILE = "-->\tReading file data into temporary file...";
	private static final String INFO_SENDING_FILE_TO_SERVER = "-->\tSending file to server...";
	private static final String INFO_READING_FILE_FROM_SERVER = "-->\tReading file from server...";
	private static final String INFO_CONVERTING_FILE_FROM_NETASCII = "-->\tConvert file from NETASCII to system text...";
	private static final String INFO_RENAMING_FILE = "-->\tRename temp file to target name...";

	private static final String INFO_SUCCESS_DATA_IN_TEMP_FILE = "-->\tData moved into temporary file.";
	private static final String INFO_SUCCESS_READING_FILE = "-->\tFile read";
	private static final String INFO_SUCCESS_TRANSFER_COMPLETE = "-->\tTransfer complete.";
	private static final String INFO_SUCCESS_FILE_CONVERTED = "-->\tFile converted.";

	private static final String PUT_DATA = "-->\tSending data to server.";
	private static final String GET_DATA = "-->\tGetting data from server.";

	private static final int SOCKET_TIMEOUT = 10000;
	private static final byte NULL_BYTE = 0x00;
	
	// TFTP servers listen on port 69 for connections
	public static final int SERVER_DEFAULT_PORT = 69;
	
	// TFTP client ports to use
	private int[] port_range = new int[] {
		50152, 50153, 50154, 50155, 50156, 50157, 50158
	};

	File tempFile;
	FileOutputStream fout;
	
	private DatagramSocket server = null;
	private int serverPort;
	private int clientPort;
	private DatagramPacket packetOut = null;
	private FileInputStream fin;
	private DatagramPacket packetIn;
	
	/**
	 * Entry point to initiate communication with a server.
	 * 
	 * @param serverIP
	 * @param direction
	 * @param mode
	 * @param file
	 */
	public void transfer(InetAddress serverIP, Direction direction, Mode mode, String file) {
		setServerIP(serverIP);
		setDirection(direction);
		setMode(mode);
		setFileName(file);
		
		fireMsgEvent(INFO_PROCESSING_REQUEST);
		
		writeTempFile();
	}
	
	/**
	 * Passed an array of integers, loops through each
	 * looking for a free port with which to create a
	 * datagram socket with. Returns the datagram socket
	 * when the first port is found.
	 * If not found port, throws an exception.
	 * @param ports
	 * @return
	 * @throws IOException
	 */
	private DatagramSocket getPort(int[] ports) throws IOException {
		for (int port : ports) {
			try {
				return new DatagramSocket(port);
			} catch (IOException ex) {
				continue;
			}
		}
		
		// If the application gets here, no port in range was found
		throw new IOException(ERROR_NO_FREE_PORT_FOUND);
	}
	
	/**
	 * Returns the server port number
	 * @return serverPort
	 */
	private int getServerPort() {
		return serverPort;
	}
	
	/**
	 * Sets the server port number
	 * @param serverPort - the serverPort to set
	 */
	private void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
	
	/**
	 * Builds a byte array with a TFTP server request instruction.
	 * @param fileName
	 * @param transMode
	 * @return byte array containing the request
	 * @throws IOException
	 */
	private byte[] buildRrq(String fileName, Mode transferMode) throws IOException {
		byte[] header = new byte[2];
		
		// GET is "01" and PUT is "02"
		header[0] = 0x00;
		
		if (direction == Direction.GET) {
			header[1] = 0x01;
			fireMsgEvent(GET_DATA);
		} else if (direction == Direction.GET) {
			header[1] = 0x02;
			fireMsgEvent(PUT_DATA);
		}

		// Use output stream to join various part of the message
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(header);
		outputStream.write(fileName.getBytes());
		outputStream.write(NULL_BYTE);
		outputStream.write(transferMode.toString().getBytes());
		outputStream.write(NULL_BYTE);
		
		// Convert output stream to byte array
		byte[] result = outputStream.toByteArray();
		outputStream.close();

		return(result);
	}
	
	/**
	 * Builds a Datagram packet using the byte array passed
	 * and assigns it to packetOut.
	 * @param buf
	 */
	private void buildDatagramPacket(byte[] buf) {
		packetOut = new DatagramPacket(buf, buf.length, serverIP, serverPort);
	}
	
	/**
	 * Sends a packet of data to the server using 
	 * the contents of packetOut.
	 * @return true or false depending on result
	 */
	private boolean sendData() {
		try {
			server.send(packetOut);
			fireMsgEvent(INFO_CONNECTED_SUCCESS);
		} catch (IOException ex) {
			// Set error
			fireMsgEvent(ERROR_SENDING_PACKET);
			ex.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	/**
	 * Makes the initial request to the server and 
	 * calls the GET or PUT process method.
	 */
	private void initiateRequest() {
		// Connect to server
		fireMsgEvent(INFO_CONNECTING_TO_SERVER);
		
		try {
			server = getPort(port_range);
			clientPort = server.getLocalPort();
			
			setServerPort(SERVER_DEFAULT_PORT);
			
			try {
				buildDatagramPacket(buildRrq(fileName, mode));
				sendData();
				server.close();
				
				if (getDirection().name().equals("GET")) {
					processGetRequest();
				} else if (getDirection().name().equals("PUT")) {
					processPutRequest();
				}
			} catch (IOException ex) {
				// Close the server connection created above
				server.close();
				
				// Set error and return
				fireMsgEvent(ERROR_STREAMING_DATA);
				ex.printStackTrace();
				return;
			}
		} catch (IOException ex) {
			fireMsgEvent(ERROR_GETTING_SERVER_CONNECTION);
			ex.printStackTrace();
		}
	}

	/**
	 * Copies the file to transmit to a temp file
	 * and then sends the data in the file to the server.
	 */
	private void processPutRequest() {
		// Flag used to indicate when finished processing file
		boolean running;

		// Counter used for retries to get same data from server
		int retry;
		DataPacket dpRecd;
		
		// Data block received from server
		int block;
		
		// Data block expected from server
		int blockCounter = 0;
		byte[] sendData;
		
		// Write file data to temporary file
		try {
			fin = openFile(getFileName());
		} catch (FileNotFoundException ex) {
			fireMsgEvent(ERROR_FILE_NOT_FOUND);
			ex.printStackTrace();
			return;
		}
		
		fireMsgEvent(INFO_READING_DATA_INTO_TEMP_FILE);
		
		// If transfer mode is binary then just do a straight copy
		// of data. If we are using NETASCII then we need to convert
		// from native format to netascii format.
		if (mode.equals(Mode.NETASCII)) {
			if (convertTextFile(fin, fout, direction)) {
				try {
					fin.close();
					fout.close();
				} catch (IOException ex) {
					fireMsgEvent(ERROR_CLOSING_FILE);
					ex.printStackTrace();
				}
			} else {
				// Failed to convert text file, so back out
				try {
					fin.close();
					fout.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}

				return;
			}
		} else {
			// Binary transfer so simply copy data to temp file
			try {
				int b = fin.read();
				while (b != -1) {
					fout.write(b);
					b = fin.read();
				}
			} catch (IOException ex) {
				fireMsgEvent(ERROR_WRITE_TO_TEMP_FILE);
				ex.printStackTrace();
				
				try {
					fout.close();
					fin.close();
				} catch (IOException exc) {
					fireMsgEvent(ERROR_CLOSING_FILE);
					exc.printStackTrace();
				}
				
				return;
			}
		}
		
		fireMsgEvent(INFO_SUCCESS_DATA_IN_TEMP_FILE);
		fireMsgEvent(INFO_SENDING_FILE_TO_SERVER);
		
		try {
			server = new DatagramSocket(clientPort);
			server.setSoTimeout(SOCKET_TIMEOUT);
		} catch (SocketException ex) {
			fireMsgEvent(ERROR_GETTING_SERVER_CONNECTION);
			ex.printStackTrace();
			return;
		}
		
		if (!readData()) {
			// readData() method has already logged message
			server.close();
			return;
		}
		
		setServerIP(packetIn.getAddress());
		setServerPort(packetIn.getPort());
		
		// Read ACK
		dpRecd = new DataPacket(packetIn.getData());
		block = dpRecd.getBlock();
		
		if (dpRecd.isERR()) {
			// Error requesting op from server
			fireMsgEvent(ERROR_SERVER_REPORTED_ERROR);
			server.close();
			return;
		}
		
		try {
			fin = new FileInputStream(tempFile);
		} catch (FileNotFoundException ex) {
			fireMsgEvent(ERROR_FILE_NOT_FOUND);
			server.close();
			ex.printStackTrace();
		}
		
		// Keep processing data until we receive less than
		// DataPacket.MAX_DATA_SIZE bytes of data
		running = true;
		retry = 0;			// Clear the entry counter
		
		while (running) {
			// If we do not receive the expect block then re-send
			// the last packet of data, otherwise send the next
			// packet of data.
			if (block == blockCounter) {
				blockCounter++;
				
				// Load next block of data
				try {
					retry = 0;
					sendData = buildData(blockCounter);
					buildDatagramPacket(sendData);
					
					// Check to see if we are sending data
					// of length DataPacket.MAX_DATA_SIZE.
					// Note we add 4 bytes because the packet has
					// Op code and block added to it.
					// If less than DataPacket.MAX_DATA_SIZE then
					// this is the last packet of data.
					if (sendData.length < DataPacket.MAX_DATA_SIZE + 4) {
						running = false;
					}
				} catch (IOException ex) {
					running = false;
					fireMsgEvent(ERROR_STREAMING_DATA);
					ex.printStackTrace();
					
					try {
						fin.close();
					} catch (IOException exc) {
						fireMsgEvent(ERROR_CLOSING_FILE);
						exc.printStackTrace();
					}
					
					server.close();
					return;
				}
			} else {
				retry++;
				
				if (retry >= 3) {
					running = false;
				}
			}
			
			// Send block of data 
			sendData();
			readData();
			
			dpRecd = new DataPacket(packetIn.getData());
			block = dpRecd.getBlock();
			
			if (dpRecd.isERR()) {
				fireMsgEvent(ERROR_SERVER_REPORTED_ERROR);
				running = false;
			}
		}
		
		fireMsgEvent(INFO_SUCCESS_TRANSFER_COMPLETE);
		server.close();
		
		try {
			fin.close();
		} catch (IOException ex) {
			fireMsgEvent(ERROR_CLOSING_FILE);
			ex.printStackTrace();
		}
	}


	/**
	 * Reads in data from server writes to temp file
	 * then renames temp file to target file name
	 */
	private void processGetRequest() {
		boolean running = true;
		
		try {
			server = new DatagramSocket(clientPort);
			server.setSoTimeout(SOCKET_TIMEOUT);
		} catch (SocketException ex) {
			fireMsgEvent(ERROR_GETTING_SERVER_CONNECTION);
			ex.printStackTrace();
			return;
		}
		
		fireMsgEvent(INFO_READING_FILE_FROM_SERVER);
		if (!readData()) {
			// Error msg set in readData so just return
			server.close();
			return;
		}
		
		setServerIP(packetIn.getAddress());
		setServerPort(packetIn.getPort());
		
		DataPacket dpRecd;
		
		while (running) {
			if (packetIn.getLength() < DataPacket.MAX_DATA_SIZE + 4) {
				ByteArrayOutputStream tempOutputStream = new ByteArrayOutputStream();
				
				if (packetIn.getLength() > 0) {
					try {
						tempOutputStream.write(Arrays.copyOf(packetIn.getData(), packetIn.getLength()));
					} catch (IOException ex) {
						fireMsgEvent(ERROR_STREAMING_DATA);
						server.close();
						ex.printStackTrace();
						return;
					}
				}
				
				dpRecd = new DataPacket(tempOutputStream.toByteArray());
			} else {
				dpRecd = new DataPacket(packetIn.getData());
			}
			
			if (dpRecd.isERR()) {
				// Set error and return
				running = false;
				
				// Output the reason for the error
				fireMsgEvent(ERROR_SERVER_REPORTED_ERROR + " : " + dpRecd.getErrCode() + " - " + dpRecd.getErrorMsg());
				return;
			} else {
				if (dpRecd.getData().length < DataPacket.MAX_DATA_SIZE) {
					// End of file reached
					running = false;
				}
				
				try {
					fout.write(Arrays.copyOf(dpRecd.getData(), dpRecd.getData().length));
				} catch (IOException ex) {
					running = false;
					
					// Set error and return
					fireMsgEvent(ERROR_STREAMING_DATA);
					server.close();
					ex.printStackTrace();
					return;
				}
			}
			
			buildDatagramPacket(buildAck(dpRecd.getBlock()));
			
			if (!sendData()) {
				running = false;
			}
			
			if (running) {
				if (!readData()) {
					running = false;
				}
			}
		}
		
		// Close the file
		try {
			fout.close();
		} catch (IOException ex) {
			fireMsgEvent(ERROR_CLOSING_FILE);
			ex.printStackTrace();
		}
		
		server.close();
		fireMsgEvent(INFO_SUCCESS_READING_FILE);
		
		File tempFile2 = null;
		if (mode.equals(Mode.NETASCII)) {

			fireMsgEvent(INFO_CONVERTING_FILE_FROM_NETASCII);
			
			// Convert temp file from NETASCII to local system ascii.
			// Write data to temp file
			try {
				fin = new FileInputStream(tempFile);
			} catch (FileNotFoundException ex) {
				fireMsgEvent(ERROR_FILE_NOT_FOUND);
				ex.printStackTrace();
				return;
			}
			
			try {
				tempFile2 = getTempFile(new File(fileName));
				fout = new FileOutputStream(tempFile2);
				convertTextFile(fin, fout, getDirection());
				
				fout.close();
				fin.close();
			} catch (IOException ex) {
				// Set error and return
				fireMsgEvent(ERROR_WRITING_FILE);
				ex.printStackTrace();
				return;
			}
			
			fireMsgEvent(INFO_SUCCESS_FILE_CONVERTED);
		} else {
			tempFile2 = tempFile;
		}
		
		// Rename temp file
		fireMsgEvent(INFO_RENAMING_FILE);
		tempFile2.renameTo(new File(fileName));
		fireMsgEvent(INFO_SUCCESS_TRANSFER_COMPLETE);
	}
	
	/**
	 * Build an ACK message
	 * @param counter
	 * @return byte array containing ACK message
	 */
	private byte[] buildAck(int counter) {
		byte[] header = new byte[4];
		
		// ACK is "04"
		header[0] = 0x00;
		header[1] = 0x04;
		
		// Should really throw an error if counter
		// exceeds 4 bytes, 65535
		if (counter < 256) {
			header[2] = 0x00;
			header[3] = Integer.valueOf(counter).byteValue();
		} else {
			header[2] = Integer.valueOf((counter & 0xFF00) >> 8).byteValue();
			header[3] = Integer.valueOf(counter).byteValue();
		}
		
		return header;
	}

	/**
	 * Creates and returns a FileInputStream
	 * from a filename.
	 * @param filename
	 * @return
	 * @throws FileNotFoundException
	 */
	private FileInputStream openFile(String filename) throws FileNotFoundException {
		return new FileInputStream(filename);
	}
	
	/**
	 * Converts between netascii and local system ascii.
	 * Uses direction to determine which way the conversion should be.
	 * The source file is the starting position and the target file the result.
	 * Required because Mac, Windows and Unix systems use different End Of Line terminators.
	 * @param source
	 * @param target
	 * @param direction
	 * @return true or false
	 */
	private boolean convertTextFile(FileInputStream source, FileOutputStream target, Direction direction) {
		fireMsgEvent(INFO_CONVERTING_TO_NETASCII);
		
		DataInputStream dataIn = new DataInputStream(source);
		BufferedReader bufferIn = new BufferedReader(new InputStreamReader(dataIn));
		
		// Get a stream to write to the normalized file
		DataOutputStream dataOut = new DataOutputStream(target);
		BufferedWriter bufferOut = new BufferedWriter(new OutputStreamWriter(dataOut));
		
		String eol = null;
		if (direction.equals(Direction.GET)) {
			eol = DataParser.SYSTEM_STRING_EOL;
		} else if (direction.equals(Direction.PUT)) {
			eol = DataParser.TFTP_STRING_EOL;
		}
		
		// For each line in the source file
		String line;
		try {
			while ((line = bufferIn.readLine()) != null) {
				// Write the original line plus the newline marker
				bufferOut.write(line);
				bufferOut.write(eol);	// Write EOL marker
			}
			
			// Close buffered reader & writer
			bufferIn.close();
			bufferOut.close();
		} catch (IOException ex) {
			fireMsgEvent(ERROR_WRITE_TO_TEMP_FILE);
			ex.printStackTrace();
			return false;
		}
		
		fireMsgEvent(INFO_CONVERTED_TO_NETASCII);
		return true;
	}
	
	/**
	 * Reads a packet of data from the server.
	 * @return boolean
	 */
	private boolean readData() {
		byte[] buf = new byte[DataPacket.MAX_DATA_SIZE + 4];
		packetIn = new DatagramPacket(buf, buf.length);
		
		try {
			server.receive(packetIn);
		} catch (IOException ex) {
			// Set error
			fireMsgEvent(ERROR_READING_PACKET);
			ex.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	/**
	 * Builds a byte array containing data from the source file.
	 * @param counter
	 * @return byte array containing data message
	 * @throws IOException
	 */
	private byte[] buildData(int counter) throws IOException {
		byte[] data = new byte[DataPacket.MAX_DATA_SIZE];
		byte[] header = new byte[4];
		int bytesRead;
		
		// DATA is "03"
		header[0] = 0x00;
		header[1] = 0x03;
		
		// Should really throw an eror if counter exceeds 4 bytes, 65,535
		if (counter < 256) {
			header[2] = 0x00;
			header[3] = Integer.valueOf(counter).byteValue();
		} else {
			header[2] = Integer.valueOf((counter & 0xFF00) >> 8).byteValue();
			header[3] = Integer.valueOf(counter).byteValue();
		}
		
		// Request to read from the file the number of bytes in 'data'
		// Find out how many bytes were actually read, 
		// may be less than size of data.
		bytesRead = fin.read(data);
		
		// Use the output stream to concatenate the two byte arrays
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(header);
		
		// Only write the number of bytes read.
		// The rest of data is filled with NULL.
		outputStream.write(data, 0, bytesRead);
		
		// Convert output stream into byte array.
		byte[] result = outputStream.toByteArray();
		
		outputStream.close();
		
		return result;		
	}

	/**
	 * Creates a temporary file to write to.
	 */
	private void writeTempFile() {
		// Create file
		fireMsgEvent(INFO_CREATING_TEMP_FILE);
		
		try {
			tempFile = getTempFile(new File(fileName));
			fout = new FileOutputStream(tempFile);
		} catch (IOException ex) {
			// Set error and return
			ex.printStackTrace();
			fireMsgEvent(ERROR_WRITING_FILE);
			return;
		}
		
		initiateRequest();
		
		// Close file
		try {
			fout.close();
		} catch (IOException ex) {
			// Set error
			fireMsgEvent(ERROR_CLOSING_FILE);
			ex.printStackTrace();
		}
		
		fireMsgEvent(INFO_DELETING_TEMP_FILE);
		tempFile.delete();
	}

	/**
	 * Creates a temporary filename.
	 * Temporary file names are used to convert
	 * NETASCII files into TFTP standard before
	 * transmitting and to receive files from
	 * server before renaming to final name.
	 * @param source - java.io.File
	 * @return java.io.File
	 * @throws IOException
	 */
	protected File getTempFile(File source) throws IOException {
		File temp = File.createTempFile("xxx-", "tmp");
		
		return temp;
	}

	/**
	 * Class fires messages to provide information on progress.
	 * Any object wishing to be notified of progress should
	 * register as a listener using this method.
	 * 
	 * @param listener
	 */
	public synchronized void addMsgListener(MessageListener listener) {
		msgListeners.add(listener);
	}
	
	/**
	 * Class fires messages to provide information on progress.
	 * Any object wishing to be removed from notification should
	 * remove themselves as a listener using this method.
	 * 
	 * @param listener
	 */
	public synchronized void removeMsgListener(MessageListener listener) {
		msgListeners.remove(listener);
	}
	
	/**
	 * This method is called within the class to notify
	 * listeners of progress and errors.
	 * 
	 * @param msg - the message to send to listeners.
	 */
	private synchronized void fireMsgEvent(String msg) {
		MessageEvent msgEvnt = new MessageEvent(this,  msg);
		
		Iterator<MessageListener> it = msgListeners.iterator();
		
		while (it.hasNext()) {
			((MessageListener) it.next()).sendMessage(msgEvnt);
		}
	}

	/**
	 * Returns the server IP number
	 * @return serverIP
	 */
	public InetAddress getServerIP() {
		return serverIP;
	}

	/**
	 * Sets the server IP number
	 * @param serverIP - the server IP to set
	 */
	public void setServerIP(InetAddress serverIP) {
		this.serverIP = serverIP;
	}

	/**
	 * Returns the direction of transfer,
	 * either retrieve file or send a file to the server.
	 * @return direction
	 */
	public Direction getDirection() {
		return direction;
	}

	/**
	 * Sets the direction of transfer,
	 * either retrieve file from server or
	 * send a file to the server.
	 * @param direction - the direction to set
	 */
	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	/**
	 * Return the mode of transfer,
	 * either Netascii or binary.
	 * @return mode
	 */
	public Mode getMode() {
		return mode;
	}

	/**
	 * Set the mode of transfer,
	 * either Netascii of binary.
	 * @param mode - the mode to set
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	/**
	 * Return the filename of the file to transfer.
	 * @return fileName
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * Set the filename of the file to transfer.
	 * @param fileName - the fileName to set
	 */
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
		
}

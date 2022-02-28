package com.coltla.utils;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * @author Anders
 *
 * Contains the constituent parts of 
 * a TFTP datagram packet
 */
public class DataPacket {

	private static final Logger logger = LogManager.getLogger(DataPacket.class);
	
	public final static String RRQ = "01";
	public final static String WRQ = "02";
	public final static String DATA = "03";
	public final static String ACK = "04";
	public final static String ERR = "05";
	
	public static final int MAX_DATA_SIZE = 512;
	
	protected byte[] data = new byte[0];
	private String errorMsg;
	private String opCode;
	private String mode;
	private String filename;

	private int block;
	private int errCode;
	
	/**
	 * Create object by passing byte array.
	 * First two bytes are used to identify 
	 * opCode and the remaining byte array 
	 * is parsed base on the opCode.
	 */
	public DataPacket(byte[] data) {
		super();
		
		if (data.length >= 2) {
			logger.debug("Parsing op code");
			
			// First two bytes will be the operation code
			Integer code1 = new Integer(data[0]);
			Integer code2 = new Integer(data[1]);
			
			opCode = new String(code1.toString() + code2.toString());
			logger.debug("Checking for op code: " + opCode);
			
			switch (opCode) {
			case RRQ:
				parseRRQ(data);
				break;
			case WRQ:
				parseWRQ(data);
				break;
			case DATA:
				parseDATA(data);
				break;
			case ACK:
				parseACK(data);
				break;
			case ERR:
				parseERR(data);
				break;
			default:
				logger.error("Invalid opCode: " + opCode);
				throw new IllegalArgumentException("Invalid opCode: " + opCode);
			}
		} else {
			logger.error("Invalid byte array length: " + data.length);
			throw new IllegalArgumentException("Invalid byte array length: " + data.length);
		}
	}
	
	/**
	 * Parses Read Request byte array
	 * 0-1	opCode
	 * 2-x	(terminated by 0x00)
	 * x-y	(terminated by 0x00)
	 * @param data
	 */
	private void parseRRQ(byte[] data) {
		logger.debug("Parsing RRQ");
		
		int posEndOfFilename = -1;
		int posEndOfMode = -1;
		
		int i;		
		for (i = 2; i < data.length; i++) {
			posEndOfFilename = i;
			
			if (data[i] == 0x00) {
				break;
			}
		}
		
		setFilename(new String(Arrays.copyOfRange(data, 2, posEndOfFilename)));
		
		for (i = posEndOfFilename + 1; i < data.length; i++) {
			posEndOfMode = i;

			if (data[i] == 0x00) {
				break;
			}
		}
		
		setMode(new String(Arrays.copyOfRange(data, posEndOfFilename + 1, posEndOfMode)));
	}
	
	/**
	 * Parses Write Request byte array
	 * 0-1	opCode
	 * 2-x	(terminated by 0x00)
	 * x-y	(terminated by 0x00)
	 * @param data
	 */
	private void parseWRQ(byte[] data) {
		logger.debug("Parsing WRQ");
		
		int posEndOfFilename = -1;
		int posEndOfMode = -1;
		
		int i;		
		for (i = 2; i < data.length; i++) {
			posEndOfFilename = i;
			
			if (data[i] == 0x00) {
				break;
			}
		}
		
		setFilename(new String(Arrays.copyOfRange(data, 2, posEndOfFilename)));
		
		for (i = posEndOfFilename + 1; i < data.length; i++) {
			posEndOfMode = i;

			if (data[i] == 0x00) {
				break;
			}
		}
		
		setMode(new String(Arrays.copyOfRange(data, posEndOfFilename + 1, posEndOfMode)));
	}
	
	/**
	 * Parse Data Request byte array
	 * 0-1	opCode
	 * 2-3	block, the sequence number of data block
	 * 4-x	(up to 512 bytes)
	 * @param data
	 */
	private void parseDATA(byte[] data) {
		logger.debug("Parsing DATA");
		
		// Set block value
		Integer code1 = new Integer(data[2]);
		Integer code2 = new Integer(data[3]);
		code1 = code1 << 8;
		block = code1 + code2;
		
		// Set data
		setData(Arrays.copyOfRange(data, 4, data.length));
	}
	
	/**
	 * Parse Acknowledge Request byte array
	 * 0-1	opCode
	 * 2-3	block, the sequence number of data block
	 * @param data
	 */
	private void parseACK(byte[] data) {
		logger.debug("Parsing DATA");
		
		// Set block value
		Integer code1 = new Integer(data[2]);
		Integer code2 = new Integer(data[3]);
		code1 = code1 << 8;
		block = code1 + code2;		
	}

	/**
	 * Parse Error Request byte array
	 * 0-1	opCode
	 * 2-3	error code
	 * 2-x	(terminated by 0x00)
	 * @param data
	 */
	private void parseERR(byte[] data) {
		logger.debug("Parsing ERR");
		
		// Set block value
		Integer code1 = new Integer(data[2]);
		Integer code2 = new Integer(data[3]);
		code1 = code1 << 8;
		errCode = code1 + code2;
		
		// Set data
		setErrorMsg(new String(Arrays.copyOfRange(data,4, data.length)));
	}

	/**
	 * @param data - the data to set
	 */
	private void setData(byte[] data) {
		this.data = data;
	}
	
	/**
	 * @param errorMsg - the errorMsg to set
	 */
	private void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}
	
	/**
	 * @param filename - the filename to set
	 */
	private void setFilename(String filename) {
		this.filename = filename;
	}
	
	/**
	 * @param mode - the mode to set
	 */
	private void setMode(String mode) {
		this.mode = mode;
	}
	
	/**
	 * @return the data
	 */
	public byte[] getData() {
		return data;
	}
	
	/**
	 * @return the errorMsg
	 */
	public String getErrorMsg() {
		return errorMsg;
	}
	
	/**
	 * @return the opCode
	 */
	public String getOpCode() {
		return opCode;
	}
	
	/**
	 * @return the mode
	 */
	public String getMode() {
		return mode;
	}
	
	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}
	
	/**
	 * @return the block
	 */
	public int getBlock() {
		return block;
	}
	
	/**
	 * @return the errCode
	 */
	public int getErrCode() {
		return errCode;
	}
	
	/**
	 * Check request is a read request
	 * @return true or false
	 */
	public boolean isRRQ() {
		if (getOpCode() != null) {
			if (getOpCode().equals(RRQ)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Check request is a write request
	 * @return true or false
	 */
	public boolean isWRQ() {
		if (getOpCode() != null) {
			if (getOpCode().equals(WRQ)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Check request is an Acknowledgement
	 * @return true or false
	 */
	public boolean isACK() {
		if (getOpCode() != null) {
			if (getOpCode().equals(ACK)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Check request is an Error
	 * @return true or false
	 */
	public boolean isERR() {
		if (getOpCode() != null) {
			if (getOpCode().equals(ERR)) {
				return true;
			}
		}
		
		return false;
	}

}

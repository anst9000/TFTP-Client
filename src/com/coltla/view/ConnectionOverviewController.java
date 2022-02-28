package com.coltla.view;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import javafx.fxml.FXML;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.coltla.event.MessageEvent;
import com.coltla.event.MessageListener;
import com.coltla.tftp.Engine;
import com.coltla.tftp.Engine.Direction;
import com.coltla.tftp.Engine.Mode;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;

public class ConnectionOverviewController implements MessageListener {

	@FXML
	private TextField serverIPField;
	@FXML
	private ChoiceBox<String> modeField;
	@FXML
	private ChoiceBox<String> directionField;
	@FXML
	private TextField fileNameField;
	@FXML
	private TextArea statusField;
	
	/**
	 * Initializes the controller class.
	 * This method is called automatically
	 * after the fxml file has been loaded.
	 */
	@FXML
	private void initialize() {
		// Set the default value of Server IP to '127.0.0.1'
		serverIPField.setText("127.0.0.1");
		// Set the default value of file name to 'test.txt'
		fileNameField.setText("test.txt");
		
		// Set the popup tool tip for the choice field
		modeField.setTooltip(new Tooltip("Select Ascii if the file is text otherwise use binary"));
		modeField.getItems().removeAll(modeField.getItems());
		
		// Set the options for the field
		modeField.getItems().addAll("Ascii", "Binary");
		// By default select an option
		modeField.getSelectionModel().select("Binary");
		
		directionField.setTooltip(new Tooltip("Send (PUT) or receive (GET) file from the server"));
		directionField.getItems().removeAll(modeField.getItems());
		directionField.getItems().addAll("GET", "PUT");
		directionField.getSelectionModel().select("GET");
		
		// Set the focus for the cursor to be
		// the first field at the top of the scene.
		// This is the set after all the items are 
		// configured and rendered.
		Platform.runLater(new Runnable() {
			
			@Override
			public void run() {
				serverIPField.requestFocus();
			}
		});
	}
	
	/**
	 * Called when the user clicks clear.
	 */
	@FXML
	private void handleClear() {
		serverIPField.setText("");
		modeField.valueProperty().set(null);
		directionField.valueProperty().set(null);
		fileNameField.setText("");
		statusField.clear();
		
		serverIPField.requestFocus();
	}
	
	/**
	 * Called when the user clicks go.
	 */
	@FXML
	private void handleGo() {

		if (isInputValid()) {
			// Call the TFTP server
			statusField.clear();
			statusField.appendText("-->\tCalling TFTP server...\n");
			
			Direction direction = (directionField.getSelectionModel().getSelectedItem().equals("PUT") ? Engine.Direction.PUT : Engine.Direction.GET);
			Mode mode = (modeField.getValue().equals("Ascii") ? Engine.Mode.NETASCII : Engine.Mode.OCTET);
			
			try {
				Engine tftpEngine = new Engine();
				tftpEngine.addMsgListener(this);
				tftpEngine.transfer(InetAddress.getByName(serverIPField.getText()), direction, mode, fileNameField.getText());
			} catch (UnknownHostException ex) {
				statusField.appendText("ERROR Calling TFTP server.");
				ex.printStackTrace();
			}
		}		
	}
	
	/**
	 * Check if user input is valid.
	 * 
	 * @return true if the input is valid
	 */
	private boolean isInputValid() {
		System.out.println("in isInputValid");
		StringBuilder sb = new StringBuilder();
		boolean isValid = true;
		
		if (serverIPField.getText() == null || serverIPField.getText().length() == 0) {
			sb.append("表tNo valid server IP.\n");
			isValid = false;
		}
		
		if (modeField.getValue() == null) {
			sb.append("表tNo valid mode.\n");
			isValid = false;
		}

		if (directionField.getValue() == null) {
			sb.append("表tNo valid direction.\n");
			isValid = false;
		}
		
		if (fileNameField.getText() == null || fileNameField.getText().length() == 0) {
			sb.append("表tNo valid file name.\n");
			isValid = false;
		}

		if (!isValid) {
			// Show the error message
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Invalid fields");
			alert.setHeaderText("Please correct invalid fields");
			alert.setContentText(sb.toString());
			alert.showAndWait();
		}
		
		return isValid;
	}

	@Override
	public void sendMessage(MessageEvent msg) {
		statusField.appendText(msg.getMessage() + "\n");
	}
}

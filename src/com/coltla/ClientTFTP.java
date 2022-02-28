package com.coltla;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class ClientTFTP extends Application {
	
	private Stage primaryStage;
	private AnchorPane rootLayout;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle("SP TFTP Client");
		
		initRootLayout();
	}

	private void initRootLayout() {
		// Load root layout from fxml file
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(ClientTFTP.class.getResource("view/ConnectionOverview.fxml"));
		
		try {
			rootLayout = (AnchorPane) loader.load();
			
			// Show the scene containing the root
			Scene scene = new Scene(rootLayout);
			primaryStage.setScene(scene);
			primaryStage.show();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}

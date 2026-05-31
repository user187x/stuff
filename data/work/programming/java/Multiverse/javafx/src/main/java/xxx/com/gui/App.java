package xxx.com.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("JavaFX Application");

    Label label = new Label("Hello, JavaFX!");
    Button button = new Button("Click Me");
    button.setOnAction(e -> label.setText("Button Clicked!"));

    VBox vbox = new VBox(10, label, button);
    vbox.setAlignment(Pos.CENTER);
    vbox.setPadding(new Insets(20));

    Scene scene = new Scene(vbox, 400, 300);
    primaryStage.setScene(scene);
    primaryStage.centerOnScreen();
    primaryStage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

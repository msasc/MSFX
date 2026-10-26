package ztest;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Smoke test for the toolchain: open this file in Emacs and press C-c C-r.
 * A window should appear showing the Java and JavaFX versions in use,
 * which should both be 26.0.1 from msfx/java/lib.
 */
public class HelloFx extends Application {

    @Override
    public void start(Stage stage) {
        String info = "Java " + System.getProperty("java.version");
        info += "  |  JavaFX " + System.getProperty("javafx.version");
        info += "\nJava home: " + System.getProperty("java.home");
        stage.setScene(new Scene(new StackPane(new Label(info)), 520, 160));
        stage.setTitle("msfx - ztest.HelloFx");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

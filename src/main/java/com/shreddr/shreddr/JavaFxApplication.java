package com.shreddr.shreddr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        String[] args = getParameters().getRaw().toArray(new String[0]);
        this.applicationContext = new SpringApplicationBuilder()
                .sources(ShreddrApplication.class)
                .run(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        
        Parent root = loader.load();
        Scene scene = new Scene(root, 1060, 720);
        scene.setFill(Color.TRANSPARENT);

        stage.setTitle("ShreddR — Secure Erasure Utility");
        stage.setScene(scene);
        stage.setMinWidth(920);
        stage.setMinHeight(630);
        stage.setResizable(true);
        stage.show();
    }

    @Override
    public void stop() {
        this.applicationContext.close();
        Platform.exit();
    }
}

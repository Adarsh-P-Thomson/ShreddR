package com.shreddr.shreddr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShreddrApplication {

	public static void main(String[] args) {
		javafx.application.Application.launch(JavaFxApplication.class, args);
	}

}

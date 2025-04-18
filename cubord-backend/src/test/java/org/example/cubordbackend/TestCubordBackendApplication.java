package org.example.cubordbackend;

import org.cubord.cubordbackend.CubordBackendApplication;
import org.springframework.boot.SpringApplication;

public class TestCubordBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(CubordBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

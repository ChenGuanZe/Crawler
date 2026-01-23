package com.game;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AppApplication extends SpringBootServletInitializer {
    private static final Logger log = LoggerFactory.getLogger(AppApplication.class);

    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(new Class[] { AppApplication.class });
    }

    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext application = SpringApplication.run(AppApplication.class, args);
        ConfigurableEnvironment configurableEnvironment = application.getEnvironment();
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = configurableEnvironment.getProperty("server.port");
        String path = configurableEnvironment.getProperty("server.servlet.context-path");
        log.info("\n----------------------------------------------------------\n\tApplication Game-Boot is running! Access URLs:\n\tLocal: \t\thttp://localhost:" + port + path + "/\n\tExternal: \thttp://" + ip + ":" + port + path + "/\n\tSwagger\thttp://" + ip + ":" + port + path + "/doc.html\n----------------------------------------------------------");
    }
}

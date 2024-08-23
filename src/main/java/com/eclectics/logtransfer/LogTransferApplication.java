package com.eclectics.logtransfer;

import com.eclectics.logtransfer.config.DirectoryMappingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableEurekaClient
public class LogTransferApplication {

public static void main (String[] args) {
	SpringApplication.run (LogTransferApplication.class, args);
}

}

package com.eclectics.logtransfer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
@Getter
@Setter
@ConfigurationProperties (prefix = "log-transfer")
public class DirectoryMappingConfig {
private List<String> directories;
}
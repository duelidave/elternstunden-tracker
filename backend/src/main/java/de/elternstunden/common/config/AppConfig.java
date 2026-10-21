package de.elternstunden.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Application-wide switches: typed settings and scheduled jobs (auth worker, cleanup). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class AppConfig {}

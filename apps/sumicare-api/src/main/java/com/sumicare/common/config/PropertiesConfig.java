package com.sumicare.common.config;

import com.sumicare.common.redis.RedisConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, RedisConfig.RedisProperties.class})
public class PropertiesConfig {
}

package com.sumicare.common.config;

import com.sumicare.common.web.RequestBaseUrlContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sumi-async-");
        executor.setTaskDecorator(propagateRequestBaseUrl());
        executor.initialize();
        return executor;
    }

    private TaskDecorator propagateRequestBaseUrl() {
        return runnable -> {
            String origin = RequestBaseUrlContext.get();
            return () -> {
                RequestBaseUrlContext.set(origin);
                try {
                    runnable.run();
                } finally {
                    RequestBaseUrlContext.clear();
                }
            };
        };
    }
}

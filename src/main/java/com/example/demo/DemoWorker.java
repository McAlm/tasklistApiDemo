package com.example.demo;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.camunda.zeebe.spring.client.annotation.JobWorker;

@Component
public class DemoWorker {

    private static final Logger LOG = LoggerFactory.getLogger(DemoWorker.class);

    @JobWorker
    public Map<String, Object> demoWorker() {
        LOG.info("executing demoWorker...");
        return Map.of("demoExecuted", true);
    }

}

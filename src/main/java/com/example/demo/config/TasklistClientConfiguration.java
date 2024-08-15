package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.tasklist.ApiClient;
import com.example.tasklist.api.TaskApi;

import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;

@Configuration
public class TasklistClientConfiguration {

  @Bean
  public TaskApi taskApi(ApiClient apiClient) {
    return new TaskApi(apiClient);
  }

  @Bean
  public ApiClient apiClient(CamundaClientProperties camundaClientProperties) {
    return new ApiClient()
        .setBasePath(camundaClientProperties.getTasklist().getBaseUrl().toString());
  }
}

package com.example.demo;

import com.example.tasklist.model.TaskOrderBy;
import com.example.tasklist.model.TaskResponse;
import com.example.tasklist.model.TaskSearchRequest;
import com.example.tasklist.model.TaskSearchResponse;
import io.camunda.common.auth.Authentication;
import com.example.tasklist.api.TaskApi;
import io.camunda.common.auth.Product;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

@Service
public class UserTasksManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(UserTasksManagementService.class);

    @Autowired
    private ZeebeClient zeebe;

    @Autowired
    private TaskApi taskApi;

    @Autowired
    private Authentication authentication;

    @EventListener(ApplicationReadyEvent.class)
    public void manageUserTasks() throws Exception {
        // 1. Start a process instance
        long processInstanceKey = zeebe
                .newCreateInstanceCommand()
                .bpmnProcessId("api-test-process")
                .latestVersion()
                .send()
                .get()
                .getProcessInstanceKey();
        LOG.info("Started a 'api-test-process' process instance with key={}", processInstanceKey);

        // 2. Search for user tasks (filter by processInstanceKey,
        // implementation=ZEEBE_USER_TASK,
        // state=CREATED and sort DESC by creationTime)
        List<TaskSearchResponse> tasks = waitFor(
                () -> taskApi.searchTasks(
                        new TaskSearchRequest()
                                .state(TaskSearchRequest.StateEnum.CREATED)
                                .implementation(TaskSearchRequest.ImplementationEnum.ZEEBE_USER_TASK)
                                .processInstanceKey(String.valueOf(processInstanceKey))
                                .sort(
                                        List.of(
                                                new TaskOrderBy()
                                                        .field(TaskOrderBy.FieldEnum.CREATIONTIME)
                                                        .order(TaskOrderBy.OrderEnum.DESC))),
                        getTasklistAuthTokenHeader()),
                taskSearchResponses -> taskSearchResponses.size() > 0);
        LOG.info("Found {} task(s) in Tasklist", tasks.size());

        // 3. Pick the last created task
        String taskId = tasks.get(0).getId();
        LOG.info("The chosen task id={}", taskId);

        // 4. Assign task to "demo" user
        zeebe.newUserTaskAssignCommand(Long.valueOf(taskId)).assignee("demo").send().get();
        LOG.info("task assigned via Zeebe to user demo" );

        // 5. Get the task by id
        TaskResponse taskResponse = waitFor(
                () -> taskApi.getTaskById(taskId, getTasklistAuthTokenHeader()),
                response -> "demo".equals(response.getAssignee()));
        LOG.info("Task {} is assigned to {}", taskId, taskResponse.getAssignee());

        // 6. Complete the task
        zeebe.newUserTaskCompleteCommand(Long.valueOf(taskId)).send().get();

        // 7. Get the task and check the state
        taskResponse = waitFor(
                () -> taskApi.getTaskById(taskId, getTasklistAuthTokenHeader()),
                response -> response.getTaskState() == TaskResponse.TaskStateEnum.COMPLETED);
        LOG.info("Task {} has {} state", taskId, taskResponse.getTaskState());
    }

    private <T> T waitFor(Callable<T> responseSupplier, Predicate<T> responseTester)
            throws Exception {
        int maxRounds = 10;
        int waitRound = 0;
        int waitTime = 1000;
        while (waitRound < maxRounds) {
            T response = responseSupplier.call();
            if (responseTester.test(response)) {
                return response;
            }
            Thread.sleep(waitTime);
            waitRound++;
        }
        throw new RuntimeException(
                String.format("Test is not successful after %s attempts", waitRound));
    }

    private Map<String, String> getTasklistAuthTokenHeader() {
        return Map.ofEntries(authentication.getTokenHeader(Product.TASKLIST));
    }
}
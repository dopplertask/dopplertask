/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskDao;
import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.dao.TaskExecutionLogDao;
import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;

import com.jayway.jsonpath.JsonPath;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TaskControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskDao taskDao;

    @Autowired
    private TaskExecutionDao taskExecutionDao;

    @Autowired
    private TaskExecutionLogDao taskExecutionLogDao;

    @Test
    public void noParamGreetingShouldReturnDefaultMessage() throws Exception {
        this.mockMvc.perform(post("/schedule/task")).andDo(print()).andExpect(status().isBadRequest());
    }


    @Test
    public void testGroupedTasksIncludeDescriptionOfEachVersionSuccess() throws Exception {
        for (String description : new String[]{"First draft", "Second draft"}) {
            String createJson = "{\"name\":\"versioned\",\"description\":\"" + description + "\",\"active\":false," +
                    "\"actions\":[{\"@type\":\"StartAction\"}],\"connections\":[]}";
            this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
        }

        this.mockMvc.perform(get("/task/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versioned[*].description", containsInAnyOrder("First draft", "Second draft")));
    }

    @Test
    public void testDeleteVersionKeepsOtherVersionsSuccess() throws Exception {
        String firstChecksum = saveVersion("First draft");
        String secondChecksum = saveVersion("Second draft");

        this.mockMvc.perform(delete("/task/" + firstChecksum)).andExpect(status().isOk());

        this.mockMvc.perform(get("/task/" + firstChecksum + "/checksum")).andExpect(status().isNotFound());
        this.mockMvc.perform(get("/task/" + secondChecksum + "/checksum")).andExpect(status().isOk());
    }

    private String saveVersion(String description) throws Exception {
        String createJson = "{\"name\":\"deletable\",\"description\":\"" + description + "\",\"active\":false," +
                "\"actions\":[{\"@type\":\"StartAction\"}],\"connections\":[]}";
        MvcResult result = this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.checksum");
    }

    @Test
    public void testManualSaveIsNotMarkedAutoSavedSuccess() throws Exception {
        saveDescription("First", "/task").andExpect(status().isOk());

        assertAutoSavedFlags(false);
    }

    @Test
    public void testAutoSaveOfExistingTaskIsMarkedAutoSavedSuccess() throws Exception {
        saveDescription("First", "/task").andExpect(status().isOk());
        saveDescription("Second", "/task?autoSaved=true").andExpect(status().isOk());

        assertAutoSavedFlags(true, false);
    }

    @Test
    public void testAutoSaveOfUnknownTaskFail() throws Exception {
        saveDescription("First", "/task?autoSaved=true").andExpect(status().isBadRequest());
    }

    @Test
    public void testManualSaveOfAutoSavedContentClearsMarkSuccess() throws Exception {
        saveDescription("First", "/task").andExpect(status().isOk());
        saveDescription("Second", "/task?autoSaved=true").andExpect(status().isOk());
        saveDescription("Second", "/task").andExpect(status().isOk());

        assertAutoSavedFlags(false, false);
    }

    @Test
    public void testAutoSaveOfManuallySavedContentKeepsItUnmarkedSuccess() throws Exception {
        saveDescription("First", "/task").andExpect(status().isOk());
        saveDescription("First", "/task?autoSaved=true").andExpect(status().isOk());

        assertAutoSavedFlags(false);
    }

    private ResultActions saveDescription(String description, String url) throws Exception {
        String createJson = "{\"name\":\"autosaving\",\"description\":\"" + description + "\",\"active\":false," +
                "\"actions\":[{\"@type\":\"StartAction\"}],\"connections\":[]}";
        return this.mockMvc.perform(post(url).content(createJson));
    }

    private void assertAutoSavedFlags(Boolean... flags) throws Exception {
        this.mockMvc.perform(get("/task/grouped"))
                .andExpect(jsonPath("$.autosaving[*].autoSaved", containsInAnyOrder(flags)));
    }

    @Test
    public void testSaveWithoutActiveAndDescriptionSuccess() throws Exception {
        this.mockMvc.perform(post("/task").content("{\"name\":\"minimal\",\"actions\":[{\"@type\":\"StartAction\"}],\"connections\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveWithoutConnectionsNamesTheFieldFail() throws Exception {
        assertSaveFailsWith("{\"name\":\"x\",\"actions\":[]}", "connections");
    }

    @Test
    public void testSaveWithWrongFieldTypeNamesThePathFail() throws Exception {
        assertSaveFailsWith("{\"name\":\"x\",\"connections\":[],\"actions\":[{\"@type\":\"HttpAction\",\"headers\":{}}]}", "at actions[0].headers");
    }

    @Test
    public void testSaveWithUnknownActionNamesTheTypeFail() throws Exception {
        assertSaveFailsWith("{\"name\":\"x\",\"connections\":[],\"actions\":[{\"@type\":\"NoSuchAction\"}]}", "NoSuchAction");
    }

    @Test
    public void testSaveWithWhitespaceInNameExplainsWhyFail() throws Exception {
        assertSaveFailsWith("{\"name\":\"has space\",\"connections\":[],\"actions\":[{\"@type\":\"StartAction\"}]}", "whitespace");
    }

    @Test
    public void testSaveWithoutStartActionExplainsWhyFail() throws Exception {
        assertSaveFailsWith("{\"name\":\"x\",\"connections\":[],\"actions\":[]}", "start action");
    }

    @Test
    public void testSaveWithMalformedJsonFail() throws Exception {
        assertSaveFailsWith("not json", "Invalid task JSON");
    }

    private void assertSaveFailsWith(String body, String expectedMessage) throws Exception {
        this.mockMvc.perform(post("/task").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(expectedMessage)));
    }

    @Test
    public void testBuildAndRunShouldReturnOK() throws Exception {
        String createJson = "{\n" +
                "    \"name\":\"accept-alert\",\n" +
                "\"description\":\"Testing\"," +
                "\"active\": false," +
                "    \"actions\":[\n" +
                "        {\n" +
                "            \"@type\":\"StartAction\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"@type\":\"PrintAction\",\n" +
                "            \"message\":\"Testing text\"\n" +
                "        }\n" +
                "    ]," +
                "   \"connections\":[]\n" +
                "}\n";
        MvcResult result = this.mockMvc.perform(post("/task").content(createJson)).andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.checksum").isString()).andReturn();


        String requestTaskRunStr = "{\n" +
                "  \"taskName\": \"accept-alert\",\n" +
                "  \"parameters\": {\n" +
                "  }\n" +
                "}";
        this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content(requestTaskRunStr)).andDo(print()).andExpect(status().isOk());
    }

    @Test
    public void testBuildAndRunWithParametersShouldReturnOK() throws Exception {
        String createJson = "{\n" +
                "    \"name\":\"example-task-2\",\n" +
                "\"description\":\"Testing\"," +
                "\"active\": false," +
                "    \"actions\":[\n" +
                "        {\n" +
                "            \"@type\":\"StartAction\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"@type\":\"PrintAction\",\n" +
                "            \"message\":\"$parameters.get('message')\"\n" +
                "        }\n" +
                "    ],\n" +
                "   \"connections\":[]\n" +
                "}\n";
        MvcResult result = this.mockMvc.perform(post("/task").content(createJson)).andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.checksum").isString()).andReturn();


        String requestTaskRunStr = "{\n" +
                "  \"taskName\": \"example-task-2\",\n" +
                "  \"parameters\": {\n" +
                " \"message\":\"Hello my fellow automators\"\n" +
                "  }\n" +
                "}";
        this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content(requestTaskRunStr)).andDo(print()).andExpect(status().isOk());

        Optional<Task> task = taskDao.findFirstByNameOrderByCreatedDesc("example-task-2");

        Assert.assertEquals(true, task.isPresent());
        Assert.assertEquals("example-task-2", task.get().getName());


        /*List<TaskExecution> execution = taskExecutionDao.findAllByTask(task.get());
        Assert.assertNotNull(execution.get(1).getTask());

        List<TaskExecutionLog> logs = taskExecutionLogDao.findByTaskExecution(execution.get(1));

        Assert.assertEquals("Hello my fellow automators", logs.get(1).getOutput());*/
    }


    @Test
    @Ignore
    public void testBuildAndRunBrowseWebActionWithParametersShouldReturnOK() throws Exception {
        // Start server
        FakeWebserver webserver = new FakeWebserver(7894);

        String createJson = "{\n" +
                "    \"name\":\"example-task-3\",\n" +
                "\"description\":\"Testing\"," +
                "    \"actions\":[\n" +
                "        {\n" +
                "            \"@type\":\"BrowseWebAction\",\n" +
                "            \"url\":\"http://localhost:7894\",\n" +
                "            \"actionList\": [\n" +
                "                {\"fieldName\": \"exampleInput\", \"findByType\": \"ID\", \"action\": \"WRITE\", \"value\": \"${message}\"}\n" +
                "            ]\n" +
                "        }\n" +
                "    ]\n" +
                "}\n";
        MvcResult result = this.mockMvc.perform(post("/task").content(createJson)).andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.id").isString()).andReturn();


        String requestTaskRunStr = "{\n" +
                "  \"taskName\": \"example-task-3\",\n" +
                "  \"parameters\": {\n" +
                " \"message\":\"Hello my fellow automators\"\n" +
                "  }\n" +
                "}";
        this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content(requestTaskRunStr)).andDo(print()).andExpect(status().isOk());

        webserver.close();

        Optional<Task> task = taskDao.findFirstByNameOrderByCreatedDesc("example-task-3");
        Assert.assertEquals(true, task.isPresent());
        Assert.assertEquals("example-task-3", task.get().getName());
        List<TaskExecution> execution = taskExecutionDao.findAllByTask(task.get());
        Assert.assertNotNull(execution.get(1).getTask());
        List<TaskExecutionLog> logs = taskExecutionLogDao.findByTaskExecution(execution.get(1));

        System.out.println("Log size: " + logs.size() + " " + logs.get(1).getOutput());

        Assert.assertEquals(true, logs.get(1).getOutput().contains("text=Hello my fellow automators"));
    }


}

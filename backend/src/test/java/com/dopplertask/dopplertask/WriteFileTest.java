package com.dopplertask.dopplertask;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WriteFileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testWritingShorterContentReplacesTheFileSuccess() throws Exception {
        Path file = Files.createTempFile("write-file-test", ".txt");
        try {
            String createJson = "{\"name\":\"write_file\",\"parameters\":[{\"name\":\"text\",\"required\":true}],\"actions\":["
                    + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"write_s\",\"portType\":\"OUTPUT\"}]},"
                    + "{\"@type\":\"WriteFileAction\",\"filename\":\"" + file + "\",\"outputType\":\"clearText\",\"contents\":\"$parameters.get(\\\"text\\\")\","
                    + "\"ports\":[{\"externalId\":\"write_i\",\"portType\":\"INPUT\"}]}],"
                    + "\"connections\":[{\"source\":{\"externalId\":\"write_s\"},\"target\":{\"externalId\":\"write_i\"}}]}";
            this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());

            write("a long first entry");
            write("short");

            Assert.assertEquals("short", Files.readString(file));
        } finally {
            Files.delete(file);
        }
    }

    private void write(String text) throws Exception {
        this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"write_file\",\"parameters\":{\"text\":\"" + text + "\"}}")).andExpect(status().isOk());
    }
}

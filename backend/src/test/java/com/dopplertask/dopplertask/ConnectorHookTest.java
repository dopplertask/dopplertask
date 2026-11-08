package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;

import java.util.concurrent.BlockingQueue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The address services send connector events to: it answers their checks, and reads forms and text as well as JSON. */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ConnectorHookTest {

    @Autowired
    private MockMvc mockMvc;

    @After
    public void stopListening() {
        ConnectorWebhooks.INSTANCE.stopListening("hook-key");
    }

    @Test
    public void testAnAddressCheckIsAnsweredOnlyWhileATriggerListensSuccess() throws Exception {
        mockMvc.perform(head("/hooks/hook-key")).andExpect(status().isNotFound());
        ConnectorWebhooks.INSTANCE.listen("hook-key", event -> null);
        mockMvc.perform(head("/hooks/hook-key")).andExpect(status().isOk());
    }

    @Test
    public void testAFormEventIsReadAsNestedFieldsSuccess() throws Exception {
        BlockingQueue<ConnectorWebhooks.Event> events = ConnectorWebhooks.INSTANCE.listen("hook-key", event -> null);
        mockMvc.perform(post("/hooks/hook-key?source=crm").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("contact%5Bemail%5D=ada%40example.com&tags%5B%5D=new&tags%5B%5D=vip&items%5B0%5D%5Bname%5D=pen&items%5B0%5D%5Bqty%5D=2")).andExpect(status().isOk());
        ConnectorWebhooks.Event event = events.take();
        Assert.assertEquals("{\"contact\":{\"email\":\"ada@example.com\"},\"tags\":[\"new\",\"vip\"],\"items\":[{\"name\":\"pen\",\"qty\":\"2\"}]}", event.getBody().toString());
        Assert.assertEquals("crm", event.getReceived().path("query").path("source").asText());
    }

    @Test
    public void testATextEventStaysTextSuccess() throws Exception {
        BlockingQueue<ConnectorWebhooks.Event> events = ConnectorWebhooks.INSTANCE.listen("hook-key", event -> null);
        mockMvc.perform(post("/hooks/hook-key").contentType(MediaType.TEXT_XML).content("<notification/>")).andExpect(status().isOk());
        Assert.assertEquals("<notification/>", events.take().getBody().asText());
    }

    @Test
    public void testAChallengeIsAnsweredByTheReplyAndIsNoEventSuccess() throws Exception {
        BlockingQueue<ConnectorWebhooks.Event> events = ConnectorWebhooks.INSTANCE.listen("hook-key", event -> {
            JsonNode challenge = event.getBody().has("challenge") ? event.getBody().get("challenge") : event.getReceived().path("query").get("hub.challenge");
            return challenge == null ? null : challenge;
        });
        mockMvc.perform(post("/hooks/hook-key").contentType(MediaType.APPLICATION_JSON).content("{\"challenge\": \"c1\"}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)).andExpect(content().string("c1"));
        mockMvc.perform(get("/hooks/hook-key?hub.challenge=c2&email=a%40b.com")).andExpect(status().isOk()).andExpect(content().string("c2"));
        Assert.assertTrue(events.isEmpty());
    }
}

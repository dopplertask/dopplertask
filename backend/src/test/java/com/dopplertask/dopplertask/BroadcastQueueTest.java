package com.dopplertask.dopplertask;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jms.core.JmsTemplate;

/**
 * A real broker with little memory. Nobody reads the broadcasts here, which is the normal case on a server.
 */
public class BroadcastQueueTest {

    private static final String ONE_MEGABYTE = "x".repeat(1024 * 1024);
    private static final long BROKER_MEMORY_BYTES = 64 * 1024 * 1024;

    @Rule
    public TemporaryFolder brokerData = new TemporaryFolder();

    private BrokerService broker;
    private JmsTemplate template;

    @Before
    public void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("broadcast-test");
        broker.setPersistent(false);
        broker.setDataDirectoryFile(brokerData.getRoot());
        broker.setUseJmx(false);
        broker.getSystemUsage().getMemoryUsage().setLimit(BROKER_MEMORY_BYTES);
        broker.setDestinationPolicy(Application.broadcastPolicy());
        broker.start();
        template = new JmsTemplate(new ActiveMQConnectionFactory("vm://broadcast-test?create=false"));
        template.setReceiveTimeout(5000);
    }

    @After
    public void stopBroker() throws Exception {
        broker.stop();
    }

    @Test(timeout = 60000)
    public void testAFullBroadcastQueueDoesNotStopNewRunsSuccess() throws Exception {
        Thread sender = new Thread(() -> {
            JmsTemplate broadcasts = Application.broadcastTemplate(template);
            for (int sent = 0; sent < 100; sent++) {
                broadcasts.convertAndSend(Application.BROADCAST_QUEUE, ONE_MEGABYTE);
            }
        });
        sender.setDaemon(true);
        sender.start();
        // Long enough to reach the limit of the queue, which is less than 100 megabytes.
        Thread.sleep(3000);
        Assert.assertTrue("The sender should be waiting for space.", sender.isAlive());

        template.convertAndSend("automation_destination", "a new run");
        Assert.assertEquals("a new run", template.receiveAndConvert("automation_destination"));
    }

    @Test(timeout = 60000)
    public void testExpiredBroadcastsGiveTheirSpaceBackSuccess() {
        JmsTemplate broadcasts = Application.broadcastTemplate(template);
        broadcasts.setTimeToLive(1000);
        for (int sent = 0; sent < 100; sent++) {
            broadcasts.convertAndSend(Application.BROADCAST_QUEUE, ONE_MEGABYTE);
        }
    }

    @Test
    public void testAClientFollowingARunStillReadsItsBroadcastsSuccess() {
        Application.broadcastTemplate(template).convertAndSend(Application.BROADCAST_QUEUE, "a line of output");
        Assert.assertEquals("a line of output", template.receiveAndConvert(Application.BROADCAST_QUEUE));
    }

    @Test
    public void testBroadcastsExpireWhenNobodyReadsThemSuccess() throws Exception {
        JmsTemplate broadcasts = Application.broadcastTemplate(template);
        broadcasts.setTimeToLive(1000);
        broadcasts.convertAndSend(Application.BROADCAST_QUEUE, "a line of output");
        Thread.sleep(1500);
        template.setReceiveTimeout(500);
        Assert.assertNull(template.receiveAndConvert(Application.BROADCAST_QUEUE));
    }
}

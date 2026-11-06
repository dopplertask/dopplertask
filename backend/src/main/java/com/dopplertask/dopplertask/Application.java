package com.dopplertask.dopplertask;

import java.util.List;
import com.dopplertask.dopplertask.service.RunHousekeeping;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import javax.jms.ConnectionFactory;

@SpringBootApplication
@EnableJms
@EnableScheduling
public class Application {
    public static final String BROADCAST_QUEUE = "taskexecution_destination";
    private static final long BROADCAST_LIFETIME_MILLISECONDS = 30_000;
    private static final long BROADCAST_EXPIRY_CHECK_MILLISECONDS = 5_000;
    private static final long BROADCAST_MEMORY_BYTES = 32 * 1024 * 1024;


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * After everything is created and before any listener or the web server starts, so that no new run is touched.
     */
    @Bean
    public SmartInitializingSingleton failInterruptedRuns(RunHousekeeping housekeeping) {
        return housekeeping::failInterruptedRuns;
    }

    @Bean
    public BrokerService broker(@Value("${dopplertask.personToken:}") String personToken, @Value("${dopplertask.sso.issuer:}") String ssoIssuer) throws Exception {
        final BrokerService broker = new BrokerService();
        broker.addConnector("tcp://localhost:61616");
        broker.addConnector("vm://localhost");
        broker.addConnector("stomp://localhost:61613");
        // Any web page can reach this socket and it has no real login, so it stays off once access is protected.
        if (personToken.isEmpty() && ssoIssuer.isEmpty()) {
            broker.addConnector("ws://0.0.0.0:61614");
        }
        broker.setPersistent(false);
        broker.setDestinationPolicy(broadcastPolicy());
        return broker;
    }

    /**
     * Only a client that follows a run live reads the broadcasts, so without a limit they would fill the memory of the
     * broker, which then stops every sender and with it the start of new runs. They therefore have memory of their own,
     * and unread ones expire so that the space comes back.
     */
    public static PolicyMap broadcastPolicy() {
        PolicyEntry broadcasts = new PolicyEntry();
        broadcasts.setQueue(BROADCAST_QUEUE);
        broadcasts.setMemoryLimit(BROADCAST_MEMORY_BYTES);
        broadcasts.setExpireMessagesPeriod(BROADCAST_EXPIRY_CHECK_MILLISECONDS);
        PolicyMap policy = new PolicyMap();
        policy.setPolicyEntries(List.of(broadcasts));
        return policy;
    }

    public static JmsTemplate broadcastTemplate(JmsTemplate template) {
        JmsTemplate broadcasts = new JmsTemplate();
        broadcasts.setConnectionFactory(template.getConnectionFactory());
        broadcasts.setMessageConverter(template.getMessageConverter());
        broadcasts.setExplicitQosEnabled(true);
        broadcasts.setDeliveryPersistent(false);
        broadcasts.setTimeToLive(BROADCAST_LIFETIME_MILLISECONDS);
        return broadcasts;
    }

    @Bean
    public JmsListenerContainerFactory<?> jmsFactory(ConnectionFactory connectionFactory,
                                                     DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConcurrency("50");
        factory.setSessionTransacted(true);
        // This provides all boot's default to this factory, including the message converter
        configurer.configure(factory, connectionFactory);
        // You could still override some of Boot's default if necessary.
        return factory;
    }

    @Bean // Serialize message content to json using TextMessage
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public VelocityEngine velocityEngine() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        return velocityEngine;
    }


}

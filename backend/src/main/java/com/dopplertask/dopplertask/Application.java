package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.service.RunHousekeeping;
import com.dopplertask.dopplertask.service.TaskService;
import org.apache.activemq.broker.BrokerService;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.util.introspection.SecureUberspector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import jakarta.jms.ConnectionFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootApplication
@EnableJms
@EnableScheduling
public class Application {


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * After everything is created and before any listener or the web server starts, so that no new run is touched and no
     * answer to a waiting run arrives while the ones that arrived before are queued.
     */
    @Bean
    public SmartInitializingSingleton prepareSavedData(RunHousekeeping housekeeping, TaskService taskService) {
        return () -> {
            housekeeping.failInterruptedRuns();
            taskService.continueAnsweredRuns();
        };
    }

    @Bean
    public BrokerService broker(@Value("${dopplertask.brokerPort:61616}") int brokerPort) throws Exception {
        final BrokerService broker = new BrokerService();
        if (brokerPort > 0) {
            broker.addConnector("tcp://localhost:" + brokerPort);
        }
        broker.addConnector("vm://localhost");
        broker.setPersistent(false);
        return broker;
    }

    @Bean
    public JmsListenerContainerFactory<?> jmsFactory(ConnectionFactory connectionFactory,
                                                     DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        // This provides all boot's default to this factory, including the message converter
        configurer.configure(factory, connectionFactory);
        // After the defaults, which would otherwise make runs wait for each other one at a time.
        factory.setConcurrency("50-50");
        factory.setSessionTransacted(true);
        // You could still override some of Boot's default if necessary.
        return factory;
    }

    @Bean // Serialize message content to json using TextMessage
    public MessageConverter jacksonJmsMessageConverter() {
        // A run waiting in the queue can leave out a yes or no setting, which then means no.
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build());
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public VelocityEngine velocityEngine(@Value("${dopplertask.templateLoopLimit:-1}") int templateLoopLimit) {
        return lockedTemplateEngine(templateLoopLimit);
    }

    /** A template fills in values. It has no reason to reach Java classes or files on the machine. */
    public static VelocityEngine lockedTemplateEngine(int loopLimit) {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, SecureUberspector.class.getName());
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "string");
        velocityEngine.setProperty("resource.loader.string.class", StringResourceLoader.class.getName());
        velocityEngine.setProperty(RuntimeConstants.MAX_NUMBER_LOOPS, loopLimit);
        velocityEngine.init();
        return velocityEngine;
    }


}

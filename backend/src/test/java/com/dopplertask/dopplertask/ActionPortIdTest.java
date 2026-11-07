package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionPort;
import jakarta.persistence.EntityManager;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * A saved database holds ports with ids from this sequence, so that new ports must continue it to be saved at all.
 */
@RunWith(SpringRunner.class)
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:actionportid")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ActionPortIdTest.DatabaseOnly.class)
public class ActionPortIdTest {

    @Configuration
    @EntityScan("com.dopplertask.dopplertask.domain")
    @EnableJpaRepositories("com.dopplertask.dopplertask.dao")
    static class DatabaseOnly {
    }

    @Autowired
    private EntityManager database;

    @Test
    public void testNewPortsContinueTheSequenceOfSavedOnesSuccess() {
        database.createNativeQuery("ALTER SEQUENCE hibernate_sequence RESTART WITH 500").executeUpdate();
        ActionPort port = new ActionPort();
        database.persist(port);
        database.flush();
        Assert.assertEquals(Long.valueOf(500), port.getId());
    }
}

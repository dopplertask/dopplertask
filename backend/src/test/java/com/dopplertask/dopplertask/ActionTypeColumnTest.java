package com.dopplertask.dopplertask;

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
 * A saved database must accept steps added after it was created, since updating a table does not widen a check on it.
 */
@RunWith(SpringRunner.class)
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:actiontypecolumn")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ActionTypeColumnTest.DatabaseOnly.class)
public class ActionTypeColumnTest {

    @Configuration
    @EntityScan("com.dopplertask.dopplertask.domain")
    @EnableJpaRepositories("com.dopplertask.dopplertask.dao")
    static class DatabaseOnly {
    }

    @Autowired
    private EntityManager database;

    @Test
    public void testATableCreatedTodayTakesAStepAddedLaterSuccess() {
        Number checks = (Number) database.createNativeQuery("SELECT COUNT(*) FROM information_schema.check_constraints WHERE check_clause LIKE '%ACTION_TYPE%'").getSingleResult();
        Assert.assertEquals(0, checks.intValue());
    }
}

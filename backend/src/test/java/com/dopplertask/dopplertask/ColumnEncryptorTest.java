package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.service.ColumnEncryptor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ColumnEncryptorTest {

    @Test
    public void testConcurrentRunsReadTheirOwnValuesSuccess() throws Exception {
        ColumnEncryptor encryptor = new ColumnEncryptor("0123456789abcdef");
        ExecutorService threads = Executors.newFixedThreadPool(16);
        List<Future<Integer>> results = new ArrayList<>();
        for (int thread = 0; thread < 16; thread++) {
            String secret = "secret number " + thread;
            results.add(threads.submit(() -> {
                int wrong = 0;
                for (int round = 0; round < 2000; round++) {
                    try {
                        if (!secret.equals(encryptor.convertToEntityAttribute(encryptor.convertToDatabaseColumn(secret)))) {
                            wrong++;
                        }
                    } catch (RuntimeException failed) {
                        wrong++;
                    }
                }
                return wrong;
            }));
        }
        int wrong = 0;
        for (Future<Integer> result : results) {
            wrong += result.get();
        }
        threads.shutdown();
        Assert.assertEquals(0, wrong);
    }
}

package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@RunWith(SpringRunner.class)
public class VariableExtractorUtilTest {

    private VariableExtractorUtil extractorUtil;

    @Before
    public void setup() {
        VelocityEngine engine = new VelocityEngine();
        extractorUtil = new VariableExtractorUtil(engine);
    }

    @Test
    public void testVelocityEvaluationShouldReturnFalse() throws IOException {
        Assert.assertEquals("6", extractorUtil.extract("$mathTool.add(2, 4)", new TaskExecution(), ScriptLanguage.VELOCITY));
    }

    @Test
    public void testJavascriptEvaluationShouldReturnFalse() throws IOException {
        Assert.assertEquals("6", extractorUtil.extract("mathTool.add(2, 4)", new TaskExecution(), ScriptLanguage.JAVASCRIPT));
    }
}

package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ExecutionParameter
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.service.TaskServiceImpl
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import org.apache.velocity.app.VelocityEngine
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
internal class WebhookTest {
    @Test
    fun testBasicAuthentication() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk".toByteArray(), false)

        val webhook = Webhook()
        webhook.username = "testuser"
        webhook.password = "testpassword"
        webhook.authentication = "basic"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.SUCCESS)
    }


    @Test
    fun testBasicAuthenticationWithWrongPasswordFail() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk".toByteArray(), false)

        val webhook = Webhook()
        webhook.username = "testuser"
        webhook.password = "password"
        webhook.authentication = "basic"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.FAILURE)
    }

    @Test
    fun testBasicAuthenticationWithWrongUsernameFail() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk".toByteArray(), false)

        val webhook = Webhook()
        webhook.username = "user"
        webhook.password = "testpassword"
        webhook.authentication = "basic"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.FAILURE)
    }

    @Test
    fun testBasicAuthenticationFail() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk".toByteArray(), false)

        val webhook = Webhook()
        webhook.username = "user"
        webhook.password = "password"
        webhook.authentication = "basic"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.FAILURE)
    }


    @Test
    fun testBasicAuthenticationEmptyUsernameAndPasswordSuccess() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Basic ".toByteArray(), false)

        val webhook = Webhook()
        webhook.username = ""
        webhook.password = ""
        webhook.authentication = "basic"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.SUCCESS)
    }

    // Test with bearer authentication
    @Test
    fun testBearerAuthentication() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Bearer c29tZXRva2Vu".toByteArray(), false)

        val webhook = Webhook()
        webhook.token = "c29tZXRva2Vu"
        webhook.authentication = "bearer"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.SUCCESS)
    }

    @Test
    fun testBearerAuthenticationFail() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Bearer c29tZXRva2Vu".toByteArray(), false)

        val webhook = Webhook()
        webhook.token = "wrongvalue"
        webhook.authentication = "bearer"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.FAILURE)
    }

    @Test
    fun testBearerAuthenticationEmptyProvdedBearerFail() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Bearer ".toByteArray(), false)

        val webhook = Webhook()
        webhook.token = "wrongvalue"
        webhook.authentication = "bearer"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.FAILURE)
    }

    @Test
    fun testBearerAuthenticationEmptyBearerOnBothSettingAndProvidedSuccess() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_authorization"] =
            ExecutionParameter("TRIGGER_authorization", "Bearer ".toByteArray(), false)

        val webhook = Webhook()
        webhook.token = ""
        webhook.authentication = "bearer"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.SUCCESS)
    }

    // Test with header authentication. webhook.headerName and webhook.headerValue is set.
    @Test
    fun testHeaderAuthenticationFail() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_Authorization"] =
            ExecutionParameter("TRIGGER_Authorization", "Basic c29tZXRva2Vu".toByteArray(), false)

        val webhook = Webhook()
        webhook.headerName = "Authorization"
        webhook.headerValue = "Basic c29tZXRva2Vu"
        webhook.authentication = "header"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.SUCCESS)
    }

    @Test
    fun testHeaderAuthentication() {
        val taskExecution = TaskExecution()
        taskExecution.parameters["TRIGGER_Authorization"] =
            ExecutionParameter("TRIGGER_Authorization", "Basic c29tZXRva2Vu2".toByteArray(), false)

        val webhook = Webhook()
        webhook.headerName = "Authorization"
        webhook.headerValue = "Basic c29tZXRva2Vu"
        webhook.authentication = "header"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.FAILURE)
    }

    @Test
    fun testNoAuthentication() {
        val taskExecution = TaskExecution()
        val webhook = Webhook()
        webhook.authentication = "none"
        val actionResult = webhook.run(TaskServiceImpl(), taskExecution, VariableExtractorUtil(VelocityEngine()))

        assertTrue(actionResult.statusCode == StatusCode.SUCCESS)
    }

}
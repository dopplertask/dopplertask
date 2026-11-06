package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.filter.Caller;
import com.dopplertask.dopplertask.service.SsoService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class AccessFilterTest {

    private static final SsoService NO_SSO = new SsoService("", "", "", "");
    private static final AccessFilter WITH_TOKENS = new AccessFilter("person-token", "agent-token", NO_SSO);

    private int statusOf(AccessFilter filter, String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    public void testWithoutTokensEverythingStaysOpenSuccess() throws Exception {
        Assert.assertEquals(200, statusOf(new AccessFilter("", "", NO_SSO), "DELETE", "/task/x", null));
    }

    @Test
    public void testATokenIsRequiredOnceConfiguredFail() throws Exception {
        Assert.assertEquals(401, statusOf(WITH_TOKENS, "GET", "/task", null));
        Assert.assertEquals(401, statusOf(WITH_TOKENS, "GET", "/task", "wrong"));
        Assert.assertEquals(401, statusOf(WITH_TOKENS, "GET", "/console/", null));
    }

    @Test
    public void testThePageAndWebhooksNeedNoTokenSuccess() throws Exception {
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "GET", "/public/index.html", null));
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "POST", "/webhook/task/Webhook/suffix/path", null));
    }

    @Test
    public void testAPersonCanDoEverythingSuccess() throws Exception {
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "DELETE", "/task/x", "person-token"));
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "POST", "/task/action", "person-token"));
    }

    @Test
    public void testAnAgentCanReadProposeAndRunSuccess() throws Exception {
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "GET", "/task/actions", "agent-token"));
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "GET", "/execution/12", "agent-token"));
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "POST", "/task", "agent-token"));
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "POST", "/schedule/task", "agent-token"));
    }

    @Test
    public void testAnAgentCannotDeleteOrRunAnythingDirectlyFail() throws Exception {
        Assert.assertEquals(403, statusOf(WITH_TOKENS, "DELETE", "/task/x", "agent-token"));
        Assert.assertEquals(403, statusOf(WITH_TOKENS, "DELETE", "/execution/12", "agent-token"));
        Assert.assertEquals(403, statusOf(WITH_TOKENS, "POST", "/task/action", "agent-token"));
        Assert.assertEquals(403, statusOf(WITH_TOKENS, "POST", "/task/action/test", "agent-token"));
        Assert.assertEquals(403, statusOf(WITH_TOKENS, "POST", "/task/push", "agent-token"));
    }

    @Test
    public void testTheCallerIsKnownToWhatFollowsSuccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/task");
        request.addHeader("Authorization", "Bearer agent-token");
        WITH_TOKENS.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Assert.assertEquals(Caller.AGENT, AccessFilter.callerOf(request));
    }

    @Test(expected = IllegalStateException.class)
    public void testAnAgentTokenAloneIsRefusedFail() {
        new AccessFilter("", "agent-token", NO_SSO);
    }

    @Test
    public void testWithTokensOnlyTheAgentTokenReachesTheAgentEndpointFail() throws Exception {
        Assert.assertEquals(401, statusOf(WITH_TOKENS, "POST", "/mcp", null));
        Assert.assertEquals(200, statusOf(WITH_TOKENS, "POST", "/mcp", "agent-token"));
    }

    @Test
    public void testWithoutTokensTheAgentEndpointCountsAsAnAgentSuccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        new AccessFilter("", "", NO_SSO).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Assert.assertEquals(Caller.AGENT, AccessFilter.callerOf(request));
    }
}

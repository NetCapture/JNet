package com.jnet.core;

import org.junit.jupiter.api.Test;
import com.jnet.core.org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;

public class TestGitHubIntegration {

    @Test
    public void testGitHubZen() throws Exception {
        System.out.println("Testing GitHub API Zen endpoint...");
        // Use JNetClient to get full Response object and set headers
        Request request = JNetClient.getInstance().newGet("https://api.github.com/zen")
                .header("User-Agent", "JNet-Integration-Test")
                .build();
        
        Response response = request.newCall().execute();
        
        System.out.println("Status: " + response.getCode());
        System.out.println("Body: " + response.getBody());
        
        assertEquals(200, response.getCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length() > 0);
    }

    @Test
    public void testGitHubRepoInfo() throws Exception {
        System.out.println("Testing GitHub API Repo Info endpoint...");
        
        Request request = JNetClient.getInstance().newGet("https://api.github.com/repos/apache/maven")
                .header("User-Agent", "JNet-Integration-Test")
                .header("Accept", "application/vnd.github+json")
                .build();
        
        Response response = request.newCall().execute();
        
        System.out.println("Status: " + response.getCode());
        
        assertEquals(200, response.getCode());
        
        JSONObject json = new JSONObject(response.getBody());
        System.out.println("Repo Name: " + json.getString("name"));
        System.out.println("Repo ID: " + json.optInt("id", 0));
        
        assertEquals("maven", json.getString("name"));
        assertTrue(json.optInt("id", 0) > 0);
    }

    @Test
    public void testGitHubApiWithoutUserAgent() throws Exception {
        System.out.println("Testing GitHub API without explicit User-Agent...");
        
        Request request = JNetClient.getInstance().newGet("https://api.github.com/zen")
                .build();
        
        Response response = request.newCall().execute();
        
        System.out.println("Status (No UA): " + response.getCode());
        System.out.println("Body: " + response.getBody());

        // We expect this to fail (403) if JNet doesn't have a default UA.
        // If it fails, we will modify code to make it pass (200).
        assertEquals(200, response.getCode(), "GitHub API requires User-Agent. JNet needs to set a default one.");
    }
}

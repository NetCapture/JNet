package com.jnet.cloudflare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Rotates User-Agents to mimic different browsers and avoid detection.
 */
public class UserAgentRotator {

    private static final List<String> DEFAULT_USER_AGENTS = Arrays.asList(
        // Chrome (Windows)
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        // Chrome (Mac)
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        // Firefox (Windows)
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        // Firefox (Mac)
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
        // Safari (Mac)
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        // Edge (Windows)
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
    );

    private final List<String> userAgents;
    private final Random random;

    public UserAgentRotator() {
        this.userAgents = new ArrayList<>(DEFAULT_USER_AGENTS);
        this.random = new Random();
    }

    /**
     * Returns a random User-Agent string from the pool.
     *
     * @return a User-Agent string
     */
    public String getRandomUserAgent() {
        if (userAgents.isEmpty()) {
            return "";
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }

    /**
     * Adds a custom User-Agent to the rotation pool.
     *
     * @param userAgent the User-Agent string to add
     */
    public void addUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.isEmpty()) {
            this.userAgents.add(userAgent);
        }
    }

    /**
     * Clears the current list of User-Agents.
     */
    public void clear() {
        this.userAgents.clear();
    }
}

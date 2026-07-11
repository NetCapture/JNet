package com.jnet.cloudflare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    private volatile List<String> userAgents;

    public UserAgentRotator() {
        this.userAgents = Collections.unmodifiableList(new ArrayList<>(DEFAULT_USER_AGENTS));
    }

    /**
     * Returns a random User-Agent string from the pool.
     *
     * @return a User-Agent string
     */
    public String getRandomUserAgent() {
        List<String> snapshot = userAgents;
        if (snapshot.isEmpty()) {
            return "";
        }
        return snapshot.get(ThreadLocalRandom.current().nextInt(snapshot.size()));
    }

    /**
     * Adds a custom User-Agent to the rotation pool.
     *
     * @param userAgent the User-Agent string to add
     */
    public synchronized void addUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.isEmpty()) {
            validateUserAgent(userAgent);
            List<String> updated = new ArrayList<>(userAgents.size() + 1);
            updated.addAll(userAgents);
            updated.add(userAgent);
            this.userAgents = Collections.unmodifiableList(updated);
        }
    }

    /**
     * Clears the current list of User-Agents.
     */
    public synchronized void clear() {
        this.userAgents = Collections.emptyList();
    }

    private static void validateUserAgent(String userAgent) {
        for (int i = 0; i < userAgent.length(); i++) {
            char current = userAgent.charAt(i);
            if (current < 0x20 || current == 0x7f) {
                throw new IllegalArgumentException("User-Agent contains a prohibited control character");
            }
        }
    }
}

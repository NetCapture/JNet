/**
 * JNet GitHub Pages - Network Request Manager
 * Handles HTTP requests with retry logic, timeout, and error handling
 */

import {
    INetworkManager,
    NetworkRequestOptions,
    NetworkResponse,
    AsyncResult,
    ErrorType
} from '../types/index.js';
import { ErrorManager, retryWithBackoff } from '../errors/ErrorManager.js';
import { ValidationManager } from './ValidationManager.js';

/**
 * NetworkManager - Handles all network operations
 */
export class NetworkManager implements INetworkManager {
    private static instance: NetworkManager | null = null;
    private errorManager: ErrorManager;
    private validationManager: ValidationManager;
    private timeout: number;
    private retryAttempts: number;
    private retryDelay: number;

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
        this.timeout = 30000; // 30 seconds
        this.retryAttempts = 3;
        this.retryDelay = 1000; // 1 second
    }

    /**
     * Get singleton instance
     */
    static getInstance(): NetworkManager {
        if (!NetworkManager.instance) {
            NetworkManager.instance = new NetworkManager();
        }
        return NetworkManager.instance;
    }

    /**
     * Initialize network manager
     */
    async initialize(): Promise<void> {
        console.log('✅ NetworkManager initialized');
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        NetworkManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'NetworkManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Set timeout for requests
     */
    setTimeout(timeout: number): void {
        if (timeout > 0 && timeout <= 300000) { // Max 5 minutes
            this.timeout = timeout;
        }
    }

    /**
     * Set retry policy
     */
    setRetryPolicy(attempts: number, delay: number): void {
        this.retryAttempts = Math.max(0, Math.min(attempts, 10));
        this.retryDelay = Math.max(0, delay);
    }

    /**
     * Generic HTTP request method
     */
    async request<T>(
        url: string,
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>> {
        try {
            // Validate URL
            if (!this.validationManager.validateURL(url)) {
                return {
                    success: false,
                    error: this.errorManager.createError(
                        ErrorType.VALIDATION_ERROR,
                        'Invalid URL provided',
                        { url }
                    )
                };
            }

            // Prepare request
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), options.timeout || this.timeout);

            const requestOptions: RequestInit = {
                method: options.method || 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    ...(options.headers || {})
                },
                signal: controller.signal
            };

            // Add body for non-GET requests
            if (options.method !== 'GET' && options.body !== undefined) {
                if (typeof options.body === 'string') {
                    requestOptions.body = options.body;
                } else {
                    requestOptions.body = JSON.stringify(options.body);
                }
            }

            // Execute with retry logic
            const maxRetries = options.retries ?? this.retryAttempts;
            const retryDelay = options.retryDelay ?? this.retryDelay;

            const result = await retryWithBackoff(
                async () => {
                    const response = await fetch(url, requestOptions);

                    clearTimeout(timeoutId);

                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                    }

                    const data = await response.json();

                    return {
                        data,
                        status: response.status,
                        statusText: response.statusText,
                        headers: this.headersToRecord(response.headers),
                        timestamp: Date.now()
                    };
                },
                maxRetries,
                retryDelay
            );

            return {
                success: true,
                data: result
            };

        } catch (error) {
            clearTimeout(timeoutId);

            // Handle specific error types
            if (error.name === 'AbortError') {
                return {
                    success: false,
                    error: this.errorManager.createError(
                        ErrorType.NETWORK_ERROR,
                        'Request timeout',
                        { url, timeout: options.timeout || this.timeout }
                    )
                };
            }

            if (error instanceof TypeError && error.message === 'Failed to fetch') {
                return {
                    success: false,
                    error: this.errorManager.createError(
                        ErrorType.NETWORK_ERROR,
                        'Network connection failed',
                        { url, originalError: error.message }
                    )
                };
            }

            // Handle rate limiting
            if (error instanceof Error && error.message.includes('429')) {
                const resetTime = new Date(Date.now() + 60000); // Assume 1 minute reset
                return {
                    success: false,
                    error: this.errorManager.handleRateLimitError(resetTime, { url })
                };
            }

            // Handle authentication errors
            if (error instanceof Error && error.message.includes('401')) {
                return {
                    success: false,
                    error: this.errorManager.handleAuthenticationError(
                        'Authentication required',
                        { url }
                    )
                };
            }

            // Handle not found errors
            if (error instanceof Error && error.message.includes('404')) {
                return {
                    success: false,
                    error: this.errorManager.createError(
                        ErrorType.NOT_FOUND_ERROR,
                        'Resource not found',
                        { url }
                    )
                };
            }

            // Generic error
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    error instanceof Error ? error.message : 'Unknown network error',
                    { url, originalError: error }
                )
            };
        }
    }

    /**
     * GET request
     */
    async get<T>(
        url: string,
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>> {
        return this.request<T>(url, { ...options, method: 'GET' });
    }

    /**
     * POST request
     */
    async post<T>(
        url: string,
        data: any,
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>> {
        return this.request<T>(url, { ...options, method: 'POST', body: data });
    }

    /**
     * PUT request
     */
    async put<T>(
        url: string,
        data: any,
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>> {
        return this.request<T>(url, { ...options, method: 'PUT', body: data });
    }

    /**
     * DELETE request
     */
    async delete<T>(
        url: string,
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>> {
        return this.request<T>(url, { ...options, method: 'DELETE' });
    }

    /**
     * PATCH request
     */
    async patch<T>(
        url: string,
        data: any,
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>> {
        return this.request<T>(url, { ...options, method: 'PATCH', body: data });
    }

    /**
     * Download file with progress tracking
     */
    async download(
        url: string,
        onProgress?: (progress: number) => void
    ): AsyncResult<Blob> {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), this.timeout);

            const response = await fetch(url, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const contentLength = response.headers.get('content-length');
            const total = contentLength ? parseInt(contentLength, 10) : 0;
            let loaded = 0;

            const reader = response.body?.getReader();
            if (!reader) {
                throw new Error('No response body');
            }

            const chunks: Uint8Array[] = [];

            while (true) {
                const { done, value } = await reader.read();

                if (done) break;

                if (value) {
                    chunks.push(value);
                    loaded += value.length;

                    if (onProgress && total > 0) {
                        onProgress((loaded / total) * 100);
                    }
                }
            }

            const blob = new Blob(chunks);

            return {
                success: true,
                data: blob
            };

        } catch (error) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    error instanceof Error ? error.message : 'Download failed',
                    { url }
                )
            };
        }
    }

    /**
     * Get JSON with caching support
     */
    async getWithCache<T>(
        url: string,
        cacheKey: string,
        ttl?: number
    ): AsyncResult<NetworkResponse<T>> {
        // Import CacheManager dynamically to avoid circular dependencies
        const { CacheManager } = await import('./CacheManager.js');
        const cacheManager = CacheManager.getInstance();

        // Check cache first
        const cached = cacheManager.get<NetworkResponse<T>>(cacheKey);
        if (cached) {
            return { success: true, data: cached };
        }

        // Fetch from network
        const result = await this.get<T>(url);

        if (result.success && result.data) {
            cacheManager.set(cacheKey, result.data, ttl);
        }

        return result;
    }

    /**
     * Batch requests
     */
    async batchRequests<T>(
        urls: string[],
        options: NetworkRequestOptions = {}
    ): AsyncResult<NetworkResponse<T>[]> {
        const results: NetworkResponse<T>[] = [];
        const errors: Error[] = [];

        for (const url of urls) {
            const result = await this.request<T>(url, options);

            if (result.success && result.data) {
                results.push(result.data);
            } else if (result.error) {
                errors.push(new Error(result.error.message));
            }
        }

        if (errors.length > 0) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    `Batch request failed with ${errors.length} errors`,
                    { successful: results.length, failed: errors.length }
                )
            };
        }

        return {
            success: true,
            data: results
        };
    }

    /**
     * Check if online
     */
    async isOnline(): Promise<boolean> {
        try {
            // Try a lightweight request to a reliable endpoint
            const result = await this.get('https://www.google.com', {
                timeout: 5000
            });
            return result.success;
        } catch (error) {
            return false;
        }
    }

    /**
     * Get network status
     */
    async getStatus(): Promise<{
        online: boolean;
        latency: number;
        timestamp: number;
    }> {
        const start = Date.now();
        const online = await this.isOnline();
        const latency = Date.now() - start;

        return {
            online,
            latency,
            timestamp: Date.now()
        };
    }

    /**
     * Abort all pending requests (if using AbortController)
     */
    abortAll(): void {
        // Note: This would require tracking all active controllers
        // For now, we'll just log the intent
        console.log('[NetworkManager] Abort all requests called');
    }

    // ==================== Private Helper Methods ====================

    private headersToRecord(headers: Headers): Record<string, string> {
        const record: Record<string, string> = {};
        headers.forEach((value, key) => {
            record[key] = value;
        });
        return record;
    }

    /**
     * Validate response data structure
     */
    private validateResponseData<T>(data: any, expectedType?: new () => T): boolean {
        if (!expectedType) return true;

        try {
            // Basic validation - check if required properties exist
            const instance = new expectedType();
            const requiredProps = Object.keys(instance);

            return requiredProps.every(prop => prop in data);
        } catch (error) {
            return false;
        }
    }
}

/**
 * Convenience function for GET requests
 */
export async function httpGet<T>(
    url: string,
    options?: NetworkRequestOptions
): AsyncResult<NetworkResponse<T>> {
    return NetworkManager.getInstance().get<T>(url, options);
}

/**
 * Convenience function for POST requests
 */
export async function httpPost<T>(
    url: string,
    data: any,
    options?: NetworkRequestOptions
): AsyncResult<NetworkResponse<T>> {
    return NetworkManager.getInstance().post<T>(url, data, options);
}

/**
 * Fetch with timeout wrapper
 */
export async function fetchWithTimeout(
    url: string,
    timeout: number = 30000
): Promise<Response> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
        const response = await fetch(url, { signal: controller.signal });
        clearTimeout(timeoutId);
        return response;
    } catch (error) {
        clearTimeout(timeoutId);
        throw error;
    }
}

/**
 * Retry wrapper for any async operation
 */
export async function retry<T>(
    operation: () => Promise<T>,
    maxRetries: number = 3,
    delay: number = 1000
): Promise<T> {
    let lastError: Error | null = null;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return await operation();
        } catch (error) {
            lastError = error as Error;

            if (attempt < maxRetries) {
                console.log(`Retry attempt ${attempt + 1}/${maxRetries}`);
                await new Promise(resolve => setTimeout(resolve, delay * (attempt + 1)));
            }
        }
    }

    if (lastError) {
        throw lastError;
    }

    throw new Error('Operation failed after all retries');
}
/**
 * JNet GitHub Pages - Unified Error Management System
 * Handles all error types with consistent formatting and reporting
 */

import {
    ErrorType,
    ErrorDetails,
    IErrorManager,
    CallbackFunction
} from '../types/index.js';

/**
 * ErrorManager - Centralized error handling and reporting
 */
export class ErrorManager implements IErrorManager {
    private static instance: ErrorManager | null = null;
    private errorHandlers: CallbackFunction[] = [];
    private errorLog: ErrorDetails[] = [];
    private maxLogSize: number = 100;

    private constructor() {
        // Private constructor for singleton pattern
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ErrorManager {
        if (!ErrorManager.instance) {
            ErrorManager.instance = new ErrorManager();
        }
        return ErrorManager.instance;
    }

    /**
     * Initialize error manager
     */
    async initialize(): Promise<void> {
        console.log('✅ ErrorManager initialized');
        this.errorHandlers = [];
        this.errorLog = [];

        // Add default error handler for console logging
        this.addErrorHandler((error: ErrorDetails) => {
            console.error(`[ErrorManager] ${error.type}: ${error.message}`, error);
        });
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        this.errorHandlers = [];
        this.errorLog = [];
        ErrorManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'ErrorManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Create a standardized error object
     */
    createError(
        type: ErrorType,
        message: string,
        context?: Record<string, any>
    ): ErrorDetails {
        const error: ErrorDetails = {
            type,
            message: this.sanitizeMessage(message),
            timestamp: new Date().toISOString(),
            context: context ? this.sanitizeContext(context) : undefined,
            stack: new Error().stack
        };

        return error;
    }

    /**
     * Handle an error with all registered handlers
     */
    handleError(error: ErrorDetails | Error): void {
        const errorDetails = this.normalizeError(error);

        // Add to log
        this.addToLog(errorDetails);

        // Call all registered handlers
        this.errorHandlers.forEach(handler => {
            try {
                handler(errorDetails);
            } catch (handlerError) {
                console.error('Error handler failed:', handlerError);
            }
        });

        // Optionally show toast for user-facing errors
        if (this.isUserFacingError(errorDetails.type)) {
            this.showUserNotification(errorDetails);
        }
    }

    /**
     * Add a custom error handler
     */
    addErrorHandler(handler: (error: ErrorDetails) => void): void {
        if (typeof handler === 'function') {
            this.errorHandlers.push(handler);
        }
    }

    /**
     * Remove an error handler
     */
    removeErrorHandler(handler: (error: ErrorDetails) => void): void {
        this.errorHandlers = this.errorHandlers.filter(h => h !== handler);
    }

    /**
     * Handle network errors with retry logic
     */
    async handleNetworkError(
        error: Error,
        retryCallback: () => Promise<any>,
        maxRetries: number = 3
    ): Promise<any> {
        const networkError = this.createError(
            ErrorType.NETWORK_ERROR,
            `Network request failed: ${error.message}`,
            { originalError: error.message, retries: maxRetries }
        );

        this.handleError(networkError);

        if (maxRetries > 0) {
            console.log(`Retrying... attempts remaining: ${maxRetries}`);
            await new Promise(resolve => setTimeout(resolve, 1000 * (4 - maxRetries)));
            return retryCallback();
        }

        throw networkError;
    }

    /**
     * Handle validation errors
     */
    handleValidationError(
        field: string,
        value: any,
        rule: string,
        message: string
    ): ErrorDetails {
        const error = this.createError(
            ErrorType.VALIDATION_ERROR,
            `Validation failed for ${field}: ${message}`,
            { field, value, rule }
        );

        this.handleError(error);
        return error;
    }

    /**
     * Handle authentication errors
     */
    handleAuthenticationError(message: string, context?: Record<string, any>): ErrorDetails {
        const error = this.createError(
            ErrorType.AUTHENTICATION_ERROR,
            message,
            context
        );

        this.handleError(error);
        return error;
    }

    /**
     * Handle rate limit errors
     */
    handleRateLimitError(resetTime: Date, context?: Record<string, any>): ErrorDetails {
        const error = this.createError(
            ErrorType.RATE_LIMIT_ERROR,
            `Rate limit exceeded. Reset at: ${resetTime.toISOString()}`,
            { resetTime: resetTime.toISOString(), ...context }
        );

        this.handleError(error);
        return error;
    }

    /**
     * Get recent error log
     */
    getErrorLog(limit: number = 10): ErrorDetails[] {
        return this.errorLog.slice(-limit);
    }

    /**
     * Clear error log
     */
    clearLog(): void {
        this.errorLog = [];
    }

    /**
     * Get error statistics
     */
    getStats(): Record<ErrorType, number> {
        const stats = {} as Record<ErrorType, number>;
        Object.values(ErrorType).forEach(type => {
            stats[type] = 0;
        });

        this.errorLog.forEach(error => {
            stats[error.type] = (stats[error.type] || 0) + 1;
        });

        return stats;
    }

    // ==================== Private Helper Methods ====================

    private normalizeError(error: ErrorDetails | Error): ErrorDetails {
        if (this.isErrorDetails(error)) {
            return error;
        }

        return this.createError(
            ErrorType.UNKNOWN_ERROR,
            error.message || 'An unknown error occurred',
            { stack: error.stack }
        );
    }

    private isErrorDetails(error: any): error is ErrorDetails {
        return (
            error &&
            typeof error.type === 'string' &&
            typeof error.message === 'string' &&
            typeof error.timestamp === 'string'
        );
    }

    private addToLog(error: ErrorDetails): void {
        this.errorLog.push(error);

        // Prevent log from growing too large
        if (this.errorLog.length > this.maxLogSize) {
            this.errorLog = this.errorLog.slice(-this.maxLogSize);
        }
    }

    private sanitizeMessage(message: string): string {
        // Remove potentially sensitive information
        return message
            .replace(/password\s*=\s*[^&\s]*/gi, 'password=***')
            .replace(/token\s*=\s*[^&\s]*/gi, 'token=***')
            .replace(/api[_-]?key\s*=\s*[^&\s]*/gi, 'api_key=***')
            .replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '[EMAIL]');
    }

    private sanitizeContext(context: Record<string, any>): Record<string, any> {
        const sanitized: Record<string, any> = {};

        for (const [key, value] of Object.entries(context)) {
            if (typeof value === 'string') {
                sanitized[key] = this.sanitizeMessage(value);
            } else if (typeof value === 'object' && value !== null) {
                sanitized[key] = JSON.stringify(value).length > 200
                    ? '[Object too large]'
                    : this.sanitizeContext(value);
            } else {
                sanitized[key] = value;
            }
        }

        return sanitized;
    }

    private isUserFacingError(type: ErrorType): boolean {
        return [
            ErrorType.VALIDATION_ERROR,
            ErrorType.AUTHENTICATION_ERROR,
            ErrorType.RATE_LIMIT_ERROR
        ].includes(type);
    }

    private showUserNotification(error: ErrorDetails): void {
        // This will be connected to the ToastManager
        // For now, we'll use a simple alert as fallback
        if (typeof window !== 'undefined' && window.alert) {
            // Don't show alerts in tests or automated environments
            if (!process?.env?.NODE_ENV?.includes('test')) {
                // We'll defer to the toast system when available
                console.log(`User notification: ${error.message}`);
            }
        }
    }
}

/**
 * Convenience function for creating and handling errors
 */
export function createError(
    type: ErrorType,
    message: string,
    context?: Record<string, any>
): ErrorDetails {
    return ErrorManager.getInstance().createError(type, message, context);
}

/**
 * Convenience function for handling errors
 */
export function handleError(error: ErrorDetails | Error): void {
    ErrorManager.getInstance().handleError(error);
}

/**
 * Error boundary for async operations
 */
export async function withErrorHandling<T>(
    operation: () => Promise<T>,
    fallback?: T,
    errorType: ErrorType = ErrorType.UNKNOWN_ERROR
): Promise<T | undefined> {
    try {
        return await operation();
    } catch (error) {
        const errorManager = ErrorManager.getInstance();

        if (error instanceof Error) {
            const errorDetails = errorManager.createError(
                errorType,
                error.message,
                { operation: operation.name }
            );
            errorManager.handleError(errorDetails);
        } else {
            errorManager.handleError(error as ErrorDetails);
        }

        return fallback;
    }
}

/**
 * Retry wrapper with exponential backoff
 */
export async function retryWithBackoff<T>(
    operation: () => Promise<T>,
    maxRetries: number = 3,
    baseDelay: number = 1000
): Promise<T> {
    let lastError: Error | null = null;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return await operation();
        } catch (error) {
            lastError = error as Error;

            if (attempt < maxRetries) {
                const delay = baseDelay * Math.pow(2, attempt);
                console.log(`Retry attempt ${attempt + 1}/${maxRetries} after ${delay}ms delay`);
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
    }

    if (lastError) {
        throw lastError;
    }

    throw new Error('Operation failed after all retries');
}
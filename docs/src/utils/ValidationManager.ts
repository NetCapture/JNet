/**
 * JNet GitHub Pages - Input Validation and Sanitization Manager
 * Handles all input validation, sanitization, and security checks
 */

import {
    IValidationManager,
    ValidationResult,
    InputValidationRules,
    ErrorType
} from '../types/index.js';
import { ErrorManager } from '../errors/ErrorManager.js';

/**
 * ValidationManager - Handles input validation and sanitization
 */
export class ValidationManager implements IValidationManager {
    private static instance: ValidationManager | null = null;
    private errorManager: ErrorManager;

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ValidationManager {
        if (!ValidationManager.instance) {
            ValidationManager.instance = new ValidationManager();
        }
        return ValidationManager.instance;
    }

    /**
     * Initialize validation manager
     */
    async initialize(): Promise<void> {
        console.log('✅ ValidationManager initialized');
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        ValidationManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'ValidationManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Validate input against rules
     */
    validateInput(input: string, rules: InputValidationRules): ValidationResult {
        const errors: string[] = [];
        let sanitizedValue: string | undefined;

        // Handle null/undefined input
        if (input === null || input === undefined) {
            if (rules.required) {
                errors.push('Input is required');
            }
            return {
                isValid: errors.length === 0,
                errors,
                sanitizedValue: ''
            };
        }

        // Convert to string if needed
        let value = String(input);

        // Apply sanitization first if requested
        if (rules.sanitize) {
            value = this.sanitizeText(value);
            sanitizedValue = value;
        }

        // Required validation
        if (rules.required && value.trim().length === 0) {
            errors.push('This field is required');
        }

        // Min length validation
        if (rules.minLength !== undefined && value.length < rules.minLength) {
            errors.push(`Minimum length is ${rules.minLength} characters`);
        }

        // Max length validation
        if (rules.maxLength !== undefined && value.length > rules.maxLength) {
            errors.push(`Maximum length is ${rules.maxLength} characters`);
        }

        // Pattern validation
        if (rules.pattern && !rules.pattern.test(value)) {
            errors.push('Invalid format');
        }

        // Allowed characters validation
        if (rules.allowedChars) {
            const regex = new RegExp(`[^${rules.allowedChars}]`, 'g');
            const invalidChars = value.match(regex);
            if (invalidChars) {
                errors.push(`Contains invalid characters: ${[...new Set(invalidChars)].join(', ')}`);
            }
        }

        const isValid = errors.length === 0;

        // Log validation errors
        if (!isValid) {
            this.errorManager.handleValidationError(
                'input',
                input,
                'validation_rules',
                errors.join('; ')
            );
        }

        return {
            isValid,
            errors,
            sanitizedValue: sanitizedValue || value
        };
    }

    /**
     * Sanitize HTML to prevent XSS attacks
     */
    sanitizeHTML(html: string): string {
        if (!html || typeof html !== 'string') return '';

        const temp = document.createElement('div');
        temp.textContent = html;

        // Remove script tags
        let sanitized = html.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '');

        // Remove event handlers
        sanitized = sanitized.replace(/\son\w+\s*=\s*["'][^"']*["']/gi, '');

        // Remove javascript: URLs
        sanitized = sanitized.replace(/javascript:/gi, '');

        // Remove dangerous tags
        const dangerousTags = ['script', 'iframe', 'object', 'embed', 'meta', 'link'];
        dangerousTags.forEach(tag => {
            const regex = new RegExp(`<${tag}\\b[^>]*>(.*?)<\\/${tag}>`, 'gi');
            sanitized = sanitized.replace(regex, '');
        });

        // Remove inline styles that could be dangerous
        sanitized = sanitized.replace(/\sstyle\s*=\s*["'][^"']*["']/gi, '');

        return sanitized;
    }

    /**
     * Sanitize text for safe display
     */
    sanitizeText(text: string): string {
        if (!text || typeof text !== 'string') return '';

        return text
            .replace(/&/g, '&')
            .replace(/</g, '<')
            .replace(/>/g, '>')
            .replace(/"/g, '"')
            .replace(/'/g, ''')
            .replace(/\//g, '/');
    }

    /**
     * Validate URL format and safety
     */
    validateURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            // Basic URL format validation
            const urlPattern = /^(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?$/;
            if (!urlPattern.test(url)) return false;

            // Parse and validate
            const parsed = new URL(url.startsWith('http') ? url : `https://${url}`);

            // Check for dangerous protocols
            const allowedProtocols = ['http:', 'https:'];
            if (!allowedProtocols.includes(parsed.protocol)) return false;

            // Check for localhost in production
            if (window.location.hostname !== 'localhost' &&
                (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1')) {
                return false;
            }

            return true;
        } catch (error) {
            return false;
        }
    }

    /**
     * Validate email format
     */
    validateEmail(email: string): boolean {
        const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
        return emailPattern.test(email);
    }

    /**
     * Validate search query
     */
    validateSearchQuery(query: string): ValidationResult {
        const rules: InputValidationRules = {
            minLength: 2,
            maxLength: 100,
            sanitize: true,
            allowedChars: 'a-zA-Z0-9\\s\\-\\_\\+\\@\\.'
        };

        return this.validateInput(query, rules);
    }

    /**
     * Validate GitHub username
     */
    validateGitHubUsername(username: string): boolean {
        const pattern = /^[a-zA-Z0-9-]{1,39}$/;
        return pattern.test(username);
    }

    /**
     * Validate API response structure
     */
    validateAPIResponse(data: any, requiredFields: string[]): boolean {
        if (!data || typeof data !== 'object') return false;

        return requiredFields.every(field => {
            const parts = field.split('.');
            let current = data;

            for (const part of parts) {
                if (current && typeof current === 'object' && part in current) {
                    current = current[part];
                } else {
                    return false;
                }
            }

            return current !== undefined && current !== null;
        });
    }

    /**
     * Validate JSON structure
     */
    validateJSON(jsonString: string): boolean {
        try {
            JSON.parse(jsonString);
            return true;
        } catch (error) {
            return false;
        }
    }

    /**
     * Validate date format
     */
    validateDate(dateString: string): boolean {
        const date = new Date(dateString);
        return !isNaN(date.getTime());
    }

    /**
     * Validate number range
     */
    validateNumberRange(value: number, min: number, max: number): boolean {
        return typeof value === 'number' && !isNaN(value) && value >= min && value <= max;
    }

    /**
     * Validate array length
     */
    validateArrayLength(array: any[], min: number, max: number): boolean {
        return Array.isArray(array) && array.length >= min && array.length <= max;
    }

    /**
     * Sanitize object keys (prevent prototype pollution)
     */
    sanitizeObject(obj: Record<string, any>): Record<string, any> {
        const sanitized: Record<string, any> = {};

        for (const [key, value] of Object.entries(obj)) {
            // Prevent prototype pollution
            if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
                continue;
            }

            if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
                sanitized[key] = this.sanitizeObject(value);
            } else {
                sanitized[key] = value;
            }
        }

        return sanitized;
    }

    /**
     * Validate and sanitize user input for display
     */
    safeDisplay(input: string, options: {
        allowHTML?: boolean;
        maxLength?: number;
        trim?: boolean;
    } = {}): string {
        const { allowHTML = false, maxLength = 500, trim = true } = options;

        if (!input || typeof input !== 'string') return '';

        let result = input;

        // Trim whitespace
        if (trim) {
            result = result.trim();
        }

        // Limit length
        if (result.length > maxLength) {
            result = result.substring(0, maxLength) + '...';
        }

        // HTML sanitization
        if (!allowHTML) {
            result = this.sanitizeHTML(result);
        }

        return result;
    }

    /**
     * Validate password strength
     */
    validatePasswordStrength(password: string): {
        isValid: boolean;
        requirements: string[];
    } {
        const requirements: string[] = [];

        if (password.length < 8) {
            requirements.push('At least 8 characters');
        }

        if (!/[a-z]/.test(password)) {
            requirements.push('One lowercase letter');
        }

        if (!/[A-Z]/.test(password)) {
            requirements.push('One uppercase letter');
        }

        if (!/[0-9]/.test(password)) {
            requirements.push('One number');
        }

        if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
            requirements.push('One special character');
        }

        return {
            isValid: requirements.length === 0,
            requirements
        };
    }

    /**
     * Validate phone number (international format)
     */
    validatePhoneNumber(phone: string): boolean {
        // Basic international phone validation
        const phonePattern = /^\+?[1-9]\d{1,14}$/;
        const cleaned = phone.replace(/[^\d+]/g, '');
        return phonePattern.test(cleaned);
    }

    /**
     * Validate boolean value
     */
    validateBoolean(value: any): boolean {
        return typeof value === 'boolean' ||
               value === 'true' ||
               value === 'false' ||
               value === 1 ||
               value === 0 ||
               value === '1' ||
               value === '0';
    }

    /**
     * Convert and normalize boolean value
     */
    normalizeBoolean(value: any): boolean {
        if (typeof value === 'boolean') return value;
        if (typeof value === 'number') return value === 1;
        if (typeof value === 'string') {
            return value.toLowerCase() === 'true' || value === '1';
        }
        return Boolean(value);
    }

    /**
     * Validate file type against allowed types
     */
    validateFileType(file: File, allowedTypes: string[]): boolean {
        if (!file || !file.type) return false;
        return allowedTypes.includes(file.type);
    }

    /**
     * Validate file size
     */
    validateFileSize(file: File, maxSizeMB: number): boolean {
        if (!file || !file.size) return false;
        return file.size <= maxSizeMB * 1024 * 1024;
    }

    /**
     * Create validation rules for common scenarios
     */
    getCommonRules(type: string): InputValidationRules {
        const rules: Record<string, InputValidationRules> = {
            search: {
                minLength: 2,
                maxLength: 100,
                sanitize: true,
                allowedChars: 'a-zA-Z0-9\\s\\-\\_\\@\\.'
            },
            username: {
                minLength: 3,
                maxLength: 20,
                pattern: /^[a-zA-Z0-9_]+$/,
                sanitize: true
            },
            email: {
                maxLength: 254,
                pattern: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
                sanitize: false
            },
            comment: {
                minLength: 1,
                maxLength: 500,
                sanitize: true
            },
            url: {
                maxLength: 2048,
                sanitize: false
            }
        };

        return rules[type] || {};
    }

    /**
     * Batch validate multiple fields
     */
    validateBatch(fields: Record<string, { value: string; rules: InputValidationRules }>):
        { isValid: boolean; errors: Record<string, string[]> } {

        const errors: Record<string, string[]> = {};
        let isValid = true;

        for (const [fieldName, fieldData] of Object.entries(fields)) {
            const result = this.validateInput(fieldData.value, fieldData.rules);

            if (!result.isValid) {
                errors[fieldName] = result.errors;
                isValid = false;
            }
        }

        return { isValid, errors };
    }
}

/**
 * Convenience function for quick validation
 */
export function validate(input: string, rules: InputValidationRules): ValidationResult {
    return ValidationManager.getInstance().validateInput(input, rules);
}

/**
 * Convenience function for sanitization
 */
export function sanitizeHTML(html: string): string {
    return ValidationManager.getInstance().sanitizeHTML(html);
}

/**
 * Convenience function for safe display
 */
export function safeDisplay(text: string, options?: {
    allowHTML?: boolean;
    maxLength?: number;
    trim?: boolean;
}): string {
    return ValidationManager.getInstance().safeDisplay(text, options);
}
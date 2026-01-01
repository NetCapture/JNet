/**
 * JNet GitHub Pages - Configuration Manager
 * Centralized configuration management with validation and updates
 */

import {
    ConfigurationManager,
    AppConfig,
    UIConfig,
    DeepPartial,
    IModule
} from '../types/index.js';

/**
 * Default configuration
 */
const DEFAULT_CONFIG: AppConfig & UIConfig = {
    github: {
        owner: 'NetCapture',
        repo: 'JNet',
        apiBaseURL: 'https://api.github.com/repos/NetCapture/JNet'
    },
    api: {
        timeout: 30000,
        retryAttempts: 3,
        retryDelay: 1000
    },
    security: {
        maxInputLength: 1000,
        allowedDomains: ['github.com', 'api.github.com'],
        sanitizeHTML: true
    },
    performance: {
        cacheDuration: 300000, // 5 minutes
        maxCacheSize: 100,
        debounceDelay: 300
    },
    animations: {
        enabled: true,
        duration: 600
    },
    toast: {
        duration: 3000,
        position: 'top-right'
    },
    search: {
        maxResults: 10,
        minQueryLength: 2
    }
};

/**
 * ConfigManager - Manages application configuration
 */
export class ConfigManager implements ConfigurationManager {
    private static instance: ConfigManager | null = null;
    private config: AppConfig & UIConfig;

    private constructor() {
        this.config = { ...DEFAULT_CONFIG };
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ConfigManager {
        if (!ConfigManager.instance) {
            ConfigManager.instance = new ConfigManager();
        }
        return ConfigManager.instance;
    }

    /**
     * Initialize configuration manager
     */
    async initialize(): Promise<void> {
        console.log('✅ ConfigManager initialized');
        await this.loadFromLocalStorage();
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        ConfigManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'ConfigManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Get complete configuration
     */
    getConfig(): AppConfig & UIConfig {
        return { ...this.config };
    }

    /**
     * Update configuration with deep merge
     */
    updateConfig(config: DeepPartial<AppConfig & UIConfig>): void {
        this.config = this.deepMerge(this.config, config);
        this.saveToLocalStorage();
    }

    /**
     * Get specific configuration value
     */
    get<T extends keyof (AppConfig & UIConfig)>(key: T): (AppConfig & UIConfig)[T] {
        return this.config[key];
    }

    /**
     * Set specific configuration value
     */
    set<T extends keyof (AppConfig & UIConfig)>(key: T, value: (AppConfig & UIConfig)[T]): void {
        this.config[key] = value;
        this.saveToLocalStorage();
    }

    /**
     * Reset to default configuration
     */
    reset(): void {
        this.config = { ...DEFAULT_CONFIG };
        this.saveToLocalStorage();
    }

    /**
     * Get configuration as JSON string
     */
    toJSON(): string {
        return JSON.stringify(this.config, null, 2);
    }

    /**
     * Load configuration from JSON string
     */
    fromJSON(json: string): boolean {
        try {
            const parsed = JSON.parse(json);
            this.config = this.deepMerge({ ...DEFAULT_CONFIG }, parsed);
            this.saveToLocalStorage();
            return true;
        } catch (error) {
            console.error('Failed to parse configuration JSON:', error);
            return false;
        }
    }

    /**
     * Export configuration
     */
    export(): AppConfig & UIConfig {
        return { ...this.config };
    }

    /**
     * Import configuration
     */
    import(config: DeepPartial<AppConfig & UIConfig>): void {
        this.updateConfig(config);
    }

    /**
     * Validate current configuration
     */
    validate(): { isValid: boolean; errors: string[] } {
        const errors: string[] = [];

        // Validate GitHub config
        if (!this.config.github.owner || this.config.github.owner.trim().length === 0) {
            errors.push('GitHub owner is required');
        }

        if (!this.config.github.repo || this.config.github.repo.trim().length === 0) {
            errors.push('GitHub repo is required');
        }

        if (!this.config.github.apiBaseURL || !this.config.github.apiBaseURL.startsWith('http')) {
            errors.push('GitHub API base URL must be a valid URL');
        }

        // Validate API config
        if (this.config.api.timeout <= 0) {
            errors.push('API timeout must be positive');
        }

        if (this.config.api.retryAttempts < 0) {
            errors.push('API retry attempts cannot be negative');
        }

        // Validate security config
        if (this.config.security.maxInputLength <= 0) {
            errors.push('Max input length must be positive');
        }

        // Validate performance config
        if (this.config.performance.cacheDuration <= 0) {
            errors.push('Cache duration must be positive');
        }

        if (this.config.performance.maxCacheSize <= 0) {
            errors.push('Max cache size must be positive');
        }

        // Validate UI config
        if (this.config.toast.duration <= 0) {
            errors.push('Toast duration must be positive');
        }

        if (this.config.search.maxResults <= 0) {
            errors.push('Search max results must be positive');
        }

        if (this.config.search.minQueryLength <= 0) {
            errors.push('Search min query length must be positive');
        }

        return {
            isValid: errors.length === 0,
            errors
        };
    }

    /**
     * Get specific configuration section
     */
    getSection<T extends keyof (AppConfig & UIConfig)>(section: T): (AppConfig & UIConfig)[T] {
        return { ...this.config[section] } as any;
    }

    /**
     * Update specific configuration section
     */
    updateSection<T extends keyof (AppConfig & UIConfig)>(
        section: T,
        updates: DeepPartial<(AppConfig & UIConfig)[T]>
    ): void {
        const current = this.config[section];
        if (typeof current === 'object' && current !== null) {
            this.config[section] = this.deepMerge(current, updates) as (AppConfig & UIConfig)[T];
            this.saveToLocalStorage();
        }
    }

    /**
     * Get configuration for environment
     */
    getConfigForEnvironment(env: 'development' | 'production' | 'test'): AppConfig & UIConfig {
        const base = { ...DEFAULT_CONFIG };

        switch (env) {
            case 'development':
                return {
                    ...base,
                    api: { ...base.api, timeout: 60000, retryAttempts: 1 },
                    performance: { ...base.performance, cacheDuration: 60000 },
                    animations: { ...base.animations, enabled: false }
                };

            case 'test':
                return {
                    ...base,
                    api: { ...base.api, timeout: 5000, retryAttempts: 0 },
                    performance: { ...base.performance, cacheDuration: 0 },
                    toast: { ...base.toast, duration: 1000 }
                };

            case 'production':
            default:
                return base;
        }
    }

    /**
     * Merge configurations with deep merge
     */
    private deepMerge<T extends Record<string, any>>(
        target: T,
        source: DeepPartial<T>
    ): T {
        const result = { ...target };

        for (const key in source) {
            if (source[key] === undefined) continue;

            if (this.isObject(source[key]) && this.isObject(result[key])) {
                result[key] = this.deepMerge(result[key], source[key]);
            } else {
                result[key] = source[key] as T[Extract<keyof T, string>];
            }
        }

        return result;
    }

    /**
     * Type guard for objects
     */
    private isObject(value: any): boolean {
        return value && typeof value === 'object' && !Array.isArray(value);
    }

    /**
     * Save configuration to localStorage
     */
    private saveToLocalStorage(): void {
        if (typeof window !== 'undefined' && window.localStorage) {
            try {
                const configString = JSON.stringify(this.config);
                localStorage.setItem('jnet_config', configString);
            } catch (error) {
                console.warn('Failed to save config to localStorage:', error);
            }
        }
    }

    /**
     * Load configuration from localStorage
     */
    private async loadFromLocalStorage(): Promise<void> {
        if (typeof window !== 'undefined' && window.localStorage) {
            try {
                const configString = localStorage.getItem('jnet_config');
                if (configString) {
                    const parsed = JSON.parse(configString);
                    this.config = this.deepMerge(this.config, parsed);
                }
            } catch (error) {
                console.warn('Failed to load config from localStorage:', error);
            }
        }
    }

    /**
     * Get configuration keys
     */
    getKeys(): string[] {
        return Object.keys(this.config);
    }

    /**
     * Check if configuration has specific key
     */
    has(key: string): boolean {
        return key in this.config;
    }

    /**
     * Subscribe to configuration changes (placeholder for future implementation)
     */
    onChange(callback: (config: AppConfig & UIConfig) => void): void {
        // This could be implemented with a proper event system
        console.log('Configuration change listener registered:', callback.name);
    }

    /**
     * Get configuration metadata
     */
    getMetadata(): {
        version: string;
        timestamp: number;
        environment: string;
    } {
        return {
            version: '1.0.0',
            timestamp: Date.now(),
            environment: this.detectEnvironment()
        };
    }

    /**
     * Detect current environment
     */
    private detectEnvironment(): string {
        if (typeof window === 'undefined') return 'unknown';

        const hostname = window.location.hostname;

        if (hostname === 'localhost' || hostname === '127.0.0.1') {
            return 'development';
        }

        if (hostname.includes('test') || hostname.includes('staging')) {
            return 'staging';
        }

        return 'production';
    }
}

/**
 * Convenience function to get configuration
 */
export function getConfig<T extends keyof (AppConfig & UIConfig)>(key: T): (AppConfig & UIConfig)[T] {
    return ConfigManager.getInstance().get(key);
}

/**
 * Convenience function to set configuration
 */
export function setConfig<T extends keyof (AppConfig & UIConfig)>(key: T, value: (AppConfig & UIConfig)[T]): void {
    ConfigManager.getInstance().set(key, value);
}

/**
 * Configuration validator helper
 */
export function validateConfig(): { isValid: boolean; errors: string[] } {
    return ConfigManager.getInstance().validate();
}

/**
 * Environment-specific configuration helper
 */
export function getConfigForEnvironment(env: 'development' | 'production' | 'test'): AppConfig & UIConfig {
    return ConfigManager.getInstance().getConfigForEnvironment(env);
}
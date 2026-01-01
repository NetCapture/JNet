/**
 * JNet GitHub Pages - GitHub Statistics Manager
 * Fetches and manages GitHub repository statistics
 */

import {
    IGitHubStatsManager,
    GitHubRepositoryStats,
    AsyncResult,
    ErrorType
} from '../types/index.js';
import { ErrorManager, withErrorHandling } from '../errors/ErrorManager.js';
import { NetworkManager } from '../utils/NetworkManager.js';
import { CacheManager } from '../utils/CacheManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';

/**
 * GitHubStatsManager - Manages GitHub repository statistics
 */
export class GitHubStatsManager implements IGitHubStatsManager {
    private static instance: GitHubStatsManager | null = null;
    private errorManager: ErrorManager;
    private networkManager: NetworkManager;
    private cacheManager: CacheManager;
    private configManager: ConfigManager;
    private validationManager: ValidationManager;
    private stats: GitHubRepositoryStats | null = null;
    private lastUpdate: number | null = null;

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
        this.networkManager = NetworkManager.getInstance();
        this.cacheManager = CacheManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
    }

    /**
     * Get singleton instance
     */
    static getInstance(): GitHubStatsManager {
        if (!GitHubStatsManager.instance) {
            GitHubStatsManager.instance = new GitHubStatsManager();
        }
        return GitHubStatsManager.instance;
    }

    /**
     * Initialize GitHub stats manager
     */
    async initialize(): Promise<void> {
        console.log('✅ GitHubStatsManager initialized');
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        GitHubStatsManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'GitHubStatsManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Fetch GitHub repository statistics
     */
    async fetchStats(): AsyncResult<GitHubRepositoryStats> {
        const config = this.configManager.get('github');
        const cacheKey = `github_stats_${config.owner}_${config.repo}`;

        // Check cache first
        const cached = this.cacheManager.get<GitHubRepositoryStats>(cacheKey);
        if (cached) {
            this.stats = cached;
            this.lastUpdate = Date.now();
            return { success: true, data: cached };
        }

        // Validate API URL
        const apiUrl = `${config.apiBaseURL}`;
        if (!this.validationManager.validateURL(apiUrl)) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.VALIDATION_ERROR,
                    'Invalid GitHub API URL',
                    { url: apiUrl }
                )
            };
        }

        // Fetch from GitHub API
        const result = await withErrorHandling(
            async () => {
                const response = await this.networkManager.get<GitHubRepositoryStats>(apiUrl, {
                    timeout: 10000,
                    retries: 2,
                    headers: {
                        'Accept': 'application/vnd.github.v3+json'
                    }
                });

                if (!response.success) {
                    throw new Error(response.error?.message || 'Failed to fetch GitHub stats');
                }

                // Validate response data
                if (!this.validateStatsData(response.data)) {
                    throw new Error('Invalid GitHub stats data structure');
                }

                // Cache the result
                const cacheDuration = this.configManager.get('performance').cacheDuration;
                this.cacheManager.set(cacheKey, response.data, cacheDuration);

                // Update internal state
                this.stats = response.data;
                this.lastUpdate = Date.now();

                return response.data;
            },
            null,
            ErrorType.NETWORK_ERROR
        );

        if (result === null) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    'Failed to fetch GitHub statistics',
                    { apiUrl }
                )
            };
        }

        return { success: true, data: result };
    }

    /**
     * Get cached statistics
     */
    getStats(): GitHubRepositoryStats | null {
        return this.stats;
    }

    /**
     * Get last update timestamp
     */
    getLastUpdate(): number | null {
        return this.lastUpdate;
    }

    /**
     * Check if data is fresh (within cache duration)
     */
    isFresh(): boolean {
        if (!this.lastUpdate) return false;

        const cacheDuration = this.configManager.get('performance').cacheDuration;
        return Date.now() - this.lastUpdate < cacheDuration;
    }

    /**
     * Force refresh statistics
     */
    async refresh(): AsyncResult<GitHubRepositoryStats> {
        // Clear cache for GitHub stats
        const config = this.configManager.get('github');
        const cacheKey = `github_stats_${config.owner}_${config.repo}`;
        this.cacheManager.delete(cacheKey);

        // Fetch fresh data
        return this.fetchStats();
    }

    /**
     * Get specific stat by key
     */
    getStat(key: keyof GitHubRepositoryStats): number | string | null {
        if (!this.stats) return null;
        return this.stats[key];
    }

    /**
     * Get formatted statistics for display
     */
    getFormattedStats(): Record<string, string> {
        if (!this.stats) return {};

        return {
            stars: this.formatNumber(this.stats.stargazers_count),
            forks: this.formatNumber(this.stats.forks_count),
            issues: this.formatNumber(this.stats.open_issues_count),
            watchers: this.formatNumber(this.stats.watchers_count),
            subscribers: this.formatNumber(this.stats.subscribers_count),
            size: this.formatFileSize(this.stats.size * 1024), // GitHub size is in KB
            created: new Date(this.stats.created_at).toLocaleDateString(),
            updated: new Date(this.stats.updated_at).toLocaleDateString(),
            pushed: new Date(this.stats.pushed_at).toLocaleDateString()
        };
    }

    /**
     * Get stats growth information
     */
    getGrowthInfo(): {
        starsPerDay: number;
        forksPerDay: number;
        daysSinceCreation: number;
    } | null {
        if (!this.stats) return null;

        const created = new Date(this.stats.created_at);
        const now = new Date();
        const days = Math.max(1, Math.floor((now.getTime() - created.getTime()) / (1000 * 60 * 60 * 24)));

        return {
            starsPerDay: Math.round(this.stats.stargazers_count / days * 10) / 10,
            forksPerDay: Math.round(this.stats.forks_count / days * 10) / 10,
            daysSinceCreation: days
        };
    }

    /**
     * Export stats as JSON
     */
    export(): string {
        return JSON.stringify({
            stats: this.stats,
            lastUpdate: this.lastUpdate,
            isFresh: this.isFresh()
        }, null, 2);
    }

    // ==================== Private Helper Methods ====================

    private validateStatsData(data: any): data is GitHubRepositoryStats {
        if (!data || typeof data !== 'object') return false;

        const requiredFields = [
            'stargazers_count',
            'forks_count',
            'open_issues_count',
            'watchers_count',
            'subscribers_count',
            'size',
            'created_at',
            'updated_at',
            'pushed_at'
        ];

        return this.validationManager.validateAPIResponse(data, requiredFields);
    }

    private formatNumber(num: number): string {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        }
        if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    }

    private formatFileSize(bytes: number): string {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }
}

/**
 * Convenience function to get GitHub stats
 */
export function getGitHubStats(): GitHubRepositoryStats | null {
    return GitHubStatsManager.getInstance().getStats();
}

/**
 * Convenience function to fetch GitHub stats
 */
export async function fetchGitHubStats(): AsyncResult<GitHubRepositoryStats> {
    return GitHubStatsManager.getInstance().fetchStats();
}

/**
 * Convenience function to get formatted stats
 */
export function getFormattedStats(): Record<string, string> {
    return GitHubStatsManager.getInstance().getFormattedStats();
}
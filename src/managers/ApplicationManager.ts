/**
 * JNet GitHub Pages - Application Manager
 * Central orchestrator for all modules and application lifecycle
 */

import {
    ApplicationState,
    ApplicationModules,
    IModule,
    ErrorType
} from '../types/index.js';

import { ErrorManager } from '../errors/ErrorManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';
import { CacheManager } from '../utils/CacheManager.js';
import { NetworkManager } from '../utils/NetworkManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';
import { LanguageManager } from './LanguageManager.js';
import { ToastManager } from './ToastManager.js';
import { SearchManager } from './SearchManager.js';
import { RealDataManager } from './RealDataManager.js';
import { GitHubStatsManager } from './GitHubStatsManager.js';
import { UserManager } from './UserManager.js';
import { GitHubDiscussionsManager } from './GitHubDiscussionsManager.js';
import { DiscussionManager } from './DiscussionManager.js';

/**
 * ApplicationManager - Main application orchestrator
 */
export class ApplicationManager {
    private static instance: ApplicationManager | null = null;
    private modules: Partial<ApplicationModules> = {};
    private state: ApplicationState;
    private initialized: boolean = false;

    private constructor() {
        this.state = {
            currentLanguage: 'zh',
            githubStats: null,
            realData: null,
            searchIndex: [],
            cacheStats: { size: 0, hits: 0, misses: 0 },
            lastError: null,
            currentUser: null,
            isAuthenticated: false
        };
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ApplicationManager {
        if (!ApplicationManager.instance) {
            ApplicationManager.instance = new ApplicationManager();
        }
        return ApplicationManager.instance;
    }

    /**
     * Initialize all modules
     */
    async initialize(): Promise<boolean> {
        if (this.initialized) {
            console.log('⚠️ Application already initialized');
            return true;
        }

        console.log('🚀 Initializing JNet GitHub Pages Application...');

        try {
            // Initialize modules in dependency order
            await this.initConfigManager();
            await this.initErrorManager();
            await this.initValidationManager();
            await this.initCacheManager();
            await this.initNetworkManager();
            await this.initLanguageManager();
            await this.initToastManager();
            await this.initSearchManager();
            await this.initRealDataManager();
            await this.initGitHubStatsManager();

            // Initialize new managers for discussions and user auth
            await this.initUserManager();
            await this.initGitHubDiscussionsManager();
            await this.initDiscussionManager();

            // Update state
            this.updateStateFromModules();

            this.initialized = true;
            console.log('✅ Application initialized successfully');
            return true;

        } catch (error) {
            console.error('❌ Application initialization failed:', error);
            this.handleError(error);
            return false;
        }
    }

    /**
     * Initialize configuration manager
     */
    private async initConfigManager(): Promise<void> {
        this.modules.config = ConfigManager.getInstance();
        await this.modules.config.initialize();
    }

    /**
     * Initialize error manager
     */
    private async initErrorManager(): Promise<void> {
        this.modules.error = ErrorManager.getInstance();
        await this.modules.error.initialize();

        // Add global error handler
        this.modules.error.addErrorHandler((error) => {
            this.state.lastError = error;
            console.error('[Global Error Handler]', error);
        });
    }

    /**
     * Initialize validation manager
     */
    private async initValidationManager(): Promise<void> {
        this.modules.validation = ValidationManager.getInstance();
        await this.modules.validation.initialize();
    }

    /**
     * Initialize cache manager
     */
    private async initCacheManager(): Promise<void> {
        this.modules.cache = CacheManager.getInstance();
        await this.modules.cache.initialize();
    }

    /**
     * Initialize network manager
     */
    private async initNetworkManager(): Promise<void> {
        this.modules.network = NetworkManager.getInstance();
        await this.modules.network.initialize();

        // Configure from config
        const config = this.modules.config!.get('api');
        this.modules.network.setTimeout(config.timeout);
        this.modules.network.setRetryPolicy(config.retryAttempts, config.retryDelay);
    }

    /**
     * Initialize language manager
     */
    private async initLanguageManager(): Promise<void> {
        this.modules.language = LanguageManager.getInstance();
        await this.modules.language.initialize();

        // Auto-detect language
        this.modules.language.autoDetectLanguage();
        this.state.currentLanguage = this.modules.language.getCurrentLanguage();
    }

    /**
     * Initialize toast manager
     */
    private async initToastManager(): Promise<void> {
        this.modules.toast = ToastManager.getInstance();
        await this.modules.toast.initialize();
    }

    /**
     * Initialize search manager
     */
    private async initSearchManager(): Promise<void> {
        this.modules.search = SearchManager.getInstance();
        await this.modules.search.initialize();
        this.state.searchIndex = this.modules.search.getIndex();
    }

    /**
     * Initialize real data manager
     */
    private async initRealDataManager(): Promise<void> {
        this.modules.realData = RealDataManager.getInstance();
        await this.modules.realData.initialize();
    }

    /**
     * Initialize GitHub stats manager
     */
    private async initGitHubStatsManager(): Promise<void> {
        this.modules.gitHubStats = GitHubStatsManager.getInstance();
        await this.modules.gitHubStats.initialize();
    }

    /**
     * Initialize user manager
     */
    private async initUserManager(): Promise<void> {
        this.modules.user = new UserManager(
            this.modules.network!,
            this.modules.toast!,
            this.modules.error!
        );
        await this.modules.user.initialize();
    }

    /**
     * Initialize GitHub discussions manager
     */
    private async initGitHubDiscussionsManager(): Promise<void> {
        this.modules.gitHubDiscussions = new GitHubDiscussionsManager(
            this.modules.network!,
            this.modules.toast!,
            this.modules.error!,
            this.modules.cache!,
            this.modules.user!
        );
        await this.modules.gitHubDiscussions.initialize();
    }

    /**
     * Initialize discussion manager
     */
    private async initDiscussionManager(): Promise<void> {
        this.modules.discussion = new DiscussionManager(
            this.modules.gitHubDiscussions!,
            this.modules.toast!,
            this.modules.language!,
            this.modules.user!,
            this.modules.cache!
        );
        await this.modules.discussion.initialize();
    }

    /**
     * Load all data (async)
     */
    async loadData(): Promise<void> {
        if (!this.initialized) {
            await this.initialize();
        }

        console.log('📥 Loading application data...');

        // Load GitHub stats
        const statsResult = await this.modules.gitHubStats!.fetchStats();
        if (statsResult.success && statsResult.data) {
            this.state.githubStats = statsResult.data;
            console.log('✅ GitHub stats loaded');
        } else {
            console.warn('⚠️ Failed to load GitHub stats');
        }

        // Load real data
        const realDataResult = await this.modules.realData!.loadRealData();
        if (realDataResult.success && realDataResult.data) {
            this.state.realData = realDataResult.data;
            console.log('✅ Real data loaded');
        } else {
            console.warn('⚠️ Failed to load real data');
        }

        // Update cache stats
        this.updateCacheStats();

        console.log('📥 Data loading completed');
    }

    /**
     * Update state from modules
     */
    private updateStateFromModules(): void {
        if (this.modules.language) {
            this.state.currentLanguage = this.modules.language.getCurrentLanguage();
        }

        if (this.modules.search) {
            this.state.searchIndex = this.modules.search.getIndex();
        }

        if (this.modules.user) {
            this.state.currentUser = this.modules.user.getCurrentUser();
            this.state.isAuthenticated = this.modules.user.isAuthenticated();
        }

        this.updateCacheStats();
    }

    /**
     * Update cache statistics
     */
    private updateCacheStats(): void {
        if (this.modules.cache) {
            this.state.cacheStats = this.modules.cache.getStats();
        }
    }

    /**
     * Get application state
     */
    getState(): ApplicationState {
        return { ...this.state };
    }

    /**
     * Get specific module
     */
    getModule<T extends keyof ApplicationModules>(name: T): ApplicationModules[T] | null {
        return this.modules[name] || null;
    }

    /**
     * Handle application-level errors
     */
    private handleError(error: any): void {
        if (this.modules.error) {
            const errorDetails = this.modules.error.createError(
                ErrorType.UNKNOWN_ERROR,
                error instanceof Error ? error.message : 'Application error',
                { error }
            );
            this.modules.error.handleError(errorDetails);
        }
    }

    /**
     * Check if application is ready
     */
    isReady(): boolean {
        return this.initialized;
    }

    /**
     * Get application health
     */
    getHealth(): {
        status: 'healthy' | 'degraded' | 'unhealthy';
        modules: Record<string, boolean>;
        errors: number;
    } {
        const modules: Record<string, boolean> = {};

        for (const [name, module] of Object.entries(this.modules)) {
            modules[name] = module !== null && module !== undefined;
        }

        const errorStats = this.modules.error?.getStats() || {};
        const totalErrors = Object.values(errorStats).reduce((sum, count) => sum + count, 0);

        const allModulesReady = Object.values(modules).every(ready => ready);
        const status = allModulesReady ? 'healthy' : (Object.values(modules).some(ready => ready) ? 'degraded' : 'unhealthy');

        return {
            status,
            modules,
            errors: totalErrors
        };
    }

    /**
     * Change language
     */
    async changeLanguage(lang: string): Promise<boolean> {
        if (!this.modules.language) return false;

        const success = this.modules.language.setLanguage(lang);
        if (success) {
            this.state.currentLanguage = lang;
            // Show success toast
            if (this.modules.toast) {
                this.modules.toast.success(
                    'Language Changed',
                    `Switched to ${this.modules.language.getLanguageInfo(lang)?.native || lang}`
                );
            }
        }
        return success;
    }

    /**
     * Refresh all data
     */
    async refreshData(): Promise<void> {
        console.log('🔄 Refreshing application data...');

        // Clear cache
        this.modules.cache?.clear();

        // Reload data
        await this.loadData();

        // Show notification
        if (this.modules.toast) {
            this.modules.toast.success('Data Refreshed', 'All data has been updated');
        }
    }

    /**
     * Search application content
     */
    search(query: string) {
        return this.modules.search?.search(query) || [];
    }

    /**
     * Show notification
     */
    showNotification(title: string, message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info'): void {
        if (this.modules.toast) {
            this.modules.toast.show({ title, message, type });
        }
    }

    /**
     * Export application state
     */
    exportState(): string {
        return JSON.stringify({
            state: this.state,
            health: this.getHealth(),
            timestamp: Date.now()
        }, null, 2);
    }

    /**
     * Destroy application
     */
    destroy(): void {
        // Destroy all modules
        for (const module of Object.values(this.modules)) {
            if (module && typeof module.destroy === 'function') {
                module.destroy();
            }
        }

        this.modules = {};
        this.initialized = false;
        ApplicationManager.instance = null;

        console.log('🛑 Application destroyed');
    }

    /**
     * Get application info
     */
    getInfo(): {
        name: string;
        version: string;
        initialized: boolean;
        modules: string[];
    } {
        return {
            name: 'JNet GitHub Pages',
            version: '2.0.0',
            initialized: this.initialized,
            modules: Object.keys(this.modules)
        };
    }
}

/**
 * Convenience function to get application instance
 */
export function getApplication(): ApplicationManager {
    return ApplicationManager.getInstance();
}

/**
 * Initialize application
 */
export async function initApp(): Promise<boolean> {
    return ApplicationManager.getInstance().initialize();
}

/**
 * Get application state
 */
export function getState() {
    return ApplicationManager.getInstance().getState();
}

/**
 * Search application
 */
export function appSearch(query: string) {
    return ApplicationManager.getInstance().search(query);
}

/**
 * Show notification
 */
export function notify(title: string, message: string, type?: 'success' | 'error' | 'info' | 'warning') {
    ApplicationManager.getInstance().showNotification(title, message, type);
}
/**
 * JNet GitHub Pages - Module Factory
 * Creates and manages all application modules
 */

import {
    ModuleFactory,
    ConfigurationManager,
    ILanguageManager,
    IErrorManager,
    IValidationManager,
    ICacheManager,
    INetworkManager,
    IToastManager,
    ISearchManager,
    IRealDataManager,
    IGitHubStatsManager,
    IUserManager,
    IGitHubDiscussionsManager,
    IDiscussionManager,
    UIManager,
    SearchUIManager,
    LanguageSelectorManager
} from '../types/index.js';

// Import all managers
import { ConfigManager } from '../utils/ConfigManager.js';
import { ErrorManager } from '../errors/ErrorManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';
import { CacheManager } from '../utils/CacheManager.js';
import { NetworkManager } from '../utils/NetworkManager.js';
import { LanguageManager } from '../managers/LanguageManager.js';
import { ToastManager } from '../managers/ToastManager.js';
import { SearchManager } from '../managers/SearchManager.js';
import { RealDataManager } from '../managers/RealDataManager.js';
import { GitHubStatsManager } from '../managers/GitHubStatsManager.js';
import { UserManager } from '../managers/UserManager.js';
import { GitHubDiscussionsManager } from '../managers/GitHubDiscussionsManager.js';
import { DiscussionManager } from '../managers/DiscussionManager.js';
import { UIManager as UIManagerClass } from '../ui/UIManager.js';
import { SearchUIManager as SearchUIManagerClass } from '../ui/SearchUIManager.js';
import { LanguageSelectorManager as LanguageSelectorManagerClass } from '../ui/LanguageSelectorManager.js';

/**
 * ModuleFactory - Factory for creating all modules
 */
export class ModuleFactory implements ModuleFactory {
    private static instance: ModuleFactory | null = null;

    private constructor() {
        // Private constructor for singleton pattern
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ModuleFactory {
        if (!ModuleFactory.instance) {
            ModuleFactory.instance = new ModuleFactory();
        }
        return ModuleFactory.instance;
    }

    /**
     * Create configuration manager
     */
    createConfigManager(): ConfigurationManager {
        return ConfigManager.getInstance();
    }

    /**
     * Create language manager
     */
    createLanguageManager(): ILanguageManager {
        return LanguageManager.getInstance();
    }

    /**
     * Create error manager
     */
    createErrorManager(): IErrorManager {
        return ErrorManager.getInstance();
    }

    /**
     * Create validation manager
     */
    createValidationManager(): IValidationManager {
        return ValidationManager.getInstance();
    }

    /**
     * Create cache manager
     */
    createCacheManager(): ICacheManager {
        return CacheManager.getInstance();
    }

    /**
     * Create network manager
     */
    createNetworkManager(): INetworkManager {
        return NetworkManager.getInstance();
    }

    /**
     * Create toast manager
     */
    createToastManager(): IToastManager {
        return ToastManager.getInstance();
    }

    /**
     * Create search manager
     */
    createSearchManager(): ISearchManager {
        return SearchManager.getInstance();
    }

    /**
     * Create real data manager
     */
    createRealDataManager(): IRealDataManager {
        return RealDataManager.getInstance();
    }

    /**
     * Create GitHub stats manager
     */
    createGitHubStatsManager(): IGitHubStatsManager {
        return GitHubStatsManager.getInstance();
    }

    /**
     * Create user manager
     */
    createUserManager(): IUserManager {
        return new UserManager(
            this.createNetworkManager(),
            this.createToastManager(),
            this.createErrorManager()
        );
    }

    /**
     * Create GitHub discussions manager
     */
    createGitHubDiscussionsManager(): IGitHubDiscussionsManager {
        return new GitHubDiscussionsManager(
            this.createNetworkManager(),
            this.createToastManager(),
            this.createErrorManager(),
            this.createCacheManager(),
            this.createUserManager()
        );
    }

    /**
     * Create discussion manager
     */
    createDiscussionManager(): IDiscussionManager {
        return new DiscussionManager(
            this.createGitHubDiscussionsManager(),
            this.createToastManager(),
            this.createLanguageManager(),
            this.createUserManager()
        );
    }

    /**
     * Create UI manager
     */
    createUIManager(): UIManager {
        return new UIManagerClass();
    }

    /**
     * Create search UI manager
     */
    createSearchUIManager(
        searchManager: ISearchManager,
        languageManager: ILanguageManager
    ): SearchUIManager {
        return new SearchUIManagerClass(
            searchManager as SearchManager,
            languageManager as LanguageManager
        );
    }

    /**
     * Create language selector manager
     */
    createLanguageSelectorManager(
        languageManager: ILanguageManager
    ): LanguageSelectorManager {
        return new LanguageSelectorManagerClass(languageManager as LanguageManager);
    }

    /**
     * Create all modules at once
     */
    createAllModules() {
        return {
            config: this.createConfigManager(),
            language: this.createLanguageManager(),
            error: this.createErrorManager(),
            validation: this.createValidationManager(),
            cache: this.createCacheManager(),
            network: this.createNetworkManager(),
            toast: this.createToastManager(),
            search: this.createSearchManager(),
            realData: this.createRealDataManager(),
            gitHubStats: this.createGitHubStatsManager(),
            user: this.createUserManager(),
            gitHubDiscussions: this.createGitHubDiscussionsManager(),
            discussion: this.createDiscussionManager(),
            ui: this.createUIManager(),
            searchUI: this.createSearchUIManager(
                this.createSearchManager(),
                this.createLanguageManager()
            ),
            languageSelector: this.createLanguageSelectorManager(
                this.createLanguageManager()
            )
        };
    }

    /**
     * Initialize all modules
     */
    async initializeAllModules() {
        const modules = this.createAllModules();

        // Initialize in dependency order
        await modules.config.initialize();
        await modules.error.initialize();
        await modules.validation.initialize();
        await modules.cache.initialize();
        await modules.network.initialize();
        await modules.language.initialize();
        await modules.toast.initialize();
        await modules.search.initialize();
        await modules.realData.initialize();
        await modules.gitHubStats.initialize();
        await modules.user.initialize();
        await modules.gitHubDiscussions.initialize();
        await modules.discussion.initialize();
        await modules.ui.initialize();
        await modules.searchUI.initialize();
        await modules.languageSelector.initialize();

        return modules;
    }
}

/**
 * Convenience function to create all modules
 */
export async function createAndInitializeModules() {
    return ModuleFactory.getInstance().initializeAllModules();
}
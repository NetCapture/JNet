/**
 * JNet GitHub Pages - Module Loader
 * Handles dynamic loading of TypeScript modules for GitHub Pages
 * Version: 2.0.0
 */

// Module path mapping
const MODULE_PATHS = {
    // Core types
    types: './src/types/index.js',

    // Error handling
    errors: './src/errors/ErrorManager.js',

    // Utilities
    utils: './src/utils/ValidationManager.js',
    cache: './src/utils/CacheManager.js',
    network: './src/utils/NetworkManager.js',
    config: './src/utils/ConfigManager.js',

    // Managers
    language: './src/managers/LanguageManager.js',
    toast: './src/managers/ToastManager.js',
    search: './src/managers/SearchManager.js',
    realData: './src/managers/RealDataManager.js',
    gitHubStats: './src/managers/GitHubStatsManager.js',
    user: './src/managers/UserManager.js',
    gitHubDiscussions: './src/managers/GitHubDiscussionsManager.js',
    discussion: './src/managers/DiscussionManager.js',
    app: './src/managers/ApplicationManager.js',

    // UI Components
    ui: './src/ui/UIManager.js',
    searchUI: './src/ui/SearchUIManager.js',
    langSelector: './src/ui/LanguageSelectorManager.js',

    // Factory
    factory: './src/factories/ModuleFactory.js',

    // Main entry
    main: './src/main.js'
};

// Module cache
const moduleCache = new Map();

// Loading promises cache
const loadingPromises = new Map();

/**
 * Load a module dynamically with caching
 * @param {string} name - Module name
 * @returns {Promise<any>} - Module exports
 */
export async function loadModule(name) {
    // Return cached module if available
    if (moduleCache.has(name)) {
        return moduleCache.get(name);
    }

    // Return existing loading promise if module is currently loading
    if (loadingPromises.has(name)) {
        return loadingPromises[name];
    }

    const path = MODULE_PATHS[name];
    if (!path) {
        throw new Error(`Unknown module: ${name}. Available modules: ${Object.keys(MODULE_PATHS).join(', ')}`);
    }

    // Create loading promise
    const loadPromise = import(path)
        .then(module => {
            moduleCache.set(name, module);
            loadingPromises.delete(name);
            return module;
        })
        .catch(error => {
            loadingPromises.delete(name);
            throw new Error(`Failed to load module '${name}' from '${path}': ${error.message}`);
        });

    loadingPromises.set(name, loadPromise);

    return loadPromise;
}

/**
 * Load multiple modules at once
 * @param {string[]} names - Array of module names
 * @returns {Promise<Record<string, any>>} - Object with loaded modules
 */
export async function loadModules(names) {
    const modules = {};
    const promises = names.map(name =>
        loadModule(name).then(module => {
            modules[name] = module;
        })
    );

    await Promise.all(promises);
    return modules;
}

/**
 * Check if module is loaded
 * @param {string} name - Module name
 * @returns {boolean}
 */
export function isModuleLoaded(name) {
    return moduleCache.has(name);
}

/**
 * Get loaded module
 * @param {string} name - Module name
 * @returns {any} - Module exports or null
 */
export function getModule(name) {
    return moduleCache.get(name) || null;
}

/**
 * Clear module cache
 */
export function clearCache() {
    moduleCache.clear();
    loadingPromises.clear();
}

/**
 * Get module dependencies
 * @param {string} name - Module name
 * @returns {string[]} - Required module names
 */
function getDependencies(name) {
    const dependencies = {
        main: ['app', 'factory'],
        app: ['factory', 'errors', 'utils', 'cache', 'network', 'config', 'language', 'toast', 'search', 'realData', 'gitHubStats', 'user', 'gitHubDiscussions', 'discussion', 'ui'],
        factory: ['types', 'errors', 'utils', 'cache', 'network', 'config', 'language', 'toast', 'search', 'realData', 'gitHubStats', 'user', 'gitHubDiscussions', 'discussion', 'ui', 'searchUI', 'langSelector'],
        ui: ['language', 'toast', 'utils'],
        searchUI: ['search', 'language', 'utils'],
        langSelector: ['language', 'toast', 'utils'],
        language: ['errors', 'utils'],
        toast: ['errors', 'config'],
        search: ['errors', 'utils', 'config'],
        realData: ['errors', 'network', 'cache', 'config', 'utils'],
        gitHubStats: ['errors', 'network', 'cache', 'config', 'utils'],
        user: ['errors', 'network', 'toast', 'config'],
        gitHubDiscussions: ['errors', 'network', 'toast', 'cache', 'user', 'config'],
        discussion: ['gitHubDiscussions', 'toast', 'language', 'user', 'cache'],
        network: ['errors', 'utils'],
        cache: ['errors'],
        utils: ['errors'],
        config: [],
        errors: [],
        types: []
    };

    return dependencies[name] || [];
}

/**
 * Load module with dependencies
 * @param {string} name - Module name
 * @returns {Promise<any>} - Module exports
 */
export async function loadModuleWithDependencies(name) {
    const deps = getDependencies(name);

    // Load dependencies first
    if (deps.length > 0) {
        await loadModules(deps);
    }

    // Then load the module
    return loadModule(name);
}

/**
 * Initialize application
 * @returns {Promise<void>}
 */
export async function initApp() {
    console.log('🚀 Initializing JNet Application via Module Loader...');

    // Show loading state
    showLoadingState();

    try {
        // Load main module with all dependencies
        const mainModule = await loadModuleWithDependencies('main');

        // Wait for DOM to be ready
        await waitForDOM();

        // Initialize application
        await mainModule.main();

        // Hide loading state
        hideLoadingState();

        // Show success notification
        showSuccessNotification();

        console.log('✅ Application initialized successfully');

    } catch (error) {
        console.error('❌ Application initialization failed:', error);
        hideLoadingState();
        showErrorState(error);
    }
}

/**
 * Wait for DOM to be ready
 */
function waitForDOM() {
    return new Promise(resolve => {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', resolve);
        } else {
            resolve();
        }
    });
}

/**
 * Show loading state
 */
function showLoadingState() {
    const existing = document.getElementById('jnet-loader');
    if (existing) return;

    const loader = document.createElement('div');
    loader.id = 'jnet-loader';
    loader.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(15, 23, 42, 0.95);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        color: white;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    `;

    loader.innerHTML = `
        <div style="font-size: 3rem; margin-bottom: 20px; animation: spin 1s linear infinite;">⏳</div>
        <div style="font-size: 1.2rem; font-weight: 600;">JNet Loading...</div>
        <div style="font-size: 0.9rem; color: rgba(255,255,255,0.6); margin-top: 8px;">Initializing TypeScript modules</div>
        <style>
            @keyframes spin {
                from { transform: rotate(0deg); }
                to { transform: rotate(360deg); }
            }
        </style>
    `;

    document.body.appendChild(loader);
}

/**
 * Hide loading state
 */
function hideLoadingState() {
    const loader = document.getElementById('jnet-loader');
    if (loader) {
        loader.style.opacity = '0';
        loader.style.transition = 'opacity 0.3s ease';
        setTimeout(() => loader.remove(), 300);
    }
}

/**
 * Show success notification
 */
function showSuccessNotification() {
    // Check if toast manager is available
    setTimeout(() => {
        const toast = getModule('toast');
        if (toast && toast.success) {
            toast.success('JNet Ready', 'TypeScript scaffold loaded successfully');
        } else {
            // Fallback notification
            const notification = document.createElement('div');
            notification.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                background: #10b981;
                color: white;
                padding: 16px 20px;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                z-index: 10000;
                font-weight: 600;
                animation: slideIn 0.3s ease;
            `;
            notification.textContent = '✅ JNet Application Ready';
            document.body.appendChild(notification);

            setTimeout(() => {
                notification.style.opacity = '0';
                notification.style.transition = 'opacity 0.3s ease';
                setTimeout(() => notification.remove(), 300);
            }, 3000);
        }
    }, 1000);
}

/**
 * Show error state
 */
function showErrorState(error) {
    const container = document.createElement('div');
    container.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: #fee;
        border: 2px solid #c00;
        padding: 24px;
        border-radius: 12px;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        z-index: 10000;
        max-width: 500px;
        box-shadow: 0 8px 24px rgba(0,0,0,0.3);
    `;

    container.innerHTML = `
        <h3 style="margin: 0 0 12px 0; color: #c00;">⚠️ Application Error</h3>
        <p style="margin: 0 0 16px 0; color: #333; font-size: 0.95rem;">${error.message}</p>
        <div style="display: flex; gap: 8px;">
            <button onclick="location.reload()" style="flex: 1; padding: 10px 16px; background: #c00; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600;">Retry</button>
            <button onclick="this.parentElement.parentElement.remove()" style="flex: 1; padding: 10px 16px; background: #666; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600;">Close</button>
        </div>
        <details style="margin-top: 12px; font-size: 0.85rem; color: #666;">
            <summary>Technical Details</summary>
            <pre style="margin-top: 8px; overflow-x: auto; background: #f5f5f5; padding: 8px; border-radius: 4px;">${error.stack || 'No stack trace available'}</pre>
        </details>
    `;

    document.body.appendChild(container);
}

/**
 * Get module status
 * @returns {Object} - Module loading status
 */
export function getModuleStatus() {
    const status = {};
    for (const [name, path] of Object.entries(MODULE_PATHS)) {
        status[name] = {
            path,
            loaded: moduleCache.has(name),
            loading: loadingPromises.has(name)
        };
    }
    return status;
}

/**
 * Preload critical modules
 * @returns {Promise<void>}
 */
export async function preloadCriticalModules() {
    const critical = ['types', 'errors', 'utils', 'config'];
    console.log('📦 Preloading critical modules:', critical);
    await loadModules(critical);
}

// Auto-initialization
if (typeof window !== 'undefined') {
    // Export to window for debugging
    window.JNetLoader = {
        loadModule,
        loadModules,
        loadModuleWithDependencies,
        getModule,
        clearCache,
        getModuleStatus,
        initApp,
        preloadCriticalModules
    };

    // Auto-start when DOM is ready
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        // Preload critical modules first
        preloadCriticalModules().then(() => {
            initApp();
        });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            preloadCriticalModules().then(() => {
                initApp();
            });
        });
    }
}

// Export for module usage
export default {
    loadModule,
    loadModules,
    loadModuleWithDependencies,
    getModule,
    clearCache,
    getModuleStatus,
    initApp,
    preloadCriticalModules
};
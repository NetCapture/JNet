/**
 * JNet GitHub Pages - Main Entry Point
 * Initializes the application and sets up all modules
 */

import { ApplicationManager } from './managers/ApplicationManager.js';
import { ModuleFactory } from './factories/ModuleFactory.js';

/**
 * Main application entry point
 */
export async function main() {
    console.log('🚀 Starting JNet GitHub Pages Application...');

    try {
        // Initialize application manager
        const app = ApplicationManager.getInstance();
        const initialized = await app.initialize();

        if (!initialized) {
            console.error('❌ Application failed to initialize');
            return;
        }

        // Load data
        await app.loadData();

        // Get modules
        const ui = app.getModule('ui');
        const language = app.getModule('language');
        const toast = app.getModule('toast');

        // Initialize UI
        if (ui) {
            ui.initializeUI();
        }

        // Show welcome message
        if (toast) {
            toast.success(
                'Welcome to JNet',
                'TypeScript scaffold optimization complete!'
            );
        }

        // Update UI with data
        const state = app.getState();
        if (ui && state.githubStats) {
            ui.updateStatsUI(state.githubStats);
        }

        if (ui && state.realData) {
            ui.updateRealDataUI(state.realData);
        }

        // Update language UI
        if (ui && language) {
            ui.updateLanguageUI(language.getCurrentLanguage());
        }

        console.log('✅ Application started successfully');
        console.log('📊 Health:', app.getHealth());
        console.log('📝 Info:', app.getInfo());

    } catch (error) {
        console.error('❌ Application startup failed:', error);

        // Try to show error notification
        try {
            const app = ApplicationManager.getInstance();
            const toast = app.getModule('toast');
            if (toast) {
                toast.error('Startup Error', 'Failed to initialize application');
            }
        } catch (e) {
            // Fallback to console
            console.error('Fallback error:', e);
        }
    }
}

/**
 * Export main functions for global access
 */
export {
    ApplicationManager,
    ModuleFactory
};

// Auto-start if in browser environment
if (typeof window !== 'undefined') {
    // Wait for DOM to be ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', main);
    } else {
        main();
    }
}

/**
 * Global error handler
 */
window.addEventListener('error', (event) => {
    console.error('Global error:', event.error);
    const app = ApplicationManager.getInstance();
    const errorManager = app.getModule('error');
    if (errorManager) {
        errorManager.handleError(event.error);
    }
});

/**
 * Unhandled promise rejection handler
 */
window.addEventListener('unhandledrejection', (event) => {
    console.error('Unhandled promise rejection:', event.reason);
    const app = ApplicationManager.getInstance();
    const errorManager = app.getModule('error');
    if (errorManager) {
        errorManager.handleError(event.reason);
    }
});
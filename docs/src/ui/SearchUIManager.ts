/**
 * JNet GitHub Pages - Search UI Manager
 * Handles search interface and user interactions
 */

import {
    SearchUIManager as ISearchUIManager,
    SearchResult,
    SearchStats
} from '../types/index.js';
import { SearchManager } from '../managers/SearchManager.js';
import { LanguageManager } from '../managers/LanguageManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';

/**
 * SearchUIManager - Manages search interface
 */
export class SearchUIManager implements ISearchUIManager {
    private searchManager: SearchManager;
    private languageManager: LanguageManager;
    private configManager: ConfigManager;
    private validationManager: ValidationManager;
    private isOpen: boolean;
    private currentQuery: string;
    private selectedIndex: number;
    private searchResults: SearchResult[];
    private debounceTimer: NodeJS.Timeout | null = null;

    constructor(searchManager: SearchManager, languageManager: LanguageManager) {
        this.searchManager = searchManager;
        this.languageManager = languageManager;
        this.configManager = ConfigManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
        this.isOpen = false;
        this.currentQuery = '';
        this.selectedIndex = 0;
        this.searchResults = [];
    }

    /**
     * Initialize search UI
     */
    async initialize(): Promise<void> {
        this.createSearchUI();
        this.bindEvents();
        console.log('🔍 Search UI initialized');
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        this.removeSearchUI();
        this.searchResults = [];
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'SearchUIManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Create search UI elements
     */
    private createSearchUI(): void {
        if (typeof document === 'undefined') return;

        // Search trigger button
        const searchBtn = document.createElement('button');
        searchBtn.className = 'search-trigger-btn';
        searchBtn.innerHTML = '🔍';
        searchBtn.title = this.languageManager.translate('search_hint');
        searchBtn.id = 'searchTriggerBtn';
        document.body.appendChild(searchBtn);

        // Search modal
        const modal = document.createElement('div');
        modal.className = 'search-modal';
        modal.id = 'searchModal';
        modal.innerHTML = `
            <div class="search-overlay"></div>
            <div class="search-container">
                <div class="search-header">
                    <div class="search-input-wrapper">
                        <span class="search-icon">🔍</span>
                        <input type="text"
                               id="searchInput"
                               placeholder="${this.languageManager.translate('search_placeholder')}"
                               autocomplete="off">
                        <span class="search-shortcut">Ctrl K</span>
                    </div>
                    <button class="search-close" id="searchClose">✕</button>
                </div>
                <div class="search-hint">${this.languageManager.translate('search_hint')}</div>
                <div class="search-results" id="searchResults">
                    <div class="search-empty">
                        <div class="empty-icon">🔍</div>
                        <div class="empty-text">开始搜索...</div>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        // Add styles
        this.addSearchStyles();
    }

    /**
     * Remove search UI
     */
    private removeSearchUI(): void {
        const searchBtn = document.getElementById('searchTriggerBtn');
        const modal = document.getElementById('searchModal');
        const styles = document.getElementById('search-styles');

        if (searchBtn) searchBtn.remove();
        if (modal) modal.remove();
        if (styles) styles.remove();
    }

    /**
     * Add search styles
     */
    private addSearchStyles(): void {
        if (document.getElementById('search-styles')) return;

        const style = document.createElement('style');
        style.id = 'search-styles';
        style.textContent = `
            .search-trigger-btn {
                position: fixed;
                top: 20px;
                right: 20px;
                width: 44px;
                height: 44px;
                border-radius: 12px;
                background: rgba(255, 255, 255, 0.95);
                border: 1px solid #e2e8f0;
                cursor: pointer;
                font-size: 1.2rem;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: all 0.3s ease;
                z-index: 999;
                box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }

            .search-trigger-btn:hover {
                transform: scale(1.1);
                box-shadow: 0 4px 12px rgba(0,0,0,0.2);
            }

            .search-modal {
                display: none;
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                z-index: 10000;
            }

            .search-modal.active {
                display: block;
            }

            .search-overlay {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.5);
                backdrop-filter: blur(4px);
                animation: fadeIn 0.2s ease;
            }

            .search-container {
                position: absolute;
                top: 10%;
                left: 50%;
                transform: translateX(-50%);
                width: 90%;
                max-width: 700px;
                background: white;
                border-radius: 16px;
                box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                animation: slideDown 0.3s ease;
                overflow: hidden;
            }

            .search-header {
                display: flex;
                align-items: center;
                gap: 12px;
                padding: 20px;
                border-bottom: 1px solid #e2e8f0;
                background: #f8fafc;
            }

            .search-input-wrapper {
                flex: 1;
                display: flex;
                align-items: center;
                gap: 10px;
                background: white;
                border: 2px solid #e2e8f0;
                border-radius: 10px;
                padding: 10px 14px;
                transition: all 0.2s ease;
            }

            .search-input-wrapper:focus-within {
                border-color: #2563eb;
                box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
            }

            .search-icon {
                font-size: 1.2rem;
                color: #64748b;
            }

            #searchInput {
                flex: 1;
                border: none;
                outline: none;
                font-size: 1rem;
                font-family: inherit;
            }

            .search-shortcut {
                font-size: 0.75rem;
                color: #94a3b8;
                background: #f1f5f9;
                padding: 4px 8px;
                border-radius: 6px;
                font-weight: 600;
            }

            .search-close {
                width: 36px;
                height: 36px;
                border: none;
                background: white;
                border-radius: 8px;
                cursor: pointer;
                font-size: 1.2rem;
                color: #64748b;
                transition: all 0.2s ease;
            }

            .search-close:hover {
                background: #fee2e2;
                color: #ef4444;
            }

            .search-hint {
                padding: 8px 20px;
                font-size: 0.85rem;
                color: #64748b;
                background: #f8fafc;
                border-bottom: 1px solid #e2e8f0;
            }

            .search-results {
                max-height: 500px;
                overflow-y: auto;
                background: white;
            }

            .search-results::-webkit-scrollbar {
                width: 8px;
            }

            .search-results::-webkit-scrollbar-track {
                background: #f1f5f9;
            }

            .search-results::-webkit-scrollbar-thumb {
                background: #cbd5e1;
                border-radius: 4px;
            }

            .search-results::-webkit-scrollbar-thumb:hover {
                background: #94a3b8;
            }

            .search-result-item {
                padding: 16px 20px;
                border-bottom: 1px solid #f1f5f9;
                cursor: pointer;
                transition: all 0.2s ease;
                display: flex;
                gap: 12px;
                align-items: flex-start;
            }

            .search-result-item:hover,
            .search-result-item.selected {
                background: #f8fafc;
                border-left: 3px solid #2563eb;
                padding-left: 17px;
            }

            .result-icon {
                font-size: 1.3rem;
                min-width: 24px;
                margin-top: 2px;
            }

            .result-content {
                flex: 1;
            }

            .result-title {
                font-weight: 600;
                color: #1e293b;
                margin-bottom: 4px;
                font-size: 0.95rem;
            }

            .result-content-text {
                font-size: 0.85rem;
                color: #64748b;
                line-height: 1.4;
                margin-bottom: 6px;
            }

            .result-meta {
                display: flex;
                gap: 8px;
                align-items: center;
                font-size: 0.75rem;
            }

            .result-category {
                background: #e0f2fe;
                color: #0369a1;
                padding: 2px 8px;
                border-radius: 10px;
                font-weight: 600;
                text-transform: uppercase;
            }

            .result-score {
                color: #94a3b8;
            }

            mark {
                background: #fef08a;
                color: #854d0e;
                padding: 0 2px;
                border-radius: 2px;
                font-weight: 600;
            }

            .search-empty {
                padding: 60px 20px;
                text-align: center;
                color: #94a3b8;
            }

            .empty-icon {
                font-size: 3rem;
                margin-bottom: 12px;
                opacity: 0.5;
            }

            .empty-text {
                font-size: 1rem;
                font-weight: 600;
            }

            .search-stats {
                padding: 12px 20px;
                background: #f8fafc;
                border-top: 1px solid #e2e8f0;
                display: flex;
                gap: 8px;
                flex-wrap: wrap;
                font-size: 0.8rem;
            }

            .stat-badge {
                background: white;
                padding: 4px 10px;
                border-radius: 12px;
                border: 1px solid #e2e8f0;
                font-weight: 600;
            }

            .stat-badge .count {
                color: #2563eb;
                margin-left: 4px;
            }

            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }

            @keyframes slideDown {
                from {
                    opacity: 0;
                    transform: translateX(-50%) translateY(-20px);
                }
                to {
                    opacity: 1;
                    transform: translateX(-50%) translateY(0);
                }
            }

            @media (max-width: 768px) {
                .search-trigger-btn {
                    top: 10px;
                    right: 10px;
                    width: 40px;
                    height: 40px;
                }

                .search-container {
                    top: 5%;
                    width: 95%;
                }

                .search-header {
                    padding: 16px;
                    flex-wrap: wrap;
                }

                .search-input-wrapper {
                    width: 100%;
                    order: 1;
                }

                .search-close {
                    order: 2;
                }

                .search-results {
                    max-height: 60vh;
                }

                .search-result-item {
                    padding: 12px 16px;
                }

                .search-shortcut {
                    display: none;
                }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Bind events
     */
    private bindEvents(): void {
        // Trigger button
        const triggerBtn = document.getElementById('searchTriggerBtn');
        if (triggerBtn) {
            triggerBtn.addEventListener('click', () => this.openSearch());
        }

        // Close button
        const closeBtn = document.getElementById('searchClose');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.closeSearch());
        }

        // Overlay click
        const overlay = document.querySelector('.search-overlay');
        if (overlay) {
            overlay.addEventListener('click', () => this.closeSearch());
        }

        // Search input
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.handleInput((e.target as HTMLInputElement).value));
            searchInput.addEventListener('keydown', (e) => this.handleKeyDown(e));
        }

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            // Ctrl+K or Cmd+K to open
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                this.openSearch();
            }

            // ESC to close
            if (e.key === 'Escape' && this.isOpen) {
                this.closeSearch();
            }
        });
    }

    /**
     * Open search modal
     */
    openSearch(): void {
        this.isOpen = true;
        const modal = document.getElementById('searchModal');
        const input = document.getElementById('searchInput');

        if (modal) {
            modal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }

        if (input) {
            setTimeout(() => input.focus(), 100);
        }
    }

    /**
     * Close search modal
     */
    closeSearch(): void {
        this.isOpen = false;
        const modal = document.getElementById('searchModal');

        if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = '';
        }

        // Reset state
        this.currentQuery = '';
        this.selectedIndex = 0;
        this.searchResults = [];

        const input = document.getElementById('searchInput');
        if (input) input.value = '';

        this.renderResults([]);
    }

    /**
     * Handle input with debouncing
     */
    handleInput(query: string): void {
        this.currentQuery = query.trim();

        if (this.currentQuery.length === 0) {
            this.renderResults([]);
            return;
        }

        // Debounce
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }

        this.debounceTimer = setTimeout(() => {
            const results = this.searchManager.search(this.currentQuery);
            this.searchResults = results;
            this.selectedIndex = 0;
            this.renderResults(results);
        }, 300);
    }

    /**
     * Handle keyboard navigation
     */
    handleKeyDown(e: KeyboardEvent): void {
        if (!this.isOpen) return;

        const resultsContainer = document.getElementById('searchResults');
        const items = resultsContainer?.querySelectorAll('.search-result-item');

        switch(e.key) {
            case 'ArrowDown':
                e.preventDefault();
                if (items && items.length > 0) {
                    this.selectedIndex = Math.min(this.selectedIndex + 1, items.length - 1);
                    this.updateSelection(items);
                }
                break;

            case 'ArrowUp':
                e.preventDefault();
                if (items && items.length > 0) {
                    this.selectedIndex = Math.max(this.selectedIndex - 1, 0);
                    this.updateSelection(items);
                }
                break;

            case 'Enter':
                e.preventDefault();
                if (items && items[this.selectedIndex]) {
                    (items[this.selectedIndex] as HTMLElement).click();
                }
                break;
        }
    }

    /**
     * Update selection highlight
     */
    private updateSelection(items: NodeList): void {
        items.forEach((item, index) => {
            if (index === this.selectedIndex) {
                (item as HTMLElement).classList.add('selected');
                item.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            } else {
                (item as HTMLElement).classList.remove('selected');
            }
        });
    }

    /**
     * Render search results
     */
    renderResults(results: SearchResult[]): void {
        const container = document.getElementById('searchResults');
        if (!container) return;

        if (results.length === 0) {
            if (this.currentQuery.length === 0) {
                container.innerHTML = `
                    <div class="search-empty">
                        <div class="empty-icon">🔍</div>
                        <div class="empty-text">开始搜索...</div>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <div class="search-empty">
                        <div class="empty-icon">😕</div>
                        <div class="empty-text">${this.languageManager.translate('search_no_results')}</div>
                        <div style="margin-top: 8px; font-size: 0.85rem; color: #94a3b8;">
                            ${this.languageManager.translate('search_try_other')}
                        </div>
                    </div>
                `;
            }
            return;
        }

        // Render results
        const html = results.map((item, index) => {
            const icon = this.getCategoryIcon(item.category);
            const highlightedTitle = this.searchManager.highlight(item.title, this.currentQuery);
            const highlightedContent = this.searchManager.highlight(item.content, this.currentQuery);

            return `
                <div class="search-result-item ${index === 0 ? 'selected' : ''}"
                     data-link="${item.link}"
                     data-index="${index}">
                    <div class="result-icon">${icon}</div>
                    <div class="result-content">
                        <div class="result-title">${highlightedTitle}</div>
                        <div class="result-content-text">${highlightedContent}</div>
                        <div class="result-meta">
                            <span class="result-category">${this.getCategoryLabel(item.category)}</span>
                            <span class="result-score">score: ${item.score}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        // Add category stats
        const stats = this.searchManager.getCategoryStats(results);
        const statsHtml = Object.entries(stats).map(([category, count]) => `
            <span class="stat-badge">
                ${this.getCategoryLabel(category)}<span class="count">${count}</span>
            </span>
        `).join('');

        container.innerHTML = html + `<div class="search-stats">${statsHtml}</div>`;

        // Bind click events
        container.querySelectorAll('.search-result-item').forEach(item => {
            item.addEventListener('click', () => {
                const link = item.getAttribute('data-link');
                this.handleResultClick(link!);
            });
        });
    }

    /**
     * Get category icon
     */
    private getCategoryIcon(category: string): string {
        const icons: Record<string, string> = {
            feature: '⚡',
            code: '💻',
            performance: '📊',
            architecture: '🏗️',
            version: '📦',
            about: 'ℹ️'
        };
        return icons[category] || '🔍';
    }

    /**
     * Get category label
     */
    private getCategoryLabel(category: string): string {
        const lang = this.languageManager.getCurrentLanguage();
        const labels: Record<string, { zh: string; en: string }> = {
            feature: { zh: '特性', en: 'Feature' },
            code: { zh: '代码', en: 'Code' },
            performance: { zh: '性能', en: 'Performance' },
            architecture: { zh: '架构', en: 'Architecture' },
            version: { zh: '版本', en: 'Version' },
            about: { zh: '关于', en: 'About' }
        };
        return labels[category]?.[lang] || category;
    }

    /**
     * Handle result click
     */
    private handleResultClick(link: string): void {
        if (link.startsWith('http')) {
            window.open(link, '_blank');
        } else if (link.startsWith('#')) {
            const element = document.querySelector(link);
            if (element) {
                element.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }
        this.closeSearch();
    }

    /**
     * Update UI for language change
     */
    updateLanguageUI(): void {
        const triggerBtn = document.getElementById('searchTriggerBtn');
        if (triggerBtn) {
            triggerBtn.title = this.languageManager.translate('search_hint');
        }

        const input = document.getElementById('searchInput');
        if (input) {
            input.placeholder = this.languageManager.translate('search_placeholder');
        }

        const hint = document.querySelector('.search-hint');
        if (hint) {
            hint.textContent = this.languageManager.translate('search_hint');
        }

        // Re-render current results if any
        if (this.currentQuery.length > 0) {
            this.renderResults(this.searchResults);
        }
    }
}
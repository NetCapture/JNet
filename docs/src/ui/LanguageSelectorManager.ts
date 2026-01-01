/**
 * JNet GitHub Pages - Language Selector UI Manager
 * Handles language selection interface and interactions
 */

import {
    LanguageSelectorManager as ILanguageSelectorManager,
    LanguageInfo
} from '../types/index.js';
import { LanguageManager } from '../managers/LanguageManager.js';
import { ToastManager } from '../managers/ToastManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';

/**
 * LanguageSelectorManager - Manages language selection UI
 */
export class LanguageSelectorManager implements ILanguageSelectorManager {
    private languageManager: LanguageManager;
    private toastManager: ToastManager;
    private validationManager: ValidationManager;
    private isOpen: boolean;

    constructor(languageManager: LanguageManager) {
        this.languageManager = languageManager;
        this.toastManager = ToastManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
        this.isOpen = false;
    }

    /**
     * Initialize language selector
     */
    async initialize(): Promise<void> {
        this.createLanguageSelector();
        this.bindEvents();
        console.log('🌐 Language selector initialized');
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        this.removeLanguageSelector();
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'LanguageSelectorManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Create language selector UI
     */
    private createLanguageSelector(): void {
        if (typeof document === 'undefined') return;

        // Language switcher container
        const switcher = document.createElement('div');
        switcher.className = 'lang-switcher';
        switcher.innerHTML = `
            <div class="lang-dropdown-btn" id="langDropdownBtn">
                <span class="flag" id="currentFlag">🇨🇳</span>
                <span id="currentLangName">中文</span>
                <span class="arrow">▼</span>
            </div>
            <div class="lang-dropdown-menu" id="langDropdownMenu">
                <div class="lang-search">
                    <input type="text" id="langSearchInput" placeholder="搜索语言...">
                </div>
                <div class="lang-list" id="langList">
                    <!-- Dynamic language list -->
                </div>
            </div>
        `;
        document.body.appendChild(switcher);

        // Overlay
        const overlay = document.createElement('div');
        overlay.className = 'lang-overlay';
        overlay.id = 'langOverlay';
        document.body.appendChild(overlay);

        // Add styles
        this.addLanguageSelectorStyles();

        // Update initial display
        this.updateDisplay(this.languageManager.getCurrentLanguage());
    }

    /**
     * Remove language selector UI
     */
    private removeLanguageSelector(): void {
        const switcher = document.querySelector('.lang-switcher');
        const overlay = document.getElementById('langOverlay');
        const styles = document.getElementById('lang-selector-styles');

        if (switcher) switcher.remove();
        if (overlay) overlay.remove();
        if (styles) styles.remove();
    }

    /**
     * Add language selector styles
     */
    private addLanguageSelectorStyles(): void {
        if (document.getElementById('lang-selector-styles')) return;

        const style = document.createElement('style');
        style.id = 'lang-selector-styles';
        style.textContent = `
            .lang-switcher {
                position: fixed;
                top: 20px;
                left: 20px;
                z-index: 1001;
            }

            .lang-dropdown-btn {
                background: rgba(255, 255, 255, 0.15);
                backdrop-filter: blur(10px);
                border: 1px solid rgba(255, 255, 255, 0.2);
                border-radius: 20px;
                padding: 8px 16px;
                cursor: pointer;
                font-weight: 600;
                font-size: 0.85rem;
                transition: all 0.3s ease;
                color: white;
                display: flex;
                align-items: center;
                gap: 6px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
            }

            .lang-dropdown-btn:hover {
                background: rgba(255, 255, 255, 0.25);
                transform: translateY(-2px);
            }

            .lang-dropdown-btn .flag {
                font-size: 1.1rem;
            }

            .lang-dropdown-btn .arrow {
                font-size: 0.7rem;
                transition: transform 0.3s ease;
            }

            .lang-dropdown-btn.active .arrow {
                transform: rotate(180deg);
            }

            .lang-dropdown-menu {
                position: absolute;
                top: 100%;
                left: 0;
                margin-top: 8px;
                background: rgba(30, 41, 59, 0.95);
                backdrop-filter: blur(10px);
                border: 1px solid rgba(255, 255, 255, 0.1);
                border-radius: 16px;
                box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
                min-width: 280px;
                max-height: 400px;
                overflow-y: auto;
                opacity: 0;
                transform: translateY(-10px);
                pointer-events: none;
                transition: all 0.3s ease;
                z-index: 1002;
            }

            .lang-dropdown-menu.show {
                opacity: 1;
                transform: translateY(0);
                pointer-events: all;
            }

            .lang-search {
                position: sticky;
                top: 0;
                background: rgba(30, 41, 59, 0.95);
                padding: 12px;
                border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                z-index: 1;
            }

            .lang-search input {
                width: 100%;
                padding: 8px 12px;
                border: 1px solid rgba(255, 255, 255, 0.2);
                border-radius: 8px;
                font-size: 0.9rem;
                outline: none;
                background: rgba(255, 255, 255, 0.1);
                color: white;
            }

            .lang-search input::placeholder {
                color: rgba(255, 255, 255, 0.5);
            }

            .lang-search input:focus {
                border-color: rgba(255, 255, 255, 0.4);
                background: rgba(255, 255, 255, 0.15);
            }

            .lang-list {
                padding: 8px;
            }

            .lang-item {
                display: flex;
                align-items: center;
                gap: 12px;
                padding: 10px 12px;
                border-radius: 8px;
                cursor: pointer;
                transition: all 0.2s ease;
                border: 1px solid transparent;
                color: white;
            }

            .lang-item:hover {
                background: rgba(255, 255, 255, 0.1);
                border-color: rgba(255, 255, 255, 0.2);
                transform: translateX(2px);
            }

            .lang-item.active {
                background: rgba(59, 130, 246, 0.3);
                border-color: rgba(59, 130, 246, 0.5);
                font-weight: 600;
            }

            .lang-item .flag {
                font-size: 1.3rem;
                min-width: 24px;
                text-align: center;
            }

            .lang-item .lang-info {
                flex: 1;
                display: flex;
                flex-direction: column;
                line-height: 1.2;
            }

            .lang-item .lang-name {
                font-size: 0.9rem;
                font-weight: 500;
            }

            .lang-item .lang-native {
                font-size: 0.75rem;
                color: rgba(255, 255, 255, 0.6);
            }

            .lang-item.active .lang-native {
                color: rgba(59, 130, 246, 0.8);
            }

            .lang-group-label {
                padding: 8px 12px;
                font-size: 0.75rem;
                font-weight: 700;
                color: rgba(255, 255, 255, 0.5);
                text-transform: uppercase;
                letter-spacing: 0.5px;
                background: rgba(255, 255, 255, 0.05);
                border-radius: 6px;
                margin: 4px 0;
            }

            .lang-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.1);
                opacity: 0;
                pointer-events: none;
                transition: opacity 0.3s ease;
                z-index: 1000;
            }

            .lang-overlay.show {
                opacity: 1;
                pointer-events: all;
            }

            @media (max-width: 768px) {
                .lang-switcher {
                    top: 10px;
                    left: 10px;
                }

                .lang-dropdown-btn {
                    padding: 6px 12px;
                    font-size: 0.75rem;
                }

                .lang-dropdown-menu {
                    min-width: 240px;
                    max-height: 60vh;
                    left: 0;
                    right: 0;
                    margin: 8px;
                }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Bind events
     */
    private bindEvents(): void {
        const dropdownBtn = document.getElementById('langDropdownBtn');
        const dropdownMenu = document.getElementById('langDropdownMenu');
        const overlay = document.getElementById('langOverlay');
        const searchInput = document.getElementById('langSearchInput');

        if (dropdownBtn) {
            dropdownBtn.addEventListener('click', () => {
                if (this.isOpen) {
                    this.closeDropdown();
                } else {
                    this.openDropdown();
                }
            });
        }

        if (overlay) {
            overlay.addEventListener('click', () => this.closeDropdown());
        }

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.renderLanguageList((e.target as HTMLInputElement).value);
            });
        }

        // ESC key to close
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isOpen) {
                this.closeDropdown();
            }
        });
    }

    /**
     * Open language dropdown
     */
    openDropdown(): void {
        this.isOpen = true;
        const dropdownBtn = document.getElementById('langDropdownBtn');
        const dropdownMenu = document.getElementById('langDropdownMenu');
        const overlay = document.getElementById('langOverlay');
        const searchInput = document.getElementById('langSearchInput');

        if (dropdownBtn) dropdownBtn.classList.add('active');
        if (dropdownMenu) dropdownMenu.classList.add('show');
        if (overlay) overlay.classList.add('show');

        this.renderLanguageList();

        if (searchInput) {
            setTimeout(() => searchInput.focus(), 100);
        }
    }

    /**
     * Close language dropdown
     */
    closeDropdown(): void {
        this.isOpen = false;
        const dropdownBtn = document.getElementById('langDropdownBtn');
        const dropdownMenu = document.getElementById('langDropdownMenu');
        const overlay = document.getElementById('langOverlay');
        const searchInput = document.getElementById('langSearchInput');

        if (dropdownBtn) dropdownBtn.classList.remove('active');
        if (dropdownMenu) dropdownMenu.classList.remove('show');
        if (overlay) overlay.classList.remove('show');

        if (searchInput) searchInput.value = '';
    }

    /**
     * Update display with current language
     */
    updateDisplay(lang: string): void {
        const langInfo = this.languageManager.getLanguageInfo(lang);
        if (!langInfo) return;

        const flagEl = document.getElementById('currentFlag');
        const nameEl = document.getElementById('currentLangName');

        if (flagEl) flagEl.textContent = langInfo.flag;
        if (nameEl) nameEl.textContent = langInfo.native;
    }

    /**
     * Render language list
     */
    renderLanguageList(filter: string = ''): void {
        const langList = document.getElementById('langList');
        if (!langList) return;

        // Language groups
        const groups: Record<string, string[]> = {
            '亚洲语言': ['zh', 'ja', 'ko', 'th', 'vi', 'id', 'ms', 'fil'],
            '欧洲语言': ['en', 'es', 'pt', 'fr', 'de', 'it', 'ru', 'uk', 'pl', 'nl', 'tr', 'cs', 'sv', 'no', 'da', 'fi', 'el', 'hu', 'ro'],
            '中东语言': ['ar', 'he'],
            '印度语言': ['hi', 'bn', 'ta', 'te'],
            '其他': ['fa', 'sw']
        };

        const allLangs = this.languageManager.getSupportedLanguages();
        let html = '';

        for (const [groupName, langCodes] of Object.entries(groups)) {
            const filteredLangs = langCodes.filter(code => {
                if (!allLangs[code]) return false;
                if (!filter) return true;

                const lang = allLangs[code];
                const searchStr = `${lang.name} ${lang.native} ${code}`.toLowerCase();
                return searchStr.includes(filter.toLowerCase());
            });

            if (filteredLangs.length === 0) continue;

            html += `<div class="lang-group-label">${groupName}</div>`;

            filteredLangs.forEach(code => {
                const lang = allLangs[code];
                const isActive = this.languageManager.getCurrentLanguage() === code;

                html += `
                    <div class="lang-item ${isActive ? 'active' : ''}" data-lang="${code}">
                        <span class="flag">${lang.flag}</span>
                        <div class="lang-info">
                            <span class="lang-name">${lang.name}</span>
                            <span class="lang-native">${lang.native}</span>
                        </div>
                    </div>
                `;
            });
        }

        langList.innerHTML = html || '<div style="padding: 20px; text-align: center; color: #999;">未找到匹配的语言</div>';

        // Bind click events
        langList.querySelectorAll('.lang-item').forEach(item => {
            item.addEventListener('click', () => {
                const lang = item.getAttribute('data-lang');
                if (lang) {
                    this.changeLanguage(lang);
                }
            });
        });
    }

    /**
     * Change language
     */
    private changeLanguage(lang: string): void {
        const success = this.languageManager.setLanguage(lang);

        if (success) {
            this.updateDisplay(lang);
            this.closeDropdown();

            // Show success toast
            const langInfo = this.languageManager.getLanguageInfo(lang);
            this.toastManager.success(
                'Language',
                `Switched to ${langInfo?.native || lang}`
            );

            // Update UI elements
            this.updateLanguageUI();
        }
    }

    /**
     * Update language-dependent UI
     */
    private updateLanguageUI(): void {
        // Update all elements with data-i18n attribute
        const elements = document.querySelectorAll('[data-i18n]');
        elements.forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (key) {
                const translated = this.languageManager.translate(key);
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                    (el as HTMLInputElement).placeholder = translated;
                } else {
                    el.textContent = translated;
                }
            }
        });

        // Update search UI if it exists
        const searchTrigger = document.getElementById('searchTriggerBtn');
        if (searchTrigger) {
            searchTrigger.title = this.languageManager.translate('search_hint');
        }
    }

    /**
     * Get current language info
     */
    getCurrentLanguageInfo(): LanguageInfo | null {
        return this.languageManager.getLanguageInfo(this.languageManager.getCurrentLanguage());
    }
}
/**
 * JNet GitHub Pages - Main UI Manager
 * Coordinates all UI updates and interactions
 */

import {
    UIManager as IUIManager,
    GitHubRepositoryStats,
    RealDataResponse
} from '../types/index.js';
import { LanguageManager } from '../managers/LanguageManager.js';
import { ToastManager } from '../managers/ToastManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';

/**
 * UIManager - Main UI coordinator
 */
export class UIManager implements IUIManager {
    private languageManager: LanguageManager;
    private toastManager: ToastManager;
    private configManager: ConfigManager;
    private validationManager: ValidationManager;

    constructor() {
        this.languageManager = LanguageManager.getInstance();
        this.toastManager = ToastManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
    }

    /**
     * Initialize UI manager
     */
    async initialize(): Promise<void> {
        console.log('✅ UIManager initialized');
        this.setupIntersectionObserver();
        this.setupSmoothScrolling();
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        // Clean up any event listeners or observers
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'UIManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Initialize UI components
     */
    initializeUI(): void {
        this.setupFadeInAnimations();
        this.setupMobileMenu();
        this.setupSmoothScrolling();
    }

    /**
     * Update language UI
     */
    updateLanguageUI(lang: string): void {
        // Update all translatable elements
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

        // Update page title if available
        const titleElement = document.querySelector('title[data-i18n]');
        if (titleElement) {
            const key = titleElement.getAttribute('data-i18n');
            if (key) {
                titleElement.textContent = this.languageManager.translate(key);
            }
        }

        // Update meta descriptions
        const metaDesc = document.querySelector('meta[name="description"]');
        if (metaDesc) {
            const descKey = metaDesc.getAttribute('data-i18n');
            if (descKey) {
                metaDesc.setAttribute('content', this.languageManager.translate(descKey));
            }
        }
    }

    /**
     * Update GitHub stats UI
     */
    updateStatsUI(stats: GitHubRepositoryStats): void {
        if (!stats) return;

        // Update stars
        const starsEl = document.getElementById('starsCount');
        if (starsEl) {
            this.animateCounter(starsEl, stats.stargazers_count);
        }

        // Update forks
        const forksEl = document.getElementById('forksCount');
        if (forksEl) {
            this.animateCounter(forksEl, stats.forks_count);
        }

        // Update issues
        const issuesEl = document.getElementById('issuesCount');
        if (issuesEl) {
            this.animateCounter(issuesEl, stats.open_issues_count);
        }

        // Update contributors (hardcoded for now)
        const contributorsEl = document.getElementById('contributorsCount');
        if (contributorsEl) {
            contributorsEl.textContent = '3';
        }

        // Update hero stats if they exist
        this.updateHeroStats(stats);
    }

    /**
     * Update real data UI
     */
    updateRealDataUI(data: RealDataResponse): void {
        if (!data) return;

        // Update testimonials
        this.renderTestimonials(data.architects);

        // Update case studies
        this.renderCaseStudies(data.companies);
    }

    /**
     * Show loading state
     */
    showLoading(element: HTMLElement): void {
        if (!element) return;

        element.style.opacity = '0.5';
        element.style.pointerEvents = 'none';

        // Add loading spinner
        const spinner = document.createElement('div');
        spinner.className = 'loading-spinner';
        spinner.innerHTML = '⏳';
        spinner.style.cssText = `
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            font-size: 2rem;
            animation: spin 1s linear infinite;
        `;

        element.style.position = 'relative';
        element.appendChild(spinner);
    }

    /**
     * Hide loading state
     */
    hideLoading(element: HTMLElement): void {
        if (!element) return;

        element.style.opacity = '';
        element.style.pointerEvents = '';
        element.style.position = '';

        const spinner = element.querySelector('.loading-spinner');
        if (spinner) {
            spinner.remove();
        }
    }

    /**
     * Scroll to section
     */
    scrollToSection(sectionId: string): void {
        const element = document.querySelector(sectionId);
        if (element) {
            element.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    }

    /**
     * Animate counter
     */
    private animateCounter(element: HTMLElement, target: number): void {
        if (!element) return;

        const duration = 1000;
        const steps = 50;
        const increment = target / steps;
        let current = 0;

        const timer = setInterval(() => {
            current += increment;
            if (current >= target) {
                element.textContent = this.formatNumber(target);
                clearInterval(timer);
            } else {
                element.textContent = this.formatNumber(Math.floor(current));
            }
        }, duration / steps);
    }

    /**
     * Format number with K/M suffix
     */
    private formatNumber(num: number): string {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        }
        if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    }

    /**
     * Update hero stats
     */
    private updateHeroStats(stats: GitHubRepositoryStats): void {
        // Update any hero-specific stats
        const heroStats = document.querySelectorAll('.hero-stat-value');
        heroStats.forEach((el, index) => {
            if (index === 0) {
                // Lines of code (hardcoded)
                el.textContent = '500+';
            } else if (index === 1) {
                // Dependencies
                el.textContent = '0';
            } else if (index === 2) {
                // Memory savings
                el.textContent = '60%';
            }
        });
    }

    /**
     * Render testimonials
     */
    private renderTestimonials(architects: any[]): void {
        const container = document.getElementById('testimonialGrid');
        if (!container || !architects) return;

        const html = architects.map(person => `
            <div class="testimonial-card">
                <div class="testimonial-quote">"</div>
                <div class="testimonial-text">${this.validationManager.sanitizeHTML(person.comment)}</div>
                <div class="testimonial-author">
                    <div class="author-avatar">${this.validationManager.sanitizeHTML(person.avatar)}</div>
                    <div class="author-info">
                        <div class="author-name">${this.validationManager.sanitizeHTML(person.name)}</div>
                        <div class="author-role">${this.validationManager.sanitizeHTML(person.role)} @ ${this.validationManager.sanitizeHTML(person.company)}</div>
                    </div>
                    <div class="stars">${'★'.repeat(person.stars)}</div>
                </div>
            </div>
        `).join('');

        container.innerHTML = html;
    }

    /**
     * Render case studies
     */
    private renderCaseStudies(companies: any[]): void {
        const container = document.getElementById('caseStudiesContainer');
        if (!container || !companies) return;

        const html = companies.map((company, index) => `
            <div class="case-study">
                <div class="case-content">
                    <h3>${this.validationManager.sanitizeHTML(company.name)} - ${this.validationManager.sanitizeHTML(company.use_case)}</h3>
                    <p>${this.validationManager.sanitizeHTML(company.industry)}领域的领军企业，使用 JNet 作为核心 HTTP 客户端。</p>
                    <p>规模：${this.validationManager.sanitizeHTML(company.scale)}员工 | 零依赖部署，性能卓越。</p>
                    <div class="case-metrics">
                        <div class="metric">
                            <div class="metric-value">${this.validationManager.sanitizeHTML(company.metrics.requests)}</div>
                            <div class="metric-label">日请求量</div>
                        </div>
                        <div class="metric">
                            <div class="metric-value">${this.validationManager.sanitizeHTML(company.metrics.latency)}</div>
                            <div class="metric-label">平均延迟</div>
                        </div>
                        <div class="metric">
                            <div class="metric-value">${this.validationManager.sanitizeHTML(company.metrics.availability)}</div>
                            <div class="metric-label">可用性</div>
                        </div>
                    </div>
                </div>
                <div class="case-visual">
                    <div class="case-visual-icon">${this.getIndustryIcon(company.industry)}</div>
                    <div class="case-visual-text">${this.validationManager.sanitizeHTML(company.industry.split('/')[0])}</div>
                </div>
            </div>
        `).join('');

        container.innerHTML = html;
    }

    /**
     * Get industry icon
     */
    private getIndustryIcon(industry: string): string {
        if (industry.includes('互联网')) return '🌐';
        if (industry.includes('社交')) return '💬';
        if (industry.includes('云计算')) return '☁️';
        if (industry.includes('生活服务')) return '🎯';
        if (industry.includes('搜索')) return '🔍';
        if (industry.includes('电商')) return '🛒';
        return '🏢';
    }

    /**
     * Setup fade-in animations
     */
    private setupFadeInAnimations(): void {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('fade-in-visible');
                }
            });
        }, { threshold: 0.1 });

        document.querySelectorAll('.fade-in').forEach(el => {
            observer.observe(el);
        });

        // Add CSS for fade-in
        const style = document.createElement('style');
        style.textContent = `
            .fade-in {
                opacity: 0;
                transform: translateY(30px);
                transition: opacity 0.6s ease, transform 0.6s ease;
            }

            .fade-in-visible {
                opacity: 1 !important;
                transform: translateY(0) !important;
            }

            .delay-1 { transition-delay: 0.1s; }
            .delay-2 { transition-delay: 0.2s; }
            .delay-3 { transition-delay: 0.3s; }
            .delay-4 { transition-delay: 0.4s; }
            .delay-5 { transition-delay: 0.5s; }
        `;
        document.head.appendChild(style);
    }

    /**
     * Setup intersection observer for animations
     */
    private setupIntersectionObserver(): void {
        if (!('IntersectionObserver' in window)) return;

        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('in-view');
                }
            });
        }, {
            threshold: 0.1,
            rootMargin: '0px 0px -50px 0px'
        });

        // Observe elements with animation classes
        const animatedElements = document.querySelectorAll('.feature-card, .perf-card, .testimonial-card, .case-study');
        animatedElements.forEach(el => observer.observe(el));

        // Add CSS for in-view animations
        const style = document.createElement('style');
        style.textContent = `
            .feature-card.in-view,
            .perf-card.in-view,
            .testimonial-card.in-view,
            .case-study.in-view {
                animation: slideUp 0.6s ease forwards;
            }

            @keyframes slideUp {
                from {
                    opacity: 0;
                    transform: translateY(20px);
                }
                to {
                    opacity: 1;
                    transform: translateY(0);
                }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Setup smooth scrolling
     */
    private setupSmoothScrolling(): void {
        // Handle all anchor links
        document.addEventListener('click', (e) => {
            const target = e.target as HTMLElement;
            const link = target.closest('a[href^="#"]') as HTMLAnchorElement;

            if (link && link.hash) {
                e.preventDefault();
                const element = document.querySelector(link.hash);
                if (element) {
                    element.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });
                }
            }
        });
    }

    /**
     * Setup mobile menu
     */
    private setupMobileMenu(): void {
        // Add mobile menu toggle if needed
        const nav = document.querySelector('.nav-links');
        if (!nav) return;

        // Check if we need mobile menu
        const checkMobile = () => {
            if (window.innerWidth <= 768) {
                // Could add mobile menu toggle here
            }
        };

        window.addEventListener('resize', checkMobile);
        checkMobile();
    }

    /**
     * Show error state
     */
    showErrorState(message: string, containerId?: string): void {
        const container = containerId ? document.getElementById(containerId) : document.body;
        if (!container) return;

        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-state';
        errorDiv.innerHTML = `
            <div class="error-icon">⚠️</div>
            <div class="error-message">${this.validationManager.sanitizeHTML(message)}</div>
            <button class="retry-btn" onclick="location.reload()">重试</button>
        `;

        // Add styles
        const style = document.createElement('style');
        style.textContent = `
            .error-state {
                text-align: center;
                padding: 60px 20px;
                color: #ef4444;
            }

            .error-icon {
                font-size: 3rem;
                margin-bottom: 16px;
            }

            .error-message {
                font-size: 1.1rem;
                margin-bottom: 24px;
                color: #374151;
            }

            .retry-btn {
                background: #ef4444;
                color: white;
                border: none;
                padding: 12px 24px;
                border-radius: 8px;
                cursor: pointer;
                font-weight: 600;
                transition: background 0.2s;
            }

            .retry-btn:hover {
                background: #dc2626;
            }
        `;
        document.head.appendChild(style);

        if (container) {
            container.innerHTML = '';
            container.appendChild(errorDiv);
        }
    }

    /**
     * Show success state
     */
    showSuccessState(message: string, containerId?: string): void {
        const container = containerId ? document.getElementById(containerId) : document.body;
        if (!container) return;

        const successDiv = document.createElement('div');
        successDiv.className = 'success-state';
        successDiv.innerHTML = `
            <div class="success-icon">✅</div>
            <div class="success-message">${this.validationManager.sanitizeHTML(message)}</div>
        `;

        // Add styles
        const style = document.createElement('style');
        style.textContent = `
            .success-state {
                text-align: center;
                padding: 40px 20px;
                color: #10b981;
            }

            .success-icon {
                font-size: 3rem;
                margin-bottom: 16px;
            }

            .success-message {
                font-size: 1.1rem;
                color: #374151;
            }
        `;
        document.head.appendChild(style);

        if (container) {
            container.innerHTML = '';
            container.appendChild(successDiv);
        }
    }

    /**
     * Update loading state
     */
    updateLoadingState(element: HTMLElement, loading: boolean, message?: string): void {
        if (!element) return;

        if (loading) {
            element.style.opacity = '0.6';
            element.style.pointerEvents = 'none';

            // Add loading overlay
            const overlay = document.createElement('div');
            overlay.className = 'loading-overlay';
            overlay.innerHTML = `
                <div class="loading-content">
                    <div class="loading-spinner">⏳</div>
                    ${message ? `<div class="loading-text">${this.validationManager.sanitizeHTML(message)}</div>` : ''}
                </div>
            `;
            overlay.style.cssText = `
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(255, 255, 255, 0.8);
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: inherit;
            `;

            element.style.position = 'relative';
            element.appendChild(overlay);
        } else {
            element.style.opacity = '';
            element.style.pointerEvents = '';
            element.style.position = '';

            const overlay = element.querySelector('.loading-overlay');
            if (overlay) {
                overlay.remove();
            }
        }
    }

    /**
     * Add loading styles
     */
    private addLoadingStyles(): void {
        if (document.getElementById('loading-styles')) return;

        const style = document.createElement('style');
        style.id = 'loading-styles';
        style.textContent = `
            .loading-content {
                text-align: center;
            }

            .loading-spinner {
                font-size: 2rem;
                animation: spin 1s linear infinite;
                margin-bottom: 8px;
            }

            .loading-text {
                font-size: 0.9rem;
                color: #64748b;
                font-weight: 500;
            }

            @keyframes spin {
                from { transform: rotate(0deg); }
                to { transform: rotate(360deg); }
            }

            @media (prefers-reduced-motion: reduce) {
                .loading-spinner {
                    animation: none;
                }
            }
        `;
        document.head.appendChild(style);
    }
}
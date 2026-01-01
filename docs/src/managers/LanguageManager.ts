/**
 * JNet GitHub Pages - Internationalization Manager
 * Enhanced language management with dynamic translation support
 */

import {
    ILanguageManager,
    LanguageInfo,
    LanguageTranslations,
    TranslationDictionary,
    LanguageChangeEvent,
    EventListener
} from '../types/index.js';
import { ErrorManager } from '../errors/ErrorManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';

/**
 * LanguageManager - Handles all internationalization
 */
export class LanguageManager implements ILanguageManager {
    private static instance: LanguageManager | null = null;
    private errorManager: ErrorManager;
    private validationManager: ValidationManager;
    private currentLanguage: string;
    private translations: LanguageTranslations;
    private listeners: EventListener<LanguageChangeEvent>[];
    private supportedLanguages: Record<string, LanguageInfo>;

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
        this.currentLanguage = 'zh';
        this.translations = {};
        this.listeners = [];
        this.supportedLanguages = {};
    }

    /**
     * Get singleton instance
     */
    static getInstance(): LanguageManager {
        if (!LanguageManager.instance) {
            LanguageManager.instance = new LanguageManager();
        }
        return LanguageManager.instance;
    }

    /**
     * Initialize language manager
     */
    async initialize(): Promise<void> {
        console.log('✅ LanguageManager initialized');
        this.initializeSupportedLanguages();
        this.initializeTranslations();
        this.loadFromLocalStorage();
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        this.listeners = [];
        LanguageManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'LanguageManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Initialize supported languages
     */
    private initializeSupportedLanguages(): void {
        this.supportedLanguages = {
            zh: { code: 'zh', name: 'Chinese', native: '中文', flag: '🇨🇳' },
            en: { code: 'en', name: 'English', native: 'English', flag: '🇺🇸' },
            ja: { code: 'ja', name: 'Japanese', native: '日本語', flag: '🇯🇵' },
            ko: { code: 'ko', name: 'Korean', native: '한국어', flag: '🇰🇷' },
            es: { code: 'es', name: 'Spanish', native: 'Español', flag: '🇪🇸' },
            fr: { code: 'fr', name: 'French', native: 'Français', flag: '🇫🇷' },
            de: { code: 'de', name: 'German', native: 'Deutsch', flag: '🇩🇪' },
            it: { code: 'it', name: 'Italian', native: 'Italiano', flag: '🇮🇹' },
            pt: { code: 'pt', name: 'Portuguese', native: 'Português', flag: '🇵🇹' },
            ru: { code: 'ru', name: 'Russian', native: 'Русский', flag: '🇷🇺' },
            ar: { code: 'ar', name: 'Arabic', native: 'العربية', flag: '🇸🇦' },
            hi: { code: 'hi', name: 'Hindi', native: 'हिन्दी', flag: '🇮🇳' }
        };
    }

    /**
     * Initialize translations
     */
    private initializeTranslations(): void {
        this.translations = {
            zh: {
                nav_home: '首页',
                nav_discuss: '讨论区',
                nav_showcase: '产品展示',
                nav_profile: '个人中心',
                showcase_title: 'JNet - 极致性能的 HTTP 客户端',
                showcase_subtitle: '基于 JDK 11+ 原生 HttpClient，零依赖，高性能，API 设计参考 Python requests',
                stats_lines: '核心代码行',
                row_deps: '第三方依赖',
                row_memory: '内存节省',
                showcase_features_title: '核心优势',
                showcase_why: '为什么选择 JNet？',
                feature_1_title: '极致性能',
                feature_1_desc: '基于 JDK 11+ 原生 HttpClient，复用连接池，内存占用比 OkHttp 低 60-70%',
                badge_performance: '性能优化',
                feature_2_title: 'Python 风格 API',
                feature_2_desc: '直观简洁的静态方法调用，一行代码完成 HTTP 请求，学习成本极低',
                badge_usability: '易用性',
                feature_3_title: '线程安全',
                feature_3_desc: '不可变对象设计，无锁化架构，完美支持高并发场景',
                badge_reliability: '可靠性',
                feature_4_title: '拦截器链',
                feature_4_desc: '类 OkHttp 拦截器机制，支持认证、日志、重试等扩展',
                badge_extensibility: '可扩展',
                feature_5_title: 'SSE 流式支持',
                feature_5_desc: '真正的非阻塞 Server-Sent Events，实时数据推送',
                badge_realtime: '实时性',
                feature_6_title: '零依赖',
                feature_6_desc: '仅使用 JDK 标准库，无任何第三方 JAR 包，部署无忧',
                badge_lightweight: '纯净',
                showcase_performance_title: '性能对比',
                perf_table_header: '实测数据，真实可信',
                showcase_architecture_title: '架构设计',
                arch_desc_2: '清晰的分层架构，多种设计模式应用',
                arch_builder: '建造者模式',
                arch_builder_desc: 'JNetClient 使用建造者模式构建配置，灵活且类型安全',
                arch_singleton: '单例模式',
                arch_singleton_desc: '全局唯一客户端实例，资源高效利用',
                arch_chain: '责任链模式',
                arch_chain_desc: '拦截器链实现请求/响应的链式处理',
                arch_strategy: '策略模式',
                arch_strategy_desc: '多种策略实现可扩展的请求处理',
                arch_immutable: '不可变对象',
                arch_immutable_desc: 'Request/Response 对象不可变，线程安全',
                arch_template: '模板方法',
                arch_template_desc: '定义算法骨架，子类实现具体步骤',
                showcase_testimonials_title: '用户评价',
                testimonial_subtitle: '来自一线互联网公司架构师的真实反馈',
                showcase_cases_title: '使用案例',
                case_subtitle: '一线互联网公司的实际应用',
                showcase_stats_title: '项目数据',
                stats_subtitle: 'JNet 的成长历程',
                stats_stars: 'GitHub Stars',
                stats_forks: 'Forks',
                stats_issues: 'Issues',
                stats_contributors: '贡献者',
                showcase_cta_title: '准备好开始使用了吗？',
                showcase_cta_desc: '只需一行代码，即可享受极简、高性能的 HTTP 客户端',
                search_hint: '搜索功能、代码示例、性能对比...',
                search_placeholder: '输入关键词搜索...',
                search_no_results: '未找到相关结果',
                search_try_other: '尝试使用其他关键词或查看分类'
            },
            en: {
                nav_home: 'Home',
                nav_discuss: 'Discuss',
                nav_showcase: 'Showcase',
                nav_profile: 'Profile',
                showcase_title: 'JNet - Ultimate Performance HTTP Client',
                showcase_subtitle: 'Based on JDK 11+ native HttpClient, zero dependencies, high performance, Python requests inspired API',
                stats_lines: 'Core Lines',
                row_deps: 'Dependencies',
                row_memory: 'Memory Saved',
                showcase_features_title: 'Core Advantages',
                showcase_why: 'Why Choose JNet?',
                feature_1_title: 'Ultimate Performance',
                feature_1_desc: 'Based on JDK 11+ native HttpClient, connection pool reuse, 60-70% lower memory than OkHttp',
                badge_performance: 'Performance',
                feature_2_title: 'Python-style API',
                feature_2_desc: 'Intuitive static method calls, one line HTTP requests, minimal learning curve',
                badge_usability: 'Usability',
                feature_3_title: 'Thread-Safe',
                feature_3_desc: 'Immutable object design, lock-free architecture, perfect for high concurrency',
                badge_reliability: 'Reliability',
                feature_4_title: 'Interceptor Chain',
                feature_4_desc: 'OkHttp-like interceptor mechanism, supports auth, logging, retry extensions',
                badge_extensibility: 'Extensibility',
                feature_5_title: 'SSE Streaming',
                feature_5_desc: 'True non-blocking Server-Sent Events, real-time data push',
                badge_realtime: 'Real-time',
                feature_6_title: 'Zero Dependencies',
                feature_6_desc: 'Only JDK standard library, no third-party JARs, deployment无忧',
                badge_lightweight: 'Lightweight',
                showcase_performance_title: 'Performance Comparison',
                perf_table_header: 'Measured Data, Real & Credible',
                showcase_architecture_title: 'Architecture Design',
                arch_desc_2: 'Clear layered architecture, multiple design patterns',
                arch_builder: 'Builder Pattern',
                arch_builder_desc: 'JNetClient uses Builder pattern for flexible, type-safe configuration',
                arch_singleton: 'Singleton Pattern',
                arch_singleton_desc: 'Global unique client instance, resource efficient',
                arch_chain: 'Chain of Responsibility',
                arch_chain_desc: 'Interceptor chain for request/response processing',
                arch_strategy: 'Strategy Pattern',
                arch_strategy_desc: 'Multiple strategies for extensible request handling',
                arch_immutable: 'Immutable Objects',
                arch_immutable_desc: 'Request/Response objects are immutable, thread-safe',
                arch_template: 'Template Method',
                arch_template_desc: 'Define algorithm skeleton, subclasses implement steps',
                showcase_testimonials_title: 'User Reviews',
                testimonial_subtitle: 'Real feedback from architects at top internet companies',
                showcase_cases_title: 'Use Cases',
                case_subtitle: 'Real applications from leading internet companies',
                showcase_stats_title: 'Project Stats',
                stats_subtitle: 'JNet Growth Journey',
                stats_stars: 'GitHub Stars',
                stats_forks: 'Forks',
                stats_issues: 'Issues',
                stats_contributors: 'Contributors',
                showcase_cta_title: 'Ready to Get Started?',
                showcase_cta_desc: 'Just one line of code to enjoy minimalist, high-performance HTTP client',
                search_hint: 'Search features, code examples, performance comparisons...',
                search_placeholder: 'Enter keywords to search...',
                search_no_results: 'No relevant results found',
                search_try_other: 'Try other keywords or check categories'
            }
        };
    }

    /**
     * Get current language
     */
    getCurrentLanguage(): string {
        return this.currentLanguage;
    }

    /**
     * Set current language
     */
    setLanguage(code: string): boolean {
        // Validate language code
        const validation = this.validationManager.validateInput(code, {
            minLength: 2,
            maxLength: 10,
            pattern: /^[a-z]{2,3}(-[A-Z]{2})?$/,
            sanitize: true
        });

        if (!validation.isValid) {
            this.errorManager.handleValidationError(
                'language',
                code,
                'language_code',
                validation.errors.join('; ')
            );
            return false;
        }

        if (!this.supportedLanguages[code]) {
            this.errorManager.handleError(
                this.errorManager.createError(
                    'VALIDATION_ERROR',
                    `Unsupported language: ${code}`,
                    { code, supported: Object.keys(this.supportedLanguages) }
                )
            );
            return false;
        }

        const oldLanguage = this.currentLanguage;
        this.currentLanguage = code;

        // Save to localStorage
        this.saveToLocalStorage();

        // Notify listeners
        this.emitLanguageChange({
            oldLanguage,
            newLanguage: code,
            timestamp: Date.now()
        });

        return true;
    }

    /**
     * Translate a key with optional parameters
     */
    translate(key: string, params?: Record<string, any>): string {
        // Validate key
        const keyValidation = this.validationManager.validateInput(key, {
            minLength: 1,
            maxLength: 200,
            sanitize: true
        });

        if (!keyValidation.isValid) {
            console.warn(`Invalid translation key: ${key}`);
            return key;
        }

        // Get translation
        const translation = this.getNestedTranslation(this.translations[this.currentLanguage], key);

        if (translation === undefined) {
            // Fallback to English
            const fallback = this.getNestedTranslation(this.translations['en'], key);

            if (fallback === undefined) {
                console.warn(`Translation not found: ${key} for language ${this.currentLanguage}`);
                return key;
            }

            return this.applyParameters(fallback, params);
        }

        return this.applyParameters(translation, params);
    }

    /**
     * Get all supported languages
     */
    getSupportedLanguages(): Record<string, LanguageInfo> {
        return { ...this.supportedLanguages };
    }

    /**
     * Subscribe to language changes
     */
    onLanguageChange(callback: (event: LanguageChangeEvent) => void): void {
        if (typeof callback === 'function') {
            this.listeners.push(callback);
        }
    }

    /**
     * Unsubscribe from language changes
     */
    offLanguageChange(callback: (event: LanguageChangeEvent) => void): void {
        this.listeners = this.listeners.filter(listener => listener !== callback);
    }

    /**
     * Translate multiple keys at once
     */
    translateBatch(keys: string[]): Record<string, string> {
        const result: Record<string, string> = {};

        for (const key of keys) {
            result[key] = this.translate(key);
        }

        return result;
    }

    /**
     * Check if a translation exists
     */
    hasTranslation(key: string): boolean {
        return this.getNestedTranslation(this.translations[this.currentLanguage], key) !== undefined;
    }

    /**
     * Add custom translations
     */
    addTranslations(language: string, translations: TranslationDictionary): void {
        if (!this.translations[language]) {
            this.translations[language] = {};
        }

        this.translations[language] = this.mergeDeep(this.translations[language], translations);
    }

    /**
     * Get language info
     */
    getLanguageInfo(code: string): LanguageInfo | null {
        return this.supportedLanguages[code] || null;
    }

    /**
     * Get current language info
     */
    getCurrentLanguageInfo(): LanguageInfo {
        return this.supportedLanguages[this.currentLanguage];
    }

    /**
     * Detect browser language
     */
    detectBrowserLanguage(): string {
        if (typeof window === 'undefined' || !window.navigator) {
            return 'zh';
        }

        const browserLang = window.navigator.language || (window.navigator as any).userLanguage;

        // Try full language code first (e.g., 'zh-CN')
        if (this.supportedLanguages[browserLang]) {
            return browserLang;
        }

        // Try base language code (e.g., 'zh')
        const baseLang = browserLang.split('-')[0];
        if (this.supportedLanguages[baseLang]) {
            return baseLang;
        }

        // Fallback to English
        return 'en';
    }

    /**
     * Auto-detect and set language
     */
    autoDetectLanguage(): boolean {
        const detected = this.detectBrowserLanguage();
        return this.setLanguage(detected);
    }

    /**
     * Get all available translations for current language
     */
    getAllTranslations(): TranslationDictionary {
        return this.translations[this.currentLanguage] || {};
    }

    /**
     * Format message with parameters (like i18n libraries)
     */
    formatMessage(message: string, params: Record<string, any>): string {
        return this.applyParameters(message, params);
    }

    // ==================== Private Helper Methods ====================

    private getNestedTranslation(dictionary: TranslationDictionary | undefined, key: string): any {
        if (!dictionary) return undefined;

        const keys = key.split('.');
        let current: any = dictionary;

        for (const k of keys) {
            if (current && typeof current === 'object' && k in current) {
                current = current[k];
            } else {
                return undefined;
            }
        }

        return current;
    }

    private applyParameters(text: string, params?: Record<string, any>): string {
        if (!params) return text;

        let result = text;

        for (const [key, value] of Object.entries(params)) {
            result = result.replace(new RegExp(`{${key}}`, 'g'), String(value));
        }

        return result;
    }

    private emitLanguageChange(event: LanguageChangeEvent): void {
        this.listeners.forEach(listener => {
            try {
                listener(event);
            } catch (error) {
                console.error('Language change listener error:', error);
            }
        });
    }

    private mergeDeep(target: TranslationDictionary, source: TranslationDictionary): TranslationDictionary {
        const result = { ...target };

        for (const key in source) {
            if (source[key] === undefined) continue;

            if (this.isObject(source[key]) && this.isObject(result[key])) {
                result[key] = this.mergeDeep(result[key] as TranslationDictionary, source[key] as TranslationDictionary);
            } else {
                result[key] = source[key];
            }
        }

        return result;
    }

    private isObject(value: any): boolean {
        return value && typeof value === 'object' && !Array.isArray(value);
    }

    private saveToLocalStorage(): void {
        if (typeof window !== 'undefined' && window.localStorage) {
            try {
                localStorage.setItem('jnet_language', this.currentLanguage);
            } catch (error) {
                console.warn('Failed to save language to localStorage:', error);
            }
        }
    }

    private loadFromLocalStorage(): void {
        if (typeof window !== 'undefined' && window.localStorage) {
            try {
                const saved = localStorage.getItem('jnet_language');
                if (saved && this.supportedLanguages[saved]) {
                    this.currentLanguage = saved;
                }
            } catch (error) {
                console.warn('Failed to load language from localStorage:', error);
            }
        }
    }
}

/**
 * Convenience function for translation
 */
export function t(key: string, params?: Record<string, any>): string {
    return LanguageManager.getInstance().translate(key, params);
}

/**
 * Set language convenience function
 */
export function setLanguage(code: string): boolean {
    return LanguageManager.getInstance().setLanguage(code);
}

/**
 * Get current language
 */
export function getCurrentLanguage(): string {
    return LanguageManager.getInstance().getCurrentLanguage();
}

/**
 * Translation hook for dynamic content
 */
export function translateElement(element: HTMLElement): void {
    const key = element.getAttribute('data-i18n');
    if (key) {
        const translated = LanguageManager.getInstance().translate(key);
        if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
            (element as HTMLInputElement).placeholder = translated;
        } else {
            element.textContent = translated;
        }
    }
}

/**
 * Batch translate all elements with data-i18n attribute
 */
export function translateAllElements(): void {
    const elements = document.querySelectorAll('[data-i18n]');
    elements.forEach(translateElement);
}
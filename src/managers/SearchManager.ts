/**
 * JNet GitHub Pages - Search Manager
 * Handles search functionality with indexing and scoring
 */

import {
    ISearchManager,
    SearchIndexItem,
    SearchResult,
    SearchStats,
    ErrorType
} from '../types/index.js';
import { ErrorManager } from '../errors/ErrorManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';

/**
 * SearchManager - Handles search operations
 */
export class SearchManager implements ISearchManager {
    private static instance: SearchManager | null = null;
    private errorManager: ErrorManager;
    private validationManager: ValidationManager;
    private configManager: ConfigManager;
    private index: SearchIndexItem[];

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.index = [];
    }

    /**
     * Get singleton instance
     */
    static getInstance(): SearchManager {
        if (!SearchManager.instance) {
            SearchManager.instance = new SearchManager();
        }
        return SearchManager.instance;
    }

    /**
     * Initialize search manager
     */
    async initialize(): Promise<void> {
        console.log('✅ SearchManager initialized');
        this.initializeSearchIndex();
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        this.index = [];
        SearchManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'SearchManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Initialize search index
     */
    private initializeSearchIndex(): void {
        this.index = [
            // Core Features
            {
                id: 'feature-performance',
                category: 'feature',
                title: '极致性能',
                titleEn: 'Ultimate Performance',
                content: '基于 JDK 11+ 原生 HttpClient，复用连接池，内存占用比 OkHttp 低 60-70%',
                contentEn: 'Based on JDK 11+ native HttpClient, 60-70% lower memory than OkHttp',
                keywords: ['性能', 'performance', '内存', 'memory', '优化', 'optimization', '原生', 'native'],
                link: '#features'
            },
            {
                id: 'feature-api',
                category: 'feature',
                title: 'Python 风格 API',
                titleEn: 'Python-style API',
                content: '直观简洁的静态方法调用，一行代码完成 HTTP 请求',
                contentEn: 'Intuitive static method calls, one line to complete HTTP requests',
                keywords: ['api', 'python', '简洁', 'simple', '易用', 'easy', '静态方法', 'static'],
                link: '#features'
            },
            {
                id: 'feature-threadsafe',
                category: 'feature',
                title: '线程安全',
                titleEn: 'Thread-Safe',
                content: '不可变对象设计，无锁化架构，完美支持并发场景',
                contentEn: 'Immutable object design, lock-free architecture, perfect for concurrency',
                keywords: ['线程', 'thread', '安全', 'safe', '并发', 'concurrency', '不可变', 'immutable'],
                link: '#features'
            },
            {
                id: 'feature-interceptor',
                category: 'feature',
                title: '拦截器链',
                titleEn: 'Interceptor Chain',
                content: '类 OkHttp 拦截器机制，支持认证、日志、重试等扩展',
                contentEn: 'OkHttp-like interceptor mechanism, supports auth, logging, retry',
                keywords: ['拦截器', 'interceptor', '扩展', 'extension', '认证', 'auth', '日志', 'logging'],
                link: '#features'
            },
            {
                id: 'feature-sse',
                category: 'feature',
                title: 'SSE 流式支持',
                titleEn: 'SSE Streaming',
                content: '真正的非阻塞 Server-Sent Events，实时数据推送',
                contentEn: 'True non-blocking Server-Sent Events, real-time data push',
                keywords: ['sse', 'stream', '实时', 'real-time', '流式', 'streaming', '事件', 'events'],
                link: '#features'
            },
            {
                id: 'feature-zero',
                category: 'feature',
                title: '零依赖',
                titleEn: 'Zero Dependencies',
                content: '仅使用 JDK 标准库，无任何第三方 JAR 包',
                contentEn: 'Only JDK standard library, no third-party JARs',
                keywords: ['零依赖', 'zero', '依赖', 'dependency', '标准库', 'standard', '纯净', 'pure'],
                link: '#features'
            },

            // Code Examples
            {
                id: 'code-get',
                category: 'code',
                title: '基础 GET 请求',
                titleEn: 'Basic GET Request',
                content: 'JNet.get("https://api.example.com/data") - 就像 Python requests！',
                contentEn: 'JNet.get("https://api.example.com/data") - Just like Python requests!',
                keywords: ['get', '请求', 'request', '基础', 'basic', '示例', 'example'],
                link: '#demo'
            },
            {
                id: 'code-post',
                category: 'code',
                title: 'POST JSON 数据',
                titleEn: 'POST JSON Data',
                content: 'JNet.post(url, JNet.json().put("name", "Alice"))',
                contentEn: 'JNet.post(url, JNet.json().put("name", "Alice"))',
                keywords: ['post', 'json', '数据', 'data', '提交', 'submit'],
                link: '#demo'
            },
            {
                id: 'code-client',
                category: 'code',
                title: '自定义客户端',
                titleEn: 'Custom Client',
                content: 'JNetClient.newBuilder().connectTimeout(5000).addInterceptor(...).build()',
                contentEn: 'JNetClient.newBuilder().connectTimeout(5000).addInterceptor(...).build()',
                keywords: ['client', '自定义', 'custom', 'builder', 'timeout', 'interceptor'],
                link: '#demo'
            },
            {
                id: 'code-async',
                category: 'code',
                title: '异步请求',
                titleEn: 'Async Request',
                content: 'CompletableFuture<String> future = JNet.getAsync(url)',
                contentEn: 'CompletableFuture<String> future = JNet.getAsync(url)',
                keywords: ['async', '异步', 'future', 'completable', '并发', 'concurrent'],
                link: '#demo'
            },
            {
                id: 'code-sse',
                category: 'code',
                title: 'SSE 实时流',
                titleEn: 'SSE Real-time Stream',
                content: 'SSEClient sse = new SSEClient(); sse.connect(url, listener)',
                contentEn: 'SSEClient sse = new SSEClient(); sse.connect(url, listener)',
                keywords: ['sse', 'stream', '实时', 'event', 'listener'],
                link: '#demo'
            },

            // Performance
            {
                id: 'perf-memory',
                category: 'performance',
                title: '内存占用对比',
                titleEn: 'Memory Usage Comparison',
                content: 'JNet: 12-18MB vs OkHttp: 40-60MB vs Apache: 80+MB',
                contentEn: 'JNet: 12-18MB vs OkHttp: 40-60MB vs Apache: 80+MB',
                keywords: ['内存', 'memory', '对比', 'comparison', '性能', 'performance', 'benchmark'],
                link: '#performance'
            },
            {
                id: 'perf-http2',
                category: 'performance',
                title: 'HTTP/2 支持',
                titleEn: 'HTTP/2 Support',
                content: 'JNet 完全支持 HTTP/2 协议',
                contentEn: 'JNet fully supports HTTP/2 protocol',
                keywords: ['http2', 'protocol', '协议', '支持', 'support'],
                link: '#performance'
            },
            {
                id: 'perf-deps',
                category: 'performance',
                title: '零依赖优势',
                titleEn: 'Zero Dependency Advantage',
                content: 'JNet: 0 dependencies vs OkHttp: 3+ vs Apache: 5+',
                contentEn: 'JNet: 0 dependencies vs OkHttp: 3+ vs Apache: 5+',
                keywords: ['依赖', 'dependency', '数量', 'count', '优势', 'advantage'],
                link: '#performance'
            },

            // Architecture
            {
                id: 'arch-builder',
                category: 'architecture',
                title: '建造者模式',
                titleEn: 'Builder Pattern',
                content: 'JNetClient 使用建造者模式构建客户端配置',
                contentEn: 'JNetClient uses Builder pattern for client configuration',
                keywords: ['builder', '建造者', '设计模式', 'pattern', '配置', 'config'],
                link: '#architecture'
            },
            {
                id: 'arch-singleton',
                category: 'architecture',
                title: '单例模式',
                titleEn: 'Singleton Pattern',
                content: 'JNetClient 单例模式确保全局唯一实例',
                contentEn: 'JNetClient singleton ensures global unique instance',
                keywords: ['singleton', '单例', '模式', 'pattern', '全局', 'global'],
                link: '#architecture'
            },
            {
                id: 'arch-chain',
                category: 'architecture',
                title: '责任链模式',
                titleEn: 'Chain of Responsibility',
                content: '拦截器链采用责任链模式处理请求',
                contentEn: 'Interceptor chain uses Chain of Responsibility pattern',
                keywords: ['chain', '责任链', '拦截器', 'interceptor', '模式', 'pattern'],
                link: '#architecture'
            },
            {
                id: 'arch-strategy',
                category: 'architecture',
                title: '策略模式',
                titleEn: 'Strategy Pattern',
                content: '多种策略实现可扩展的请求处理',
                contentEn: 'Multiple strategies for extensible request handling',
                keywords: ['strategy', '策略', '模式', 'pattern', '扩展', 'extensible'],
                link: '#architecture'
            },
            {
                id: 'arch-immutable',
                category: 'architecture',
                title: '不可变对象',
                titleEn: 'Immutable Objects',
                content: 'Request 对象不可变，线程安全',
                contentEn: 'Request objects are immutable and thread-safe',
                keywords: ['immutable', '不可变', '线程安全', 'thread-safe', '设计', 'design'],
                link: '#architecture'
            },

            // Version Updates
            {
                id: 'version-341',
                category: 'version',
                title: 'v3.4.3 版本',
                titleEn: 'v3.4.3 Version',
                content: 'Pages 自动部署，版本号升级，GitHub Packages 发布测试',
                contentEn: 'Pages auto-deploy, version upgrade, GitHub Packages testing',
                keywords: ['3.4.3', '版本', 'version', '更新', 'update', '部署', 'deploy'],
                link: '#updates'
            },
            {
                id: 'version-features',
                category: 'version',
                title: '新增功能',
                titleEn: 'New Features',
                content: '自动化发布流程、动态版本管理、GitHub Packages 自动发布',
                contentEn: 'Automated release, dynamic versioning, GitHub Packages publishing',
                keywords: ['新增', 'new', '功能', 'feature', '自动化', 'automated'],
                link: '#updates'
            },
            {
                id: 'version-improvements',
                category: 'version',
                title: '改进优化',
                titleEn: 'Improvements',
                content: '优化 pom.xml 配置，全新 README.md 和架构文档',
                contentEn: 'Optimized pom.xml, new README.md and architecture docs',
                keywords: ['改进', 'improvement', '优化', 'optimize', '文档', 'docs'],
                link: '#updates'
            },

            // About
            {
                id: 'about-jnet',
                category: 'about',
                title: 'JNet 项目',
                titleEn: 'JNet Project',
                content: '极简、高性能、零依赖的 HTTP 客户端库',
                contentEn: 'Minimalist, high-performance, zero-dependency HTTP client',
                keywords: ['jnet', '项目', 'project', 'http', '客户端', 'client'],
                link: '#'
            },
            {
                id: 'about-github',
                category: 'about',
                title: 'GitHub 仓库',
                titleEn: 'GitHub Repository',
                content: 'NetCapture/JNet - 基于 JDK 11+ 原生 HttpClient',
                contentEn: 'NetCapture/JNet - Based on JDK 11+ native HttpClient',
                keywords: ['github', '仓库', 'repository', 'netcapture', 'star'],
                link: 'https://github.com/NetCapture/JNet'
            },
            {
                id: 'about-design',
                category: 'about',
                title: '设计哲学',
                titleEn: 'Design Philosophy',
                content: 'Python requests 风格，简洁优雅，功能强大',
                contentEn: 'Python requests style, concise, elegant, powerful',
                keywords: ['设计', 'design', '哲学', 'philosophy', 'python', '简洁', 'elegant'],
                link: '#architecture'
            }
        ];
    }

    /**
     * Search with scoring algorithm
     */
    search(query: string): SearchResult[] {
        // Validate query
        const validation = this.validationManager.validateSearchQuery(query);
        if (!validation.isValid || !validation.sanitizedValue) {
            return [];
        }

        const cleanQuery = validation.sanitizedValue.trim().toLowerCase();
        const minQueryLength = this.configManager.get('search').minQueryLength;

        if (cleanQuery.length < minQueryLength) {
            return [];
        }

        const keywords = cleanQuery.split(/\s+/).filter(k => k.length > 0);
        const lang = 'zh'; // Could be made dynamic

        const results = this.index.map(item => {
            let score = 0;
            const title = lang === 'zh' ? item.title : item.titleEn;
            const content = lang === 'zh' ? item.content : item.contentEn;
            const searchableText = `${title} ${content} ${item.keywords.join(' ')}`.toLowerCase();

            // Exact title match - highest score
            if (title.toLowerCase().includes(cleanQuery)) {
                score += 100;
            }

            // Keyword matching
            keywords.forEach(keyword => {
                // Title match
                if (title.toLowerCase().includes(keyword)) {
                    score += 50;
                }
                // Content match
                if (content.toLowerCase().includes(keyword)) {
                    score += 20;
                }
                // Keyword tag match
                if (item.keywords.some(k => k.toLowerCase().includes(keyword))) {
                    score += 30;
                }
                // Exact match
                if (searchableText === keyword) {
                    score += 100;
                }
            });

            // Category match
            if (item.category.includes(cleanQuery)) {
                score += 15;
            }

            return { ...item, score, title, content };
        })
        .filter(item => item.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, this.configManager.get('search').maxResults);

        return results;
    }

    /**
     * Get search index
     */
    getIndex(): SearchIndexItem[] {
        return [...this.index];
    }

    /**
     * Get category statistics
     */
    getCategoryStats(results: SearchResult[]): SearchStats {
        const stats: SearchStats = {};

        results.forEach(item => {
            stats[item.category] = (stats[item.category] || 0) + 1;
        });

        return stats;
    }

    /**
     * Highlight search terms in text
     */
    highlight(text: string, query: string): string {
        if (!query || !text) return text;

        const validation = this.validationManager.validateSearchQuery(query);
        if (!validation.isValid) return text;

        const keywords = validation.sanitizedValue.split(/\s+/).filter(k => k.length > 0);
        let highlighted = text;

        keywords.forEach(keyword => {
            try {
                const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
                highlighted = highlighted.replace(regex, '<mark>$1</mark>');
            } catch (error) {
                // Invalid regex, skip highlighting
                console.warn('Invalid regex for highlighting:', keyword);
            }
        });

        return highlighted;
    }

    /**
     * Add custom items to search index
     */
    addToIndex(items: SearchIndexItem | SearchIndexItem[]): void {
        const itemsArray = Array.isArray(items) ? items : [items];

        for (const item of itemsArray) {
            // Validate item
            if (this.validateSearchItem(item)) {
                this.index.push(item);
            } else {
                this.errorManager.handleError(
                    this.errorManager.createError(
                        ErrorType.VALIDATION_ERROR,
                        'Invalid search index item',
                        { item }
                    )
                );
            }
        }
    }

    /**
     * Search with category filter
     */
    searchWithFilter(query: string, category: string): SearchResult[] {
        const results = this.search(query);
        return results.filter(r => r.category === category);
    }

    /**
     * Get all categories
     */
    getCategories(): string[] {
        const categories = new Set(this.index.map(item => item.category));
        return Array.from(categories);
    }

    /**
     * Search by category only
     */
    searchByCategory(category: string): SearchResult[] {
        return this.index
            .filter(item => item.category === category)
            .map(item => ({ ...item, score: 1 }));
    }

    // ==================== Private Helper Methods ====================

    private validateSearchItem(item: any): item is SearchIndexItem {
        return (
            item &&
            typeof item.id === 'string' &&
            typeof item.category === 'string' &&
            typeof item.title === 'string' &&
            typeof item.titleEn === 'string' &&
            typeof item.content === 'string' &&
            typeof item.contentEn === 'string' &&
            Array.isArray(item.keywords) &&
            typeof item.link === 'string'
        );
    }
}

/**
 * Convenience function for searching
 */
export function search(query: string): SearchResult[] {
    return SearchManager.getInstance().search(query);
}

/**
 * Convenience function for highlighting
 */
export function highlight(text: string, query: string): string {
    return SearchManager.getInstance().highlight(text, query);
}
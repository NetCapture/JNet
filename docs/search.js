/**
 * JNet GitHub Pages - 搜索功能管理器
 * 负责搜索索引、算法和 UI 管理
 */

// 搜索索引数据 - 包含所有可搜索内容
const SEARCH_INDEX = [
    // 核心特性
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
        keywords: ['3.5.1', 'python', '简洁', 'simple', '易用', 'easy', '静态方法', 'static'],
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
        keywords: ['3.5.1', 'stream', '实时', 'real-time', '流式', 'streaming', '事件', 'events'],
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

    // 代码示例
    {
        id: 'code-get',
        category: 'code',
        title: '基础 GET 请求',
        titleEn: 'Basic GET Request',
        content: 'JNet.get("https://api.example.com/data") - 就像 Python requests！',
        contentEn: 'JNet.get("https://api.example.com/data") - Just like Python requests!',
        keywords: ['3.5.1', '请求', 'request', '基础', 'basic', '示例', 'example'],
        link: '#demo'
    },
    {
        id: 'code-post',
        category: 'code',
        title: 'POST JSON 数据',
        titleEn: 'POST JSON Data',
        content: 'JNet.postJson(url, payload)',
        contentEn: 'JNet.postJson(url, payload)',
        keywords: ['3.5.1', 'json', '数据', 'data', '提交', 'submit'],
        link: '#demo'
    },
    {
        id: 'code-client',
        category: 'code',
        title: '自定义客户端',
        titleEn: 'Custom Client',
        content: 'JNetClient.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()',
        contentEn: 'JNetClient.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()',
        keywords: ['3.5.1', '自定义', 'custom', 'builder', 'timeout', 'interceptor'],
        link: '#demo'
    },
    {
        id: 'code-async',
        category: 'code',
        title: '异步请求',
        titleEn: 'Async Request',
        content: 'CompletableFuture<String> future = JNet.getAsync(url)',
        contentEn: 'CompletableFuture<String> future = JNet.getAsync(url)',
        keywords: ['3.5.1', '异步', 'future', 'completable', '并发', 'concurrent'],
        link: '#demo'
    },
    {
        id: 'code-sse',
        category: 'code',
        title: 'SSE 实时流',
        titleEn: 'SSE Real-time Stream',
        content: 'SSEClient sse = new SSEClient(); sse.stream(url, null, listener)',
        contentEn: 'SSEClient sse = new SSEClient(); sse.stream(url, null, listener)',
        keywords: ['3.5.1', 'stream', '实时', 'event', 'listener'],
        link: '#demo'
    },

    // 性能对比
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
        keywords: ['3.5.1', 'protocol', '协议', '支持', 'support'],
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

    // 架构设计
    {
        id: 'arch-builder',
        category: 'architecture',
        title: '建造者模式',
        titleEn: 'Builder Pattern',
        content: 'JNetClient 使用建造者模式构建客户端配置',
        contentEn: 'JNetClient uses Builder pattern for client configuration',
        keywords: ['3.5.1', '建造者', '设计模式', 'pattern', '配置', 'config'],
        link: '#architecture'
    },
    {
        id: 'arch-singleton',
        category: 'architecture',
        title: '单例模式',
        titleEn: 'Singleton Pattern',
        content: 'JNetClient 单例模式确保全局唯一实例',
        contentEn: 'JNetClient singleton ensures global unique instance',
        keywords: ['3.5.1', '单例', '模式', 'pattern', '全局', 'global'],
        link: '#architecture'
    },
    {
        id: 'arch-chain',
        category: 'architecture',
        title: '责任链模式',
        titleEn: 'Chain of Responsibility',
        content: '拦截器链采用责任链模式处理请求',
        contentEn: 'Interceptor chain uses Chain of Responsibility pattern',
        keywords: ['3.5.1', '责任链', '拦截器', 'interceptor', '模式', 'pattern'],
        link: '#architecture'
    },
    {
        id: 'arch-strategy',
        category: 'architecture',
        title: '策略模式',
        titleEn: 'Strategy Pattern',
        content: '多种策略实现可扩展的请求处理',
        contentEn: 'Multiple strategies for extensible request handling',
        keywords: ['3.5.1', '策略', '模式', 'pattern', '扩展', 'extensible'],
        link: '#architecture'
    },
    {
        id: 'arch-immutable',
        category: 'architecture',
        title: '不可变对象',
        titleEn: 'Immutable Objects',
        content: 'Request 对象不可变，线程安全',
        contentEn: 'Request objects are immutable and thread-safe',
        keywords: ['3.5.1', '不可变', '线程安全', 'thread-safe', '设计', 'design'],
        link: '#architecture'
    },

    // 版本更新
    {
        id: 'version-341',
        category: 'version',
        title: '3.5.1 版本',
        titleEn: '3.5.1 Version',
        content: 'Pages 自动部署，版本号升级，GitHub Packages 发布测试',
        contentEn: 'Pages auto-deploy, version upgrade, GitHub Packages testing',
        keywords: ['3.5.1', '版本', 'version', '更新', 'update', '部署', 'deploy'],
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

    // 项目信息
    {
        id: 'about-jnet',
        category: 'about',
        title: 'JNet 项目',
        titleEn: 'JNet Project',
        content: '极简、高性能、零依赖的 HTTP 客户端库',
        contentEn: 'Minimalist, high-performance, zero-dependency HTTP client',
        keywords: ['3.5.1', '项目', 'project', 'http', '客户端', 'client'],
        link: '#'
    },
    {
        id: 'about-github',
        category: 'about',
        title: 'GitHub 仓库',
        titleEn: 'GitHub Repository',
        content: 'NetCapture/JNet - 基于 JDK 11+ 原生 HttpClient',
        contentEn: 'NetCapture/JNet - Based on JDK 11+ native HttpClient',
        keywords: ['3.5.1', '仓库', 'repository', 'netcapture', 'star'],
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

// 搜索管理器 - 处理搜索逻辑
class SearchManager {
    constructor(langManager) {
        this.langManager = langManager;
        this.index = SEARCH_INDEX;
    }

    // 搜索核心算法
    search(query) {
        if (!query || query.trim().length === 0) {
            return [];
        }

        const lang = this.langManager.getCurrentLanguage();
        const keywords = query.toLowerCase().split(/\s+/).filter(k => k.length > 0);

        const results = this.index.map(item => {
            let score = 0;
            const title = lang === 'zh' ? item.title : item.titleEn;
            const content = lang === 'zh' ? item.content : item.contentEn;
            const searchableText = `${title} ${content} ${item.keywords.join(' ')}`.toLowerCase();

            // 精确匹配标题 - 高分
            if (title.toLowerCase().includes(query.toLowerCase())) {
                score += 100;
            }

            // 关键词匹配
            keywords.forEach(keyword => {
                // 标题匹配
                if (title.toLowerCase().includes(keyword)) {
                    score += 50;
                }
                // 内容匹配
                if (content.toLowerCase().includes(keyword)) {
                    score += 20;
                }
                // 关键词标签匹配
                if (item.keywords.some(k => k.toLowerCase().includes(keyword))) {
                    score += 30;
                }
                // 完全匹配
                if (searchableText === keyword) {
                    score += 100;
                }
            });

            // 类别匹配
            if (item.category.includes(query.toLowerCase())) {
                score += 15;
            }

            return { ...item, score, title, content };
        })
        .filter(item => item.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, 10); // 限制结果数量

        return results;
    }

    // 获取分类统计
    getCategoryStats(results) {
        const stats = {};
        results.forEach(item => {
            stats[item.category] = (stats[item.category] || 0) + 1;
        });
        return stats;
    }

    // 高亮搜索词
    highlight(text, query) {
        if (!query) return text;

        const keywords = query.split(/\s+/).filter(k => k.length > 0);
        let highlighted = text;

        keywords.forEach(keyword => {
            const regex = new RegExp(`(${keyword})`, 'gi');
            highlighted = highlighted.replace(regex, '<mark>$1</mark>');
        });

        return highlighted;
    }
}

// 搜索 UI 管理器 - 处理界面交互
class SearchUIManager {
    constructor(searchManager, langManager) {
        this.searchManager = searchManager;
        this.langManager = langManager;
        this.isOpen = false;
        this.currentQuery = '';
        this.selectedIndex = 0;
        this.searchResults = [];
    }

    init() {
        this.createSearchUI();
        this.bindEvents();
        console.log('🔍 Search UI initialized');
    }

    createSearchUI() {
        // 搜索模态框
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
                               placeholder="${this.langManager.translate('search_placeholder')}"
                               autocomplete="off">
                        <span class="search-shortcut">Ctrl K</span>
                    </div>
                    <button class="search-close" id="searchClose">✕</button>
                </div>
                <div class="search-hint">${this.langManager.translate('search_hint')}</div>
                <div class="search-results" id="searchResults">
                    <div class="search-empty">
                        <div class="empty-icon">🔍</div>
                        <div class="empty-text">开始搜索...</div>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        // 添加搜索样式
        this.addSearchStyles();
    }

    addSearchStyles() {
        const style = document.createElement('style');
        style.textContent = `
            /* 搜索模态框 */
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

            /* 搜索头部 */
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

            /* 搜索提示 */
            .search-hint {
                padding: 8px 20px;
                font-size: 0.85rem;
                color: #64748b;
                background: #f8fafc;
                border-bottom: 1px solid #e2e8f0;
            }

            /* 搜索结果 */
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

            /* 结果项 */
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

            /* 高亮标记 */
            mark {
                background: #fef08a;
                color: #854d0e;
                padding: 0 2px;
                border-radius: 2px;
                font-weight: 600;
            }

            /* 空状态 */
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

            /* 分类统计 */
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

            /* 动画 */
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

            /* 移动端适配 */
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

    bindEvents() {
        // 触发按钮点击 - 支持浮动按钮和header中的搜索按钮
        const triggerBtn = document.getElementById('searchTriggerBtn');
        if (triggerBtn) {
            triggerBtn.addEventListener('click', () => this.openSearch());
        }

        // Header中的搜索按钮 (id="searchBtn")
        const headerSearchBtn = document.getElementById('searchBtn');
        if (headerSearchBtn) {
            headerSearchBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.openSearch();
            });
        }

        // 关闭按钮
        const closeBtn = document.getElementById('searchClose');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.closeSearch());
        }

        // 点击遮罩层关闭
        const overlay = document.querySelector('.search-overlay');
        if (overlay) {
            overlay.addEventListener('click', () => this.closeSearch());
        }

        // 搜索输入
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.handleInput(e.target.value));
            searchInput.addEventListener('keydown', (e) => this.handleKeyDown(e));
        }

        // 键盘快捷键
        document.addEventListener('keydown', (e) => {
            // Ctrl+K 或 Cmd+K 打开搜索
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                this.openSearch();
            }
            // ESC 关闭搜索
            if (e.key === 'Escape' && this.isOpen) {
                this.closeSearch();
            }
        });
    }

    openSearch() {
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

    closeSearch() {
        this.isOpen = false;
        const modal = document.getElementById('searchModal');

        if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = '';
        }

        // 重置状态
        this.currentQuery = '';
        this.selectedIndex = 0;
        this.searchResults = [];

        const input = document.getElementById('searchInput');
        if (input) input.value = '';

        this.renderResults([]);
    }

    handleInput(query) {
        this.currentQuery = query.trim();

        if (this.currentQuery.length === 0) {
            this.renderResults([]);
            return;
        }

        const results = this.searchManager.search(this.currentQuery);
        this.searchResults = results;
        this.selectedIndex = 0;
        this.renderResults(results);
    }

    handleKeyDown(e) {
        if (!this.isOpen) return;

        const resultsContainer = document.getElementById('searchResults');
        const items = resultsContainer.querySelectorAll('.search-result-item');

        switch(e.key) {
            case 'ArrowDown':
                e.preventDefault();
                this.selectedIndex = Math.min(this.selectedIndex + 1, items.length - 1);
                this.updateSelection(items);
                break;

            case 'ArrowUp':
                e.preventDefault();
                this.selectedIndex = Math.max(this.selectedIndex - 1, 0);
                this.updateSelection(items);
                break;

            case 'Enter':
                e.preventDefault();
                if (items[this.selectedIndex]) {
                    items[this.selectedIndex].click();
                }
                break;
        }
    }

    updateSelection(items) {
        items.forEach((item, index) => {
            if (index === this.selectedIndex) {
                item.classList.add('selected');
                item.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            } else {
                item.classList.remove('selected');
            }
        });
    }

    renderResults(results) {
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
                        <div class="empty-text">${this.langManager.translate('search_no_results')}</div>
                        <div style="margin-top: 8px; font-size: 0.85rem; color: #94a3b8;">
                            ${this.langManager.translate('search_try_other')}
                        </div>
                    </div>
                `;
            }
            return;
        }

        // 渲染结果列表
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

        // 添加分类统计
        const stats = this.searchManager.getCategoryStats(results);
        const statsHtml = Object.entries(stats).map(([category, count]) => `
            <span class="stat-badge">
                ${this.getCategoryLabel(category)}<span class="count">${count}</span>
            </span>
        `).join('');

        container.innerHTML = html + `<div class="search-stats">${statsHtml}</div>`;

        // 绑定点击事件
        container.querySelectorAll('.search-result-item').forEach(item => {
            item.addEventListener('click', () => {
                const link = item.getAttribute('data-link');
                this.handleResultClick(link);
            });
        });
    }

    getCategoryIcon(category) {
        const icons = {
            feature: '⚡',
            code: '💻',
            performance: '📊',
            architecture: '🏗️',
            version: '📦',
            about: 'ℹ️'
        };
        return icons[category] || '🔍';
    }

    getCategoryLabel(category) {
        const lang = this.langManager.getCurrentLanguage();
        const labels = {
            feature: { zh: '特性', en: 'Feature' },
            code: { zh: '代码', en: 'Code' },
            performance: { zh: '性能', en: 'Performance' },
            architecture: { zh: '架构', en: 'Architecture' },
            version: { zh: '版本', en: 'Version' },
            about: { zh: '关于', en: 'About' }
        };
        return labels[category]?.[lang] || category;
    }

    handleResultClick(link) {
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
}

// 导出供 app.js 使用
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { SearchManager, SearchUIManager, SEARCH_INDEX };
}

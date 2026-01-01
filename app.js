/**
 * JNet GitHub Pages Dynamic Content Loader
 * 负责动态加载统计数据、多语言和搜索
 */

class JNetPagesApp {
    constructor() {
        this.apiBase = 'https://api.github.com/repos/NetCapture/JNet';
        this.cache = {
            stats: null
        };
        this.langManager = langManager; // 来自 i18n.js
        this.searchManager = null; // 将在初始化时创建
        this.init();
    }

    async init() {
        console.log('🚀 JNet Pages App Initializing...');

        // 1. 初始化语言管理器
        this.initLanguage();

        // 2. Update dynamic content after language is initialized
        this.updateDynamicContent();

        // 3. 并行加载数据
        await Promise.all([
            this.loadStats()
        ]);

        // 4. 更新页面内容
        this.updateStats();

        // 5. 延迟初始化搜索（等待翻译完成）
        setTimeout(() => {
            this.initSearch();
        }, 500);

        console.log('✅ JNet Pages App Ready');
    }

    // ==================== 语言管理 ====================

    initLanguage() {
        // 使用简单的按钮式语言切换器
        this.initSimpleLanguageSwitcher();

        // 监听语言变化
        this.langManager.onLanguageChange((lang) => {
            this.onLanguageChange(lang);
            this.updateSimpleLanguageSwitcher(lang);
        });

        // Load language from localStorage if available
        const storedLang = localStorage.getItem('jnet_language');
        if (storedLang && (storedLang === 'zh' || storedLang === 'en')) {
            this.langManager.setLanguage(storedLang);
        }

        // 初始翻译
        this.langManager.updateContent();
        this.langManager.updateUI();
        this.updateSimpleLanguageSwitcher(this.langManager.getCurrentLanguage());
    }

    // 简单的语言切换器 - 按钮式
    initSimpleLanguageSwitcher() {
        const langButtons = document.querySelectorAll('.lang-btn');

        if (langButtons.length === 0) return;

        langButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                const lang = btn.dataset.lang;
                this.langManager.setLanguage(lang);
                localStorage.setItem('jnet_language', lang);
            });
        });
    }

    // 更新语言切换器按钮状态
    updateSimpleLanguageSwitcher(lang) {
        const langButtons = document.querySelectorAll('.lang-btn');
        langButtons.forEach(btn => {
            if (btn.dataset.lang === lang) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    onLanguageChange(lang) {
        console.log(`Language changed to: ${lang}`);

        // 更新动态内容的翻译
        this.updateDynamicContent();

        // 如果搜索已初始化，更新搜索提示
        if (this.searchUI) {
            const searchInput = document.querySelector('#searchInput');
            const searchHint = document.querySelector('.search-hint');
            if (searchInput) {
                searchInput.placeholder = this.langManager.translate('search_placeholder');
            }
            if (searchHint) {
                searchHint.textContent = this.langManager.translate('search_hint');
            }
        }

        // 更新页脚时间
        const now = new Date().toLocaleString(lang === 'zh' ? 'zh-CN' : 'en-US');
        const lastUpdatedEl = document.getElementById('lastUpdated');
        if (lastUpdatedEl) {
            lastUpdatedEl.textContent = `${this.langManager.translate('footer_updated')}: ${now}`;
        }

        // Sync with TypeScript LanguageManager if available (for discuss.html)
        if (typeof window !== 'undefined' && window.languageManagerInstance) {
            window.languageManagerInstance.setLanguage(lang);
        }

        // Save to localStorage for cross-page sync
        if (typeof localStorage !== 'undefined') {
            localStorage.setItem('jnet_language', lang);
        }
    }

    updateDynamicContent() {
        // 更新统计标签
        const statsLabels = document.querySelectorAll('.stat-label');
        const statKeys = ['stats_github_stars', 'stats_forks', 'stats_issues', 'stats_lines'];
        statsLabels.forEach((el, index) => {
            if (statKeys[index]) {
                el.textContent = this.langManager.translate(statKeys[index]);
            }
        });

        // 更新特性卡片
        const featureCards = document.querySelectorAll('.feature-card');
        const featureData = [
            { title: 'feature_1_title', desc: 'feature_1_desc' },
            { title: 'feature_2_title', desc: 'feature_2_desc' },
            { title: 'feature_3_title', desc: 'feature_3_desc' },
            { title: 'feature_4_title', desc: 'feature_4_desc' },
            { title: 'feature_5_title', desc: 'feature_5_desc' },
            { title: 'feature_6_title', desc: 'feature_6_desc' }
        ];

        featureCards.forEach((card, index) => {
            if (featureData[index]) {
                const titleEl = card.querySelector('.feature-title');
                const descEl = card.querySelector('.feature-desc');
                if (titleEl) titleEl.textContent = this.langManager.translate(featureData[index].title);
                if (descEl) descEl.textContent = this.langManager.translate(featureData[index].desc);
            }
        });

        // 更新特性高亮文本
        const highlightTexts = document.querySelectorAll('.highlight-text');
        const highlightKeys = ['highlight_1', 'highlight_2', 'highlight_3', 'highlight_4'];
        highlightTexts.forEach((el, index) => {
            if (highlightKeys[index]) {
                el.textContent = this.langManager.translate(highlightKeys[index]);
            }
        });

        // 更新学习曲线
        const curveCells = document.querySelectorAll('.comparison-table tbody tr:last-child td');
        if (curveCells.length >= 5) {
            const lang = this.langManager.getCurrentLanguage();
            if (lang === 'en') {
                curveCells[1].textContent = 'Easy';
                curveCells[2].textContent = 'Medium';
                curveCells[3].textContent = 'Steep';
                curveCells[4].textContent = 'Steep';
            } else {
                curveCells[1].textContent = '平缓';
                curveCells[2].textContent = '中等';
                curveCells[3].textContent = '陡峭';
                curveCells[4].textContent = '陡峭';
            }
        }

        // 更新 Changelog 内容
        const changelogItems = document.querySelectorAll('.version-features li');
        const changelogKeys = [
            'changelog_item_1', 'changelog_item_2', 'changelog_item_3', 'changelog_item_4',
            'changelog_item_5', 'changelog_item_6', 'changelog_item_7',
            'changelog_item_8', 'changelog_item_9'
        ];
        changelogItems.forEach((el, index) => {
            if (changelogKeys[index]) {
                el.textContent = this.langManager.translate(changelogKeys[index]);
            }
        });

        // 更新代码注释
        const codeComments = document.querySelectorAll('.code-comment');
        const commentKeys = ['code_comment_1', 'code_comment_2', 'code_comment_3', 'code_comment_4', 'code_comment_5'];
        codeComments.forEach((el, index) => {
            if (commentKeys[index]) {
                el.textContent = this.langManager.translate(commentKeys[index]);
            }
        });

        // 更新表格行
        const tableRows = document.querySelectorAll('.comparison-table tbody tr');
        const rowKeys = ['row_deps', 'row_lines', 'row_http2', 'row_interceptor', 'row_sse', 'row_memory', 'row_curve'];
        tableRows.forEach((row, index) => {
            if (rowKeys[index]) {
                const firstCell = row.querySelector('td:first-child');
                if (firstCell) {
                    firstCell.innerHTML = `<strong>${this.langManager.translate(rowKeys[index])}</strong>`;
                }
            }
        });

        // 更新架构设计模式标签
        const patternTags = document.querySelectorAll('.pattern-tag');
        const patterns = ['建造者模式', '单例模式', '责任链模式', '策略模式', '不可变对象', '模板方法'];
        const patternsEn = ['Builder', 'Singleton', 'Chain of Responsibility', 'Strategy', 'Immutable', 'Template Method'];

        if (this.langManager.getCurrentLanguage() === 'en') {
            patternTags.forEach((tag, index) => {
                if (patternsEn[index]) tag.textContent = patternsEn[index];
            });
        } else {
            patternTags.forEach((tag, index) => {
                if (patterns[index]) tag.textContent = patterns[index];
            });
        }
    }

    // ==================== 数据加载 ====================

    async loadStats() {
        try {
            const response = await fetch(this.apiBase);
            if (response.ok) {
                this.cache.stats = await response.json();
            }
        } catch (error) {
            console.warn('Failed to load stats:', error);
        }
    }

    // ==================== 页面更新 ====================

    updateStats() {
        if (!this.cache.stats) return;

        const stats = [
            { selector: '.stat-stars', value: this.cache.stats.stargazers_count },
            { selector: '.stat-forks', value: this.cache.stats.forks_count },
            { selector: '.stat-issues', value: this.cache.stats.open_issues_count }
        ];

        stats.forEach(stat => {
            const el = document.querySelector(stat.selector);
            if (el && stat.value !== null) {
                el.textContent = this.formatNumber(stat.value);
                el.style.animation = 'pulse 0.5s ease';
            }
        });
    }

    // ==================== 搜索功能 ====================

    initSearch() {
        // 动态加载搜索模块
        if (typeof SearchManager === 'undefined' || typeof SearchUIManager === 'undefined') {
            console.log('🔍 Loading search module...');
            const script = document.createElement('script');
            script.src = 'search.js';
            script.onload = () => {
                console.log('✅ Search module loaded successfully');
                this.setupSearch();
            };
            script.onerror = (e) => {
                console.error('❌ Failed to load search.js:', e);
            };
            document.head.appendChild(script);
        } else {
            console.log('🔍 Search module already available');
            this.setupSearch();
        }
    }

    setupSearch() {
        this.searchManager = new SearchManager(this.langManager);
        this.searchUI = new SearchUIManager(this.searchManager, this.langManager);
        this.searchUI.init();
        // Note: searchUI.init() already binds events to #searchBtn via bindEvents()
    }

    // ==================== 工具函数 ====================

    formatNumber(num) {
        if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'k';
        }
        return num.toString();
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    // 确保 i18n.js 已加载
    if (typeof langManager !== 'undefined') {
        new JNetPagesApp();
    } else {
        console.error('i18n.js not loaded yet!');
        // 等待 i18n.js 加载
        setTimeout(() => {
            if (typeof langManager !== 'undefined') {
                new JNetPagesApp();
            }
        }, 100);
    }
});

// 添加一些全局工具
if (typeof module !== 'undefined' && module.exports) {
    module.exports = JNetPagesApp;
}

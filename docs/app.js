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

        // 2. 并行加载数据
        await Promise.all([
            this.loadStats()
        ]);

        // 3. 更新页面内容
        this.updateStats();

        // 4. 延迟初始化搜索（等待翻译完成）
        setTimeout(() => {
            this.initSearch();
        }, 500);

        console.log('✅ JNet Pages App Ready');
    }

    // ==================== 语言管理 ====================

    initLanguage() {
        // 创建语言选择器
        this.createLanguageSelector();

        // 监听语言变化
        this.langManager.onLanguageChange((lang) => {
            this.onLanguageChange(lang);
            this.updateLanguageSelector(lang);
        });

        // 初始翻译
        this.langManager.updateContent();
        this.langManager.updateUI();
        this.updateLanguageSelector(this.langManager.getCurrentLanguage());
    }

    createLanguageSelector() {
        const langList = document.getElementById('langList');
        const dropdownBtn = document.getElementById('langDropdownBtn');
        const dropdownMenu = document.getElementById('langDropdownMenu');
        const overlay = document.getElementById('langOverlay');
        const searchInput = document.getElementById('langSearchInput');

        if (!langList || !dropdownBtn) return;

        // 简化：只显示中文和英文
        const renderLangList = (filter = '') => {
            const allLangs = this.langManager.getSupportedLanguages();
            let html = '';

            // 只显示中文和英文
            ['zh', 'en'].forEach(code => {
                const lang = allLangs[code];
                if (!lang) return;

                if (filter) {
                    const match = lang.name.toLowerCase().includes(filter.toLowerCase()) ||
                                 lang.native.toLowerCase().includes(filter.toLowerCase()) ||
                                 code.toLowerCase().includes(filter.toLowerCase());
                    if (!match) return;
                }

                const isActive = this.langManager.getCurrentLanguage() === code;
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

            langList.innerHTML = html || '<div style="padding: 20px; text-align: center; color: #999;">未找到匹配的语言</div>';

            // 绑定点击事件
            langList.querySelectorAll('.lang-item').forEach(item => {
                item.addEventListener('click', () => {
                    const lang = item.dataset.lang;
                    this.langManager.setLanguage(lang);
                    this.closeLanguageDropdown();
                });
            });
        };

        // 打开/关闭下拉菜单
        const toggleDropdown = () => {
            const isOpen = dropdownMenu.classList.contains('show');
            if (isOpen) {
                this.closeLanguageDropdown();
            } else {
                this.openLanguageDropdown();
            }
        };

        // 打开
        this.openLanguageDropdown = () => {
            dropdownBtn.classList.add('active');
            dropdownMenu.classList.add('show');
            overlay.classList.add('show');
            renderLangList();
            setTimeout(() => searchInput?.focus(), 100);
        };

        // 关闭
        this.closeLanguageDropdown = () => {
            dropdownBtn.classList.remove('active');
            dropdownMenu.classList.remove('show');
            overlay.classList.remove('show');
            if (searchInput) searchInput.value = '';
        };

        // 更新显示
        this.updateLanguageSelector = (lang) => {
            const langInfo = this.langManager.getLanguageInfo(lang);
            const flagEl = document.getElementById('currentFlag');
            const nameEl = document.getElementById('currentLangName');
            if (flagEl) flagEl.textContent = langInfo.flag;
            if (nameEl) nameEl.textContent = langInfo.native;
        };

        // 事件绑定
        dropdownBtn.addEventListener('click', toggleDropdown);
        overlay.addEventListener('click', () => this.closeLanguageDropdown());

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                renderLangList(e.target.value);
            });
        }

        // ESC键关闭
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && dropdownMenu.classList.contains('show')) {
                this.closeLanguageDropdown();
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

        // 绑定搜索按钮点击事件
        const searchBtn = document.getElementById('searchBtn');
        if (searchBtn) {
            searchBtn.addEventListener('click', (e) => {
                e.preventDefault();
                if (this.searchUI) {
                    this.searchUI.openSearch();
                }
            });
        }
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

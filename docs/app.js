/**
 * JNet GitHub Pages Dynamic Content Loader
 * 负责动态加载版本信息、统计数据和功能亮点
 */

class JNetPagesApp {
    constructor() {
        this.apiBase = 'https://api.github.com/repos/NetCapture/JNet';
        this.cache = {
            releases: null,
            readme: null,
            stats: null
        };
        this.init();
    }

    async init() {
        console.log('🚀 JNet Pages App Initializing...');

        // 并行加载数据
        await Promise.all([
            this.loadStats(),
            this.loadLatestRelease(),
            this.loadReadme()
        ]);

        // 更新页面内容
        this.updateStats();
        this.updateVersionInfo();
        this.updateFeatureHighlights();
        this.updateChangelog();

        console.log('✅ JNet Pages App Ready');
    }

    // 加载仓库统计数据
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

    // 加载最新版本信息
    async loadLatestRelease() {
        try {
            const response = await fetch(`${this.apiBase}/releases/latest`);
            if (response.ok) {
                this.cache.releases = await response.json();
            }
        } catch (error) {
            console.warn('Failed to load releases:', error);
        }
    }

    // 加载 README（用于提取功能亮点）
    async loadReadme() {
        try {
            const response = await fetch(`${this.apiBase}/readme`, {
                headers: { 'Accept': 'application/vnd.github.raw' }
            });
            if (response.ok) {
                this.cache.readme = await response.text();
            }
        } catch (error) {
            console.warn('Failed to load readme:', error);
        }
    }

    // 更新统计数据展示
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

    // 更新版本信息
    updateVersionInfo() {
        if (!this.cache.releases) return;

        const release = this.cache.releases;
        const version = release.tag_name.replace('v', '');
        const publishDate = new Date(release.published_at).toLocaleString('zh-CN');

        // 更新版本号
        const versionEls = document.querySelectorAll('[data-version]');
        versionEls.forEach(el => {
            el.textContent = `v${version}`;
            el.classList.add('fade-in');
        });

        // 更新发布时间
        const timeEls = document.querySelectorAll('[data-publish-time]');
        timeEls.forEach(el => {
            el.textContent = publishDate;
        });

        // 更新下载链接
        const downloadBtn = document.querySelector('[data-download-link]');
        if (downloadBtn) {
            downloadBtn.href = release.html_url;
        }

        // 更新 Release Notes 链接
        const releaseLink = document.querySelector('[data-release-link]');
        if (releaseLink) {
            releaseLink.href = release.html_url;
        }
    }

    // 更新功能亮点
    updateFeatureHighlights() {
        if (!this.cache.releases || !this.cache.releases.body) return;

        const body = this.cache.releases.body;
        const highlights = this.extractHighlights(body);

        const container = document.querySelector('#featureHighlights');
        if (container && highlights.length > 0) {
            container.innerHTML = highlights.map(h => `
                <div class="highlight-item">
                    <span class="highlight-icon">${h.icon}</span>
                    <span class="highlight-text">${h.text}</span>
                </div>
            `).join('');
            container.style.opacity = '1';
        }
    }

    // 从 Release Notes 提取亮点
    extractHighlights(body) {
        const highlights = [];
        const lines = body.split('\n');

        // 提取 ✨ 新增功能
        lines.forEach(line => {
            if (line.includes('✨') || line.includes('✅')) {
                const text = line.replace(/^[*-]\s*(✨|✅)\s*/, '').trim();
                if (text) highlights.push({ icon: '✨', text });
            } else if (line.includes('🔧') || line.includes('improvement')) {
                const text = line.replace(/^[*-]\s*(🔧)\s*/, '').trim();
                if (text) highlights.push({ icon: '🔧', text });
            } else if (line.includes('🐛')) {
                const text = line.replace(/^[*-]\s*(🐛)\s*/, '').trim();
                if (text) highlights.push({ icon: '🐛', text });
            }
        });

        return highlights.slice(0, 6); // 最多显示6个
    }

    // 更新变更日志
    updateChangelog() {
        if (!this.cache.releases) return;

        const container = document.querySelector('#changelogContainer');
        if (!container) return;

        const release = this.cache.releases;
        const version = release.tag_name.replace('v', '');
        const publishDate = new Date(release.published_at).toLocaleDateString('zh-CN');

        // 解析 release body 为结构化数据
        const sections = this.parseReleaseBody(release.body);

        container.innerHTML = `
            <div class="version-card fade-in">
                <div class="version-header">
                    <span class="version-tag">v${version}</span>
                    <span class="version-date">${publishDate}</span>
                </div>
                ${sections.map(section => `
                    <div class="version-section">
                        <h4>${section.title}</h4>
                        <ul class="version-features">
                            ${section.items.map(item => `<li>${item}</li>`).join('')}
                        </ul>
                    </div>
                `).join('')}
                <div style="margin-top: 20px; text-align: center;">
                    <a href="${release.html_url}" target="_blank" class="btn btn-secondary" style="display: inline-block; padding: 10px 20px; background: var(--primary); color: white; text-decoration: none; border-radius: 8px;">
                        📋 查看完整 Release
                    </a>
                </div>
            </div>
        `;
    }

    // 解析 Release Body
    parseReleaseBody(body) {
        if (!body) return [];

        const sections = [];
        const lines = body.split('\n');

        let currentSection = null;

        lines.forEach(line => {
            // 检测章节标题
            if (line.startsWith('### ✨') || line.includes('新增功能')) {
                if (currentSection) sections.push(currentSection);
                currentSection = { title: '✨ 新增功能', items: [] };
            } else if (line.startsWith('### 🔧') || line.includes('改进')) {
                if (currentSection) sections.push(currentSection);
                currentSection = { title: '🔧 改进', items: [] };
            } else if (line.startsWith('### 🐛') || line.includes('修复')) {
                if (currentSection) sections.push(currentSection);
                currentSection = { title: '🐛 修复', items: [] };
            } else if (line.match(/^[-*]\s+/) && currentSection) {
                // 提取列表项
                const item = line.replace(/^[-*]\s+/, '').trim();
                if (item) currentSection.items.push(item);
            }
        });

        if (currentSection) sections.push(currentSection);
        return sections;
    }

    // 工具函数：格式化数字
    formatNumber(num) {
        if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'k';
        }
        return num.toString();
    }

    // 工具函数：创建动画卡片
    createAnimatedCard(content, delay = 0) {
        const card = document.createElement('div');
        card.className = 'feature-card fade-in';
        card.style.animationDelay = `${delay}s`;
        card.innerHTML = content;
        return card;
    }

    // 添加交互效果
    addInteractiveEffects() {
        // 按钮悬停效果
        document.querySelectorAll('.btn').forEach(btn => {
            btn.addEventListener('mouseenter', function() {
                this.style.transform = 'translateY(-2px) scale(1.05)';
            });
            btn.addEventListener('mouseleave', function() {
                this.style.transform = 'translateY(0) scale(1)';
            });
        });

        // 特性卡片悬停效果
        document.querySelectorAll('.feature-card').forEach(card => {
            card.addEventListener('mouseenter', function() {
                this.style.transform = 'translateY(-5px) scale(1.02)';
                this.style.boxShadow = '0 15px 35px rgba(0,0,0,0.15)';
            });
            card.addEventListener('mouseleave', function() {
                this.style.transform = 'translateY(0) scale(1)';
                this.style.boxShadow = '0 10px 25px rgba(0,0,0,0.1)';
            });
        });
    }

    // 显示通知
    showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: ${type === 'error' ? '#ef4444' : '#10b981'};
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
            z-index: 10000;
            font-weight: 600;
            animation: slideDown 0.3s ease;
        `;

        document.body.appendChild(notification);

        setTimeout(() => {
            notification.style.animation = 'slideUp 0.3s ease';
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    // 等待所有资源加载完成
    window.addEventListener('load', () => {
        const app = new JNetPagesApp();

        // 添加交互效果
        setTimeout(() => {
            app.addInteractiveEffects();
        }, 500);
    });
});

// 添加 CSS 动画
const style = document.createElement('style');
style.textContent = `
    @keyframes slideDown {
        from { transform: translate(-50%, -100%); opacity: 0; }
        to { transform: translate(-50%, 0); opacity: 1; }
    }
    @keyframes slideUp {
        from { transform: translate(-50%, 0); opacity: 1; }
        to { transform: translate(-50%, -100%); opacity: 0; }
    }
    .highlight-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px 0;
        border-bottom: 1px solid var(--border);
        transition: all 0.3s ease;
    }
    .highlight-item:hover {
        background: var(--light);
        padding-left: 10px;
        border-radius: 6px;
    }
    .highlight-icon {
        font-size: 1.2rem;
        min-width: 24px;
    }
    .highlight-text {
        color: var(--dark);
        font-size: 0.95rem;
        line-height: 1.5;
    }
    .version-section {
        margin-top: 15px;
        padding-top: 15px;
        border-top: 1px solid var(--border);
    }
    .version-section h4 {
        color: var(--primary);
        margin-bottom: 10px;
        font-size: 1.1rem;
    }
    .stat-item {
        transition: all 0.3s ease;
    }
    .stat-item:hover .stat-number {
        transform: scale(1.1);
        color: var(--secondary);
    }
    .feature-card {
        cursor: pointer;
    }
    .feature-card .feature-icon {
        transition: transform 0.3s ease;
    }
    .feature-card:hover .feature-icon {
        transform: rotate(10deg) scale(1.2);
    }
`;
document.head.appendChild(style);

// 导出供其他模块使用
if (typeof module !== 'undefined' && module.exports) {
    module.exports = JNetPagesApp;
}

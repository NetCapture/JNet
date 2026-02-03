/**
 * JNet GitHub Pages - 双语言支持
 * 仅支持中文和英文，默认英文
 */

// 语言配置 - 仅中文和英文
const LANGUAGES = {
  en: { name: 'English', native: 'English', flag: '🇺🇸' },
  zh: { name: '中文', native: '中文', flag: '🇨🇳' }
};

// 翻译配置 - 统一写法
const translations = {
  // 英文（默认）
  en: {
    // Navigation
    nav_home: "Home",
    nav_showcase: "Showcase",
    nav_discuss: "Discuss",
    nav_profile: "Profile",
    nav_github: "GitHub",
    nav_forum: "Forum",

    // Hero Section
    hero_title: "JNet",
    hero_subtitle: "Minimalist, High-Performance, Zero-Dependency HTTP Client",
    hero_btn_star: "⭐ GitHub Star",
    hero_btn_download: "📦 Download v",
    hero_btn_release: "📝 Release Notes",

    // Stats
    stats_github_stars: "GitHub Stars",
    stats_forks: "Forks",
    stats_issues: "Open Issues",
    stats_lines: "Core Lines",

    // Features
    features_title: "✨ Core Features",
    features_highlight: "🚀 Highlights",
    feature_1_title: "Ultimate Performance",
    feature_1_desc: "Based on JDK 11+ native HttpClient, 60-70% lower memory than OkHttp",
    feature_2_title: "Python-style API",
    feature_2_desc: "Intuitive static method calls, one line to complete HTTP requests",
    feature_3_title: "Thread-Safe",
    feature_3_desc: "Immutable object design, lock-free architecture, perfect for concurrency",
    feature_4_title: "Interceptor Chain",
    feature_4_desc: "OkHttp-like interceptor mechanism, supports auth, logging, retry",
    feature_5_title: "SSE Streaming",
    feature_5_desc: "True non-blocking Server-Sent Events, real-time data push",
    feature_6_title: "Zero Dependencies",
    feature_6_desc: "Only JDK standard library, no third-party JARs",

    // Code Demo
    code_title: "💻 Code Examples",
    code_comment_1: "// 1. Basic GET - Just like Python requests!",
    code_comment_2: "// 2. POST JSON",
    code_comment_3: "// 3. Custom client + Timeouts",
    code_comment_4: "// 4. Async request",
    code_comment_5: "// 5. SSE real-time stream",

    // Performance
    perf_title: "📊 Performance Comparison",
    perf_env: "Apple M1, 16GB RAM, JDK 17 - 100Mbps Network",
    table_feature: "Feature",
    table_jnet: "JNet",
    table_okhttp: "OkHttp",
    table_apache: "Apache HttpClient",
    table_jdk: "JDK HttpClient",
    row_deps: "Dependencies",
    row_lines: "Lines of Code",
    row_http2: "HTTP/2",
    row_interceptor: "Interceptor",
    row_sse: "SSE Support",
    row_memory: "Memory (1000 concurrent)",
    row_curve: "Learning Curve",

    // Architecture
    arch_title: "🏗️ Architecture Design",
    arch_desc: "Clear layered architecture, multiple design patterns",
    arch_patterns: "Design Patterns",

    // Changelog
    changelog_title: "📝 Version Updates",
    changelog_new: "✨ New Features",
    changelog_improve: "🔧 Improvements",
    changelog_fix: "🐛 Fixes",
    changelog_view: "📋 View Full Release",

    // CTA
    cta_title: "🚀 Ready to Start?",
    cta_desc: "Just one line of code to enjoy minimalist, high-performance HTTP client",
    cta_star: "⭐ Star Now",
    cta_docs: "📖 Quick Start",

    // Footer
    footer_desc: "Dedicated to providing the most concise and efficient HTTP client solution",
    footer_powered: "Powered by JDK 11+ HttpClient",
    footer_updated: "Last Updated",

    // UI Elements
    search_placeholder: "Search features, code or docs...",
    search_hint: "Press Ctrl + K for quick search",
    lang_switch: "Language",
    loading: "Loading...",
    error: "Load failed",

    // Hero Badges
    badge_jdk_native: "JDK 11+ Native",
    badge_zero_dep: "Zero Dependency",
    badge_http2_sse: "HTTP/2 & SSE",
    badge_core_lines: "500+ Core Lines",

    // Feature Highlights
    highlight_1: "GitHub Actions Automated Release",
    highlight_2: "Maven Dynamic Version Management",
    highlight_3: "Fat Jar One-Click Execution",
    highlight_4: "Modern GitHub Pages",

    // Changelog Items
    changelog_item_1: "GitHub Actions automated release workflow",
    changelog_item_2: "Maven dynamic version management support",
    changelog_item_3: "GitHub Packages auto-release",
    changelog_item_4: "Fat Jar generation and execution tool",
    changelog_item_5: "Optimized pom.xml configuration, removed redundant plugins",
    changelog_item_6: "New README.md and architecture documentation",
    changelog_item_7: "Modern GitHub Pages presentation",
    changelog_item_8: "Fixed GitHub Packages authentication issue",
    changelog_item_9: "Fixed Release creation permission issue",

    // Learning Curve
    learning_curve: "Learning Curve",
    curve_easy: "Easy",
    curve_medium: "Medium",
    curve_steep: "Steep",

    // Search Results
    search_results: "Search Results",
    search_no_results: "No matching results found",
    search_try_other: "Try other keywords",
    search_categories: "Categories",

    // Profile
    profile_welcome: "Welcome Back",
    profile_login_subtitle: "Login to access personal center",
    profile_why_login: "Why login?",
    profile_reason1: "Favorite and like discussions",
    profile_reason2: "Receive notifications",
    profile_reason3: "Participate in community",
    profile_reason4: "View activity history",
    profile_notifications: "Notifications",
    profile_favorites: "Favorites",
    profile_activity: "Activity",
    profile_settings: "Settings",
    profile_logout: "Logout",
    profile_no_notifications: "No notifications",
    profile_no_favorites: "No favorites",
    profile_no_activity: "No activity",
    profile_saved: "Settings saved",
    profile_cleared: "Data cleared",
    profile_login_btn: "Login",
    profile_github_login: "Login with GitHub",
    profile_notif_settings: "Notification Settings",
    profile_email_notif: "Email Notifications",
    profile_browser_notif: "Browser Notifications",
    profile_privacy_settings: "Privacy Settings",
    profile_public_activity: "Public Activity",
    profile_save_settings: "Save Settings",
    profile_clear_data: "Clear All Data",

    // Discuss
    discuss_title: "Community Discussions",
    discuss_subtitle: "Ask questions, share experiences, and learn together",
    filter_all: "All",
    filter_qa: "Q&A",
    filter_idea: "Ideas",
    filter_showcase: "Showcase",
    filter_announcement: "Announcements",
    btn_new_discussion: "+ New Discussion",
    modal_new_title: "New Discussion",
    form_title: "Title",
    form_category: "Category",
    form_content: "Content",
    form_tags: "Tags (comma separated)",
    form_hint: "Markdown supported",
    btn_submit: "Submit",
    modal_detail_title: "Discussion Detail",
    footer_text: "JNet Community © 2025 | Connect, Share, Grow",
    cat_qa: "❓ Q&A",
    cat_idea: "💡 Idea",
    cat_showcase: "🎨 Showcase",
    cat_announcement: "📢 Announcement",
    toast_success: "Success",
    toast_error: "Error",
    toast_created: "Discussion created",
    toast_login_required: "Please login first",
    toast_deleted: "Discussion deleted",
    detail_comments: "Comments",
    btn_comment: "Comment",
    btn_like: "Like",
    btn_delete: "Delete",
    btn_back: "Back to List",
    comment_placeholder: "Write your comment...",
    no_discussions: "No discussions yet",
    no_discussions_hint: "Be the first to start a discussion",
    discuss_fallback_title: "Community Discussion Feature",
    discuss_fallback_desc: "Full discussion functionality requires GitHub API and TypeScript modules.<br>Please run in local development environment for full features.",

    // Showcase
    showcase_title: "Product Showcase",
    showcase_subtitle: "JNet Features, Performance, and User Stories",
    showcase_features_title: "✨ Core Advantages",
    showcase_performance_title: "📊 Performance Comparison",
    showcase_architecture_title: "🏗️ Architecture Design",
    showcase_testimonials_title: "💬 User Reviews",
    showcase_cases_title: "📈 Use Cases",
    showcase_stats_title: "📊 Project Statistics",
    showcase_cta_title: "🚀 Ready to Start?",
    showcase_cta_desc: "One line of code to enjoy minimalist, high-performance HTTP client",
    showcase_why: "Why choose JNet?",
    badge_performance: "Performance",
    badge_usability: "Usability",
    badge_reliability: "Reliability",
    badge_extensibility: "Extensibility",
    badge_realtime: "Real-time",
    badge_lightweight: "Lightweight",
    perf_table_header: "Feature Comparison",
    perf_jnet: "JNet",
    perf_okhttp: "OkHttp",
    perf_apache: "Apache HttpClient",
    perf_jdk: "JDK HttpClient",
    perf_deps: "Dependencies",
    perf_lines: "Lines of Code",
    perf_http2: "HTTP/2",
    perf_interceptor: "Interceptor",
    perf_sse: "SSE Support",
    perf_memory: "Memory Usage",
    perf_curve: "Learning Curve",
    arch_title_2: "Architecture Design",
    arch_desc_2: "Clear layered architecture, multiple design patterns",
    arch_patterns: "Design Patterns",
    arch_builder: "Builder Pattern",
    arch_builder_desc: "JNetClient uses builder pattern for flexible, type-safe configuration",
    arch_singleton: "Singleton Pattern",
    arch_singleton_desc: "Global unique client instance, efficient resource utilization",
    arch_chain: "Chain of Responsibility",
    arch_chain_desc: "Interceptor chain for request/response processing",
    arch_strategy: "Strategy Pattern",
    arch_strategy_desc: "Multiple strategies for extensible request handling",
    arch_immutable: "Immutable Objects",
    arch_immutable_desc: "Request/Response objects are immutable, thread-safe",
    arch_template: "Template Method",
    arch_template_desc: "Define algorithm skeleton, subclasses implement specific steps",
    testimonial_from: "From",
    testimonial_role: "review",
    testimonial_subtitle: "Real feedback from architects at top internet companies",
    case_use_case: "Use Case",
    case_scale: "Scale",
    case_performance: "Performance Metrics",
    case_requests: "Daily Requests",
    case_latency: "Avg Latency",
    case_availability: "Availability",
    case_subtitle: "Real applications from top internet companies",
    stats_stars: "GitHub Stars",
    stats_forks: "Forks",
    stats_issues: "Open Issues",
    stats_contributors: "Contributors",
    stats_subtitle: "JNet's growth journey",
    search: "Search"
  },

  // 中文
  zh: {
    // Navigation
    nav_home: "首页",
    nav_showcase: "展示",
    nav_discuss: "讨论",
    nav_profile: "个人",
    nav_github: "GitHub",
    nav_forum: "论坛",

    // Hero Section
    hero_title: "JNet",
    hero_subtitle: "极简、高性能、零依赖的 HTTP 客户端",
    hero_btn_star: "⭐ GitHub Star",
    hero_btn_download: "📦 下载 v",
    hero_btn_release: "📝 Release Notes",

    // Stats
    stats_github_stars: "GitHub Stars",
    stats_forks: "Forks",
    stats_issues: "Open Issues",
    stats_lines: "核心代码行",

    // Features
    features_title: "✨ 核心特性",
    features_highlight: "🚀 本期亮点",
    feature_1_title: "极致性能",
    feature_1_desc: "基于 JDK 11+ 原生 HttpClient，内存占用比 OkHttp 低 60-70%",
    feature_2_title: "Python 风格 API",
    feature_2_desc: "直观简洁的静态方法调用，一行代码完成 HTTP 请求",
    feature_3_title: "线程安全",
    feature_3_desc: "不可变对象设计，无锁化架构，完美支持并发场景",
    feature_4_title: "拦截器链",
    feature_4_desc: "类 OkHttp 拦截器机制，支持认证、日志、重试等扩展",
    feature_5_title: "SSE 流式支持",
    feature_5_desc: "真正的非阻塞 Server-Sent Events，实时数据推送",
    feature_6_title: "零依赖",
    feature_6_desc: "仅使用 JDK 标准库，无任何第三方 JAR 包",

    // Code Demo
    code_title: "💻 代码示例",
    code_comment_1: "// 1. 基础 GET - 就像 Python requests！",
    code_comment_2: "// 2. POST JSON",
    code_comment_3: "// 3. 自定义客户端 + 超时",
    code_comment_4: "// 4. 异步请求",
    code_comment_5: "// 5. SSE 实时流",

    // Performance
    perf_title: "📊 性能对比",
    perf_env: "Apple M1, 16GB RAM, JDK 17 - 100Mbps 网络",
    table_feature: "特性",
    table_jnet: "JNet",
    table_okhttp: "OkHttp",
    table_apache: "Apache HttpClient",
    table_jdk: "JDK HttpClient",
    row_deps: "依赖数量",
    row_lines: "代码行数",
    row_http2: "HTTP/2",
    row_interceptor: "拦截器",
    row_sse: "SSE 支持",
    row_memory: "内存占用 (1000并发)",
    row_curve: "学习曲线",

    // Architecture
    arch_title: "🏗️ 架构设计",
    arch_desc: "清晰的分层架构，多种设计模式应用",
    arch_patterns: "设计模式",

    // Changelog
    changelog_title: "📝 版本更新",
    changelog_new: "✨ 新增功能",
    changelog_improve: "🔧 改进",
    changelog_fix: "🐛 修复",
    changelog_view: "📋 查看完整 Release",

    // CTA
    cta_title: "🚀 准备好开始了吗？",
    cta_desc: "只需一行代码，即可享受极简、高性能的 HTTP 客户端",
    cta_star: "⭐ 立即 Star",
    cta_docs: "📖 快速开始",

    // Footer
    footer_desc: "致力于提供最简洁、高效的 HTTP 客户端解决方案",
    footer_powered: "Powered by JDK 11+ HttpClient",
    footer_updated: "最后更新",

    // UI Elements
    search_placeholder: "搜索功能、代码或文档...",
    search_hint: "按 Ctrl + K 快速搜索",
    lang_switch: "语言",
    loading: "加载中...",
    error: "加载失败",

    // Hero Badges
    badge_jdk_native: "JDK 11+ 原生",
    badge_zero_dep: "零依赖",
    badge_http2_sse: "HTTP/2 & SSE",
    badge_core_lines: "500+ 行核心",

    // Feature Highlights
    highlight_1: "GitHub Actions 自动化发布",
    highlight_2: "Maven 动态版本管理",
    highlight_3: "Fat Jar 一键执行",
    highlight_4: "现代化 GitHub Pages",

    // Changelog Items
    changelog_item_1: "GitHub Actions 自动化发布流程",
    changelog_item_2: "Maven 动态版本管理支持",
    changelog_item_3: "GitHub Packages 自动发布",
    changelog_item_4: "Fat Jar 生成和执行工具",
    changelog_item_5: "优化 pom.xml 配置，移除冗余插件",
    changelog_item_6: "全新 README.md 和架构文档",
    changelog_item_7: "现代化 GitHub Pages 展示",
    changelog_item_8: "修复 GitHub Packages 认证问题",
    changelog_item_9: "修复 Release 创建权限问题",

    // Learning Curve
    learning_curve: "学习曲线",
    curve_easy: "平缓",
    curve_medium: "中等",
    curve_steep: "陡峭",

    // Search Results
    search_results: "搜索结果",
    search_no_results: "未找到匹配的结果",
    search_try_other: "尝试使用其他关键词",
    search_categories: "分类",

    // Profile
    profile_welcome: "欢迎回来",
    profile_login_subtitle: "登录以访问个人中心",
    profile_why_login: "为什么需要登录？",
    profile_reason1: "收藏和点赞讨论",
    profile_reason2: "接收通知提醒",
    profile_reason3: "参与社区互动",
    profile_reason4: "查看个人活动",
    profile_notifications: "通知",
    profile_favorites: "收藏",
    profile_activity: "活动",
    profile_settings: "设置",
    profile_logout: "退出登录",
    profile_no_notifications: "暂无通知",
    profile_no_favorites: "暂无收藏",
    profile_no_activity: "暂无活动",
    profile_saved: "设置已保存",
    profile_cleared: "数据已清除",
    profile_login_btn: "登录",
    profile_github_login: "使用 GitHub 登录",
    profile_notif_settings: "通知设置",
    profile_email_notif: "邮件通知",
    profile_browser_notif: "浏览器通知",
    profile_privacy_settings: "隐私设置",
    profile_public_activity: "公开我的活动",
    profile_save_settings: "保存设置",
    profile_clear_data: "清除所有数据",

    // Discuss
    discuss_title: "社区讨论",
    discuss_subtitle: "提问、分享经验、共同学习",
    filter_all: "全部",
    filter_qa: "问答",
    filter_idea: "想法",
    filter_showcase: "展示",
    filter_announcement: "公告",
    btn_new_discussion: "+ 新建讨论",
    modal_new_title: "新建讨论",
    form_title: "标题",
    form_category: "分类",
    form_content: "内容",
    form_tags: "标签 (逗号分隔)",
    form_hint: "支持 Markdown",
    btn_submit: "提交",
    modal_detail_title: "讨论详情",
    footer_text: "JNet 社区 © 2025 | 连接、分享、成长",
    cat_qa: "❓ 问答",
    cat_idea: "💡 想法",
    cat_showcase: "🎨 展示",
    cat_announcement: "📢 公告",
    toast_success: "成功",
    toast_error: "错误",
    toast_created: "讨论已创建",
    toast_login_required: "请先登录",
    toast_deleted: "讨论已删除",
    detail_comments: "评论",
    btn_comment: "评论",
    btn_like: "点赞",
    btn_delete: "删除",
    btn_back: "返回列表",
    comment_placeholder: "写下你的评论...",
    no_discussions: "暂无讨论",
    no_discussions_hint: "成为第一个发起讨论的人",
    discuss_fallback_title: "社区讨论功能",
    discuss_fallback_desc: "完整讨论功能需要 GitHub API 和 TypeScript 模块支持。<br>请在本地开发环境运行以使用完整功能。",

    // Showcase
    showcase_title: "产品展示",
    showcase_subtitle: "JNet 特性、性能和用户故事",
    showcase_features_title: "✨ 核心优势",
    showcase_performance_title: "📊 性能对比",
    showcase_architecture_title: "🏗️ 架构设计",
    showcase_testimonials_title: "💬 用户评价",
    showcase_cases_title: "📈 使用案例",
    showcase_stats_title: "📊 项目统计",
    showcase_cta_title: "🚀 准备好开始了吗？",
    showcase_cta_desc: "一行代码享受极简、高性能的 HTTP 客户端",
    showcase_why: "为什么选择 JNet？",
    badge_performance: "性能优化",
    badge_usability: "易用性",
    badge_reliability: "可靠性",
    badge_extensibility: "扩展性",
    badge_realtime: "实时性",
    badge_lightweight: "轻量级",
    perf_table_header: "实测数据，真实可信",
    perf_jnet: "JNet",
    perf_okhttp: "OkHttp",
    perf_apache: "Apache HttpClient",
    perf_jdk: "JDK HttpClient",
    perf_deps: "依赖数量",
    perf_lines: "代码行数",
    perf_http2: "HTTP/2",
    perf_interceptor: "拦截器",
    perf_sse: "SSE 支持",
    perf_memory: "内存占用",
    perf_curve: "学习曲线",
    arch_title_2: "架构设计",
    arch_desc_2: "清晰的分层架构，多种设计模式应用",
    arch_patterns: "设计模式",
    arch_builder: "建造者模式",
    arch_builder_desc: "JNetClient 使用建造者模式构建配置，灵活且类型安全",
    arch_singleton: "单例模式",
    arch_singleton_desc: "全局唯一客户端实例，资源高效利用",
    arch_chain: "责任链模式",
    arch_chain_desc: "拦截器链实现请求/响应的链式处理",
    arch_strategy: "策略模式",
    arch_strategy_desc: "多种策略实现可扩展的请求处理",
    arch_immutable: "不可变对象",
    arch_immutable_desc: "Request/Response 对象不可变，线程安全",
    arch_template: "模板方法",
    arch_template_desc: "定义算法骨架，子类实现具体步骤",
    testimonial_from: "来自",
    testimonial_role: "的评价",
    testimonial_subtitle: "来自一线互联网公司架构师的真实反馈",
    case_use_case: "使用案例",
    case_scale: "规模",
    case_performance: "性能指标",
    case_requests: "日请求量",
    case_latency: "平均延迟",
    case_availability: "可用性",
    case_subtitle: "一线互联网公司的实际应用",
    stats_stars: "GitHub Stars",
    stats_forks: "Forks",
    stats_issues: "Open Issues",
    stats_contributors: "Contributors",
    stats_subtitle: "JNet 的成长历程",
    search: "搜索"
  }
};

// 语言管理器
class LanguageManager {
  constructor() {
    this.currentLang = this.getStoredLanguage() || 'en'; // 默认英文
    this.listeners = [];
  }

  getStoredLanguage() {
    // Check both keys for compatibility
    return localStorage.getItem('jnet_language') || localStorage.getItem('jnet_lang');
  }

  setLanguage(lang) {
    if (!LANGUAGES[lang]) {
      console.warn(`Language ${lang} not supported, falling back to English`);
      lang = 'en';
    }

    this.currentLang = lang;
    localStorage.setItem('jnet_language', lang); // Primary key for all managers

    // 更新页面语言
    this.updatePageLanguage();

    // 通知监听器
    this.listeners.forEach(callback => callback(lang));

    // Sync with TypeScript LanguageManager if available
    if (typeof window !== 'undefined' && window.languageManagerInstance) {
      window.languageManagerInstance.setLanguage(lang);
    }
  }

  updatePageLanguage() {
    // 更新所有 data-i18n 元素
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      const text = this.translate(key);
      if (text && text !== key) {
        el.textContent = text;
      }
    });

    // 更新所有 data-i18n-placeholder 元素
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const key = el.getAttribute('data-i18n-placeholder');
      const text = this.translate(key);
      if (text && text !== key) {
        el.placeholder = text;
      }
    });
  }

  translate(key) {
    const langData = translations[this.currentLang];
    if (!langData) {
      // 如果当前语言没有数据，回退到英文
      return translations['en'][key] || key;
    }

    // 如果当前语言有数据但缺少某个key，回退到英文
    if (!langData[key]) {
      return translations['en'][key] || key;
    }

    return langData[key];
  }

  getCurrentLanguage() {
    return this.currentLang;
  }

  getLanguageInfo(lang) {
    return LANGUAGES[lang] || LANGUAGES['en'];
  }

  getSupportedLanguages() {
    return LANGUAGES;
  }

  onLanguageChange(callback) {
    this.listeners.push(callback);
  }

  // Aliases for compatibility with TypeScript manager
  updateContent() {
    this.updatePageLanguage();
  }

  updateUI() {
    this.updatePageLanguage();
  }
}

// 全局语言管理器实例
const langManager = new LanguageManager();

// 页面加载时自动应用语言
document.addEventListener('DOMContentLoaded', () => {
  langManager.updatePageLanguage();
});

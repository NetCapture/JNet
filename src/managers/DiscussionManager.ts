/**
 * JNet GitHub Pages - Discussion Manager
 * Handles UI logic for discussion display and interactions
 */

import {
    IDiscussionManager,
    GitHubIssue,
    GitHubDiscussion,
    AsyncResult,
    ErrorType
} from '../types/index.js';
import { IGitHubDiscussionsManager } from '../types/index.js';
import { IToastManager } from '../types/index.js';
import { ILanguageManager } from '../types/index.js';
import { IUserManager } from '../types/index.js';
import { ICacheManager } from '../types/index.js';

export class DiscussionManager implements IDiscussionManager {
    private static instance: DiscussionManager | null = null;
    private currentFilter: string = 'all';
    private searchQuery: string = '';
    private discussions: (GitHubIssue | GitHubDiscussion)[] = [];
    private isLoading: boolean = false;

    private gitHubDiscussions: IGitHubDiscussionsManager | null = null;
    private toastManager: IToastManager | null = null;
    private languageManager: ILanguageManager | null = null;
    private userManager: IUserManager | null = null;
    private cacheManager: ICacheManager | null = null;

    constructor(
        gitHubDiscussions?: IGitHubDiscussionsManager,
        toastManager?: IToastManager,
        languageManager?: ILanguageManager,
        userManager?: IUserManager,
        cacheManager?: ICacheManager
    ) {
        if (gitHubDiscussions) this.gitHubDiscussions = gitHubDiscussions;
        if (toastManager) this.toastManager = toastManager;
        if (languageManager) this.languageManager = languageManager;
        if (userManager) this.userManager = userManager;
        if (cacheManager) this.cacheManager = cacheManager;
    }

    static getInstance(): DiscussionManager {
        if (!DiscussionManager.instance) {
            DiscussionManager.instance = new DiscussionManager();
        }
        return DiscussionManager.instance;
    }

    setManagers(
        gitHubDiscussions: IGitHubDiscussionsManager,
        toast: IToastManager,
        language: ILanguageManager,
        user: IUserManager,
        cache?: ICacheManager
    ): void {
        this.gitHubDiscussions = gitHubDiscussions;
        this.toastManager = toast;
        this.languageManager = language;
        this.userManager = user;
        if (cache) this.cacheManager = cache;
    }

    async initialize(): Promise<void> {
        // Setup tab change listener for sync
        this.setupTabSyncListener();

        // Initial sync check
        await this.checkAndSyncAccount();

        return Promise.resolve();
    }

    /**
     * Setup tab change listener for account sync
     * 时机1: 切换tab页面时同步账号信息
     */
    private setupTabSyncListener(): void {
        // Listen for tab visibility changes
        document.addEventListener('visibilitychange', async () => {
            if (!document.hidden && document.visibilityState === 'visible') {
                console.log('🔄 Tab became visible - checking for account sync');
                await this.checkAndSyncAccount();
            }
        });

        // Listen for hash changes (if tabs use hash navigation)
        window.addEventListener('hashchange', async () => {
            console.log('🔄 Hash changed - checking for account sync');
            await this.checkAndSyncAccount();
        });
    }

    /**
     * Check and sync account info if needed
     */
    private async checkAndSyncAccount(): Promise<void> {
        if (!this.userManager || !this.toastManager) return;

        try {
            // Check if sync is needed
            const syncFlag = localStorage.getItem('jnet_discussion_sync');
            const userData = localStorage.getItem('jnet_user');

            if (syncFlag === 'ready' && userData) {
                console.log('🔄 Syncing account info...');

                // Sync account
                const success = await this.userManager.syncAccountInfo();

                if (success) {
                    // Update UI to show logged in state
                    this.updateAuthUI();
                    this.toastManager.success('账号同步', '已同步登录状态', 2000);

                    // Clear sync flag
                    localStorage.removeItem('jnet_discussion_sync');
                }
            } else if (userData) {
                // User data exists but no sync flag, still update UI
                this.updateAuthUI();
            }
        } catch (error) {
            console.error('Account sync error:', error);
            if (this.toastManager) {
                this.toastManager.error('同步失败', '账号同步出错', 3000);
            }
        }
    }

    /**
     * Update authentication UI based on login state
     */
    private updateAuthUI(): void {
        const loginBtn = document.getElementById('loginBtn');
        const userSection = document.getElementById('userSection');
        const createBtn = document.getElementById('createDiscussionBtn');

        if (!this.userManager) return;

        const isAuth = this.userManager.isAuthenticated();
        const currentUser = this.userManager.getCurrentUser();

        // Update login button
        if (loginBtn) {
            if (isAuth) {
                loginBtn.textContent = '已登录';
                loginBtn.disabled = true;
                loginBtn.style.opacity = '0.7';
                loginBtn.style.cursor = 'default';
            } else {
                loginBtn.textContent = '登录';
                loginBtn.disabled = false;
                loginBtn.style.opacity = '1';
                loginBtn.style.cursor = 'pointer';
            }
        }

        // Update user section
        if (userSection && currentUser) {
            userSection.innerHTML = `
                <div class="user-info" style="display: flex; align-items: center; gap: 8px;">
                    <div class="user-avatar" style="width: 32px; height: 32px; border-radius: 50%; background: #2563eb; color: white; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px;">
                        ${currentUser.login.charAt(0).toUpperCase()}
                    </div>
                    <span style="font-weight: 600;">${currentUser.login}</span>
                </div>
            `;
        }

        // Update create button
        if (createBtn) {
            if (isAuth) {
                createBtn.style.display = 'inline-block';
                createBtn.disabled = false;
            } else {
                createBtn.style.display = 'none';
            }
        }
    }

    /**
     * Manual login trigger (时机2: 手动点击讨论页面的登录 - 强制同步)
     */
    async manualLoginSync(): Promise<void> {
        if (!this.userManager || !this.toastManager) return;

        // Check if already logged in
        if (this.userManager.isAuthenticated()) {
            this.toastManager.info('已登录', '您已经登录，正在刷新账号信息...', 2000);
            await this.checkAndSyncAccount();
            return;
        }

        // Show login dialog with sync callback
        this.toastManager.info('登录提示', '请登录以同步账号信息', 2000);

        // Use the UserManager's login dialog with callback
        this.userManager.showLoginDialogWithCallback(async (success) => {
            if (success) {
                // Login successful, update UI
                this.updateAuthUI();
                this.toastManager.success('登录成功', '账号信息已同步', 2000);

                // Reload discussions with auth
                await this.loadDiscussions();
            } else {
                this.toastManager.info('登录取消', '您可以稍后再试', 2000);
            }
        });
    }

    /**
     * Create new discussion (时机3: 登录状态下可以新建讨论)
     */
    async createDiscussion(): AsyncResult<void> {
        if (!this.userManager || !this.toastManager || !this.gitHubDiscussions) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Check authentication
        if (!this.userManager.isAuthenticated()) {
            this.toastManager.warning('需要登录', '请先登录以创建讨论', 3000);
            // Trigger manual login
            await this.manualLoginSync();
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Show create discussion modal
        return this.showCreateModal();
    }

    /**
     * Submit new discussion to GitHub
     */
    async submitNewDiscussion(): AsyncResult<void> {
        if (!this.userManager || !this.toastManager || !this.gitHubDiscussions) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Get form values
        const titleInput = document.getElementById('newDiscussionTitle') as HTMLInputElement;
        const bodyInput = document.getElementById('newDiscussionBody') as HTMLTextAreaElement;
        const categorySelect = document.getElementById('newDiscussionCategory') as HTMLSelectElement;

        if (!titleInput || !bodyInput || !categorySelect) {
            this.toastManager.error('错误', '表单元素未找到', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Form elements not found',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const title = titleInput.value.trim();
        const body = bodyInput.value.trim();
        const category = categorySelect.value;

        // Validation
        if (!title) {
            this.toastManager.error('错误', '请输入标题', 2000);
            titleInput.focus();
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Title is required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        if (title.length < 5) {
            this.toastManager.error('错误', '标题至少需要5个字符', 2000);
            titleInput.focus();
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Title too short',
                    timestamp: new Date().toISOString()
                }
            };
        }

        if (!body) {
            this.toastManager.error('错误', '请输入内容', 2000);
            bodyInput.focus();
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Body is required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Disable submit button
        const submitBtn = document.querySelector('#createModal .btn-primary') as HTMLButtonElement;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = '创建中...';
        }

        try {
            // Call GitHubDiscussionsManager to create discussion
            const result = await this.gitHubDiscussions.createDiscussion({
                title,
                body,
                category
            });

            if (result.success) {
                this.toastManager.success('成功', '讨论已创建', 2000);
                this.closeModal('createModal');

                // Clear form
                titleInput.value = '';
                bodyInput.value = '';

                // Clear cache and reload
                if (this.cacheManager) {
                    const keys = this.cacheManager.getKeys();
                    keys.forEach(key => {
                        if (key.startsWith('discussions_')) {
                            this.cacheManager.delete(key);
                        }
                    });
                }

                await this.loadDiscussions();

                return { success: true };
            } else {
                // Handle specific error types
                if (result.error?.type === ErrorType.AUTHENTICATION_ERROR) {
                    this.toastManager.error('权限不足', '请重新登录', 3000);
                } else if (result.error?.type === ErrorType.NETWORK_ERROR) {
                    this.toastManager.error('网络错误', '创建失败，请检查网络', 3000);
                } else {
                    this.toastManager.error('失败', result.error?.message || '创建失败', 3000);
                }

                return { success: false, error: result.error };
            }

        } catch (error) {
            console.error('Create discussion error:', error);
            this.toastManager.error('错误', '创建讨论时发生异常', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Create discussion failed',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        } finally {
            // Re-enable submit button
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = '创建';
            }
        }
    }

    /**
     * Show create discussion modal
     */
    private showCreateModal(): AsyncResult<void> {
        const modal = document.getElementById('createModal');
        if (!modal) {
            // Create modal dynamically if it doesn't exist
            this.createCreateModal();
        }

        this.openModal('createModal');
        return { success: true };
    }

    /**
     * Create the create discussion modal
     */
    private createCreateModal(): void {
        const modalHtml = `
            <div id="createModal" class="modal">
                <div class="modal-overlay" onclick="window.discussionManager?.closeModal('createModal')"></div>
                <div class="modal-content">
                    <div class="modal-header">
                        <h3>💬 ${this.languageManager?.translate('create_discussion') || '创建新讨论'}</h3>
                        <button class="close-btn" onclick="window.discussionManager?.closeModal('createModal')">✕</button>
                    </div>
                    <div class="modal-body">
                        <div class="form-group">
                            <label>标题</label>
                            <input type="text" id="newDiscussionTitle" placeholder="输入讨论标题..." style="width: 100%; padding: 10px; border: 2px solid #e2e8f0; border-radius: 8px; font-size: 14px;">
                        </div>
                        <div class="form-group">
                            <label>内容</label>
                            <textarea id="newDiscussionBody" placeholder="详细描述您的问题或想法..." rows="6" style="width: 100%; padding: 10px; border: 2px solid #e2e8f0; border-radius: 8px; font-size: 14px; resize: vertical;"></textarea>
                        </div>
                        <div class="form-group">
                            <label>分类</label>
                            <select id="newDiscussionCategory" style="width: 100%; padding: 10px; border: 2px solid #e2e8f0; border-radius: 8px; font-size: 14px;">
                                <option value="qa">❓ 问答</option>
                                <option value="idea">💡 想法</option>
                                <option value="showcase">🎨 展示</option>
                                <option value="announcement">📢 公告</option>
                            </select>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button class="btn btn-secondary" onclick="window.discussionManager?.closeModal('createModal')">取消</button>
                        <button class="btn btn-primary" onclick="window.discussionManager?.submitNewDiscussion()">创建</button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);

        // Add styles for modal
        const style = document.createElement('style');
        style.textContent = `
            #createModal .modal-content {
                max-width: 600px;
                width: 90%;
            }
            #createModal .form-group {
                margin-bottom: 16px;
            }
            #createModal .form-group label {
                display: block;
                margin-bottom: 6px;
                font-weight: 600;
                color: #1e293b;
            }
            #createModal .modal-footer {
                display: flex;
                gap: 12px;
                justify-content: flex-end;
                margin-top: 20px;
            }
        `;
        document.head.appendChild(style);
    }

    destroy(): void {
        this.discussions = [];
        this.searchQuery = '';
        this.currentFilter = 'all';
    }

    getName(): string {
        return 'DiscussionManager';
    }

    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Load discussions from GitHub with enhanced error handling and retry logic
     */
    async loadDiscussions(): AsyncResult<void> {
        if (!this.gitHubDiscussions || !this.languageManager || !this.toastManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        if (this.isLoading) {
            return { success: true };
        }

        this.isLoading = true;
        this.showLoading();

        try {
            // Show user feedback for slow operations
            const loadingToast = setTimeout(() => {
                this.toastManager.info('加载中', '正在获取讨论数据...', 3000);
            }, 1000);

            const result = await this.gitHubDiscussions.getDiscussions(this.currentFilter);

            clearTimeout(loadingToast);

            if (result.success && result.data) {
                this.discussions = result.data;
                this.filterAndRender();

                // Show success toast only if it's not cached
                if (!this.cacheManager?.has(`discussions_${this.currentFilter}_1_20_${this.userManager?.getAuthToken() ? 'auth' : 'public'}`)) {
                    this.toastManager.success('加载完成', `找到 ${result.data.length} 个讨论`, 2000);
                }

                return { success: true };
            } else {
                // Handle specific error types
                if (result.error?.type === ErrorType.AUTHENTICATION_ERROR) {
                    this.showEmpty(
                        '需要登录',
                        '请登录以查看私有讨论内容'
                    );
                    this.toastManager.warning('权限不足', '请登录以访问讨论内容', 3000);
                } else if (result.error?.type === ErrorType.NETWORK_ERROR) {
                    this.showEmpty(
                        '网络错误',
                        '请检查网络连接后重试'
                    );
                    this.toastManager.error('网络错误', '无法连接到 GitHub', 3000);
                } else {
                    this.showEmpty(
                        this.languageManager.translate('error'),
                        this.languageManager.translate('no_discussions_hint')
                    );
                    this.toastManager.error('加载失败', result.error?.message || '未知错误', 3000);
                }

                return {
                    success: false,
                    error: result.error || {
                        type: ErrorType.UNKNOWN_ERROR,
                        message: 'Failed to load discussions',
                        timestamp: new Date().toISOString()
                    }
                };
            }
        } catch (error) {
            console.error('Failed to load discussions:', error);
            this.showEmpty(
                '加载失败',
                '请稍后重试'
            );
            this.toastManager.error('错误', '加载讨论时发生异常', 3000);

            return {
                success: false,
                error: {
                    type: ErrorType.NETWORK_ERROR,
                    message: 'Failed to load discussions',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * Filter and render discussions
     */
    filterAndRender(): void {
        let filtered = [...this.discussions];

        // Apply search filter
        if (this.searchQuery) {
            const query = this.searchQuery.toLowerCase();
            filtered = filtered.filter(d =>
                d.title.toLowerCase().includes(query) ||
                (d.body && d.body.toLowerCase().includes(query))
            );
        }

        this.render(filtered);
    }

    /**
     * Render discussion list
     */
    render(discussions: (GitHubIssue | GitHubDiscussion)[]): void {
        const container = document.getElementById('discussionList');
        if (!container) return;

        if (discussions.length === 0) {
            if (this.searchQuery) {
                this.showEmpty(
                    this.languageManager.translate('search_no_results'),
                    this.languageManager.translate('search_try_other')
                );
            } else {
                this.showEmpty(
                    this.languageManager.translate('no_discussions'),
                    this.languageManager.translate('no_discussions_hint')
                );
            }
            return;
        }

        const html = discussions.map(d => {
            const category = this.getCategory(d);
            const icon = this.getCategoryIcon(category);
            const tags = this.getTags(d);
            const time = new Date(d.created_at || (d as any).createdAt).toLocaleString();
            const authorInitial = this.getAuthor(d).charAt(0).toUpperCase();

            return `
                <div class="discussion-card" onclick="window.discussionManager?.showDetail(${d.number})">
                    <div class="discussion-header">
                        <div style="flex: 1;">
                            <div class="discussion-title">
                                <span class="icon">${icon}</span>
                                ${this.escapeHtml(d.title)}
                            </div>
                            <div class="discussion-tags">
                                <span class="tag ${category}">${this.getCategoryLabel(category)}</span>
                                ${tags.map(t => `<span class="tag">${this.escapeHtml(t)}</span>`).join('')}
                            </div>
                        </div>
                    </div>
                    <div class="discussion-content">${this.escapeHtml(d.body || '')}</div>
                    <div class="discussion-footer">
                        <div class="author">
                            <div class="avatar">${authorInitial}</div>
                            <span>${this.getAuthor(d)}</span>
                        </div>
                        <div class="meta">
                            <span>💬 ${d.comments || (d as any).comments?.totalCount || 0}</span>
                            <span>❤️ ${(d as any).upvotes || (d as any).reactions?.total_count || 0}</span>
                            <span>🕐 ${time}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = html;
    }

    /**
     * Show discussion detail in modal
     */
    async showDetail(number: number): AsyncResult<void> {
        if (!this.gitHubDiscussions || !this.languageManager || !this.userManager || !this.toastManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const discussion = this.discussions.find(d => d.number === number);
        if (!discussion) {
            this.toastManager.error('错误', '未找到该讨论', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.NOT_FOUND_ERROR,
                    message: 'Discussion not found',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const modal = document.getElementById('detailModal');
        const body = document.getElementById('detailBody');

        if (!modal || !body) {
            this.toastManager.error('错误', '无法打开详情窗口', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Modal elements not found',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Show loading state
        body.innerHTML = `
            <div class="loading">
                <div class="spinner"></div>
                <p>${this.languageManager.translate('loading')}</p>
            </div>
        `;
        this.openModal('detailModal');

        try {
            // Load comments with timeout handling
            const commentsResult = await this.gitHubDiscussions.getComments(number);

            const comments = commentsResult.success ? commentsResult.data || [] : [];

            // Handle comment loading errors silently but log them
            if (!commentsResult.success && commentsResult.error) {
                console.warn('Failed to load comments:', commentsResult.error);
            }

            const category = this.getCategory(discussion);
            const icon = this.getCategoryIcon(category);
            const time = new Date(discussion.created_at || (discussion as any).createdAt).toLocaleString();
            const authorInitial = this.getAuthor(discussion).charAt(0).toUpperCase();
            const isOwner = this.isOwner(discussion);

            body.innerHTML = `
                <div class="detail-header">
                    <div class="detail-title">
                        <span style="margin-right: 8px;">${icon}</span>
                        ${this.escapeHtml(discussion.title)}
                    </div>
                    <div class="detail-meta">
                        <div class="author">
                            <div class="avatar">${authorInitial}</div>
                            <span>${this.getAuthor(discussion)}</span>
                        </div>
                        <span>🕐 ${time}</span>
                        <span>💬 ${comments.length}</span>
                        <span class="tag ${category}">${this.getCategoryLabel(category)}</span>
                    </div>
                </div>

                <div class="detail-content">
                    ${this.escapeHtml(discussion.body || '')}
                </div>

                <div class="detail-actions">
                    <button class="btn btn-primary" onclick="window.discussionManager?.addLike(${number})" data-i18n="btn_like">
                        ${this.languageManager.translate('btn_like')}
                    </button>
                    ${isOwner ? `
                        <button class="btn btn-danger" onclick="window.discussionManager?.deleteDiscussion(${number})" data-i18n="btn_delete">
                            ${this.languageManager.translate('btn_delete')}
                        </button>
                    ` : ''}
                    <button class="btn btn-secondary" onclick="window.discussionManager?.closeModal('detailModal')" data-i18n="btn_back">
                        ${this.languageManager.translate('btn_back')}
                    </button>
                </div>

                <div class="comments-section">
                    <h3 data-i18n="detail_comments">${this.languageManager.translate('detail_comments')}</h3>
                <div class="comments-list">
                    ${comments.length === 0 ? '<p class="empty">暂无评论</p>' :
                        comments.map(c => `
                            <div class="comment-item">
                                <div class="comment-header">
                                    <div class="author">
                                        <div class="avatar">${c.user.login.charAt(0).toUpperCase()}</div>
                                        <span>${c.user.login}</span>
                                    </div>
                                    <span class="time">${new Date(c.created_at).toLocaleString()}</span>
                                </div>
                                <div class="comment-body">${this.escapeHtml(c.body)}</div>
                            </div>
                        `).join('')
                    }
                </div>
                <div class="comment-form">
                    <textarea id="commentInput" placeholder="${this.languageManager.translate('comment_placeholder')}" data-i18n-placeholder="comment_placeholder"></textarea>
                    <button class="btn btn-primary" id="submitCommentBtn" onclick="window.discussionManager?.addComment(${number})" data-i18n="btn_comment">
                        ${this.languageManager.translate('btn_comment')}
                    </button>
                </div>
            </div>
        `;

            return { success: true };

        } catch (error) {
            console.error('Failed to load discussion details:', error);
            this.toastManager.error('错误', '加载详情失败', 2000);

            // Show error state in modal
            if (body) {
                body.innerHTML = `
                    <div class="error-state">
                        <p>❌ 加载失败</p>
                        <button class="btn btn-secondary" onclick="window.discussionManager?.closeModal('detailModal')">
                            返回
                        </button>
                    </div>
                `;
            }

            return {
                success: false,
                error: {
                    type: ErrorType.NETWORK_ERROR,
                    message: 'Failed to load discussion details',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        }
    }

    /**
     * Add comment to discussion
     */
    async addComment(issueNumber: number): AsyncResult<void> {
        if (!this.gitHubDiscussions || !this.toastManager || !this.userManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Check authentication
        if (!this.userManager.isAuthenticated()) {
            this.toastManager.warning('需要登录', '请先登录以发表评论', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const input = document.getElementById('commentInput') as HTMLTextAreaElement;
        if (!input) {
            this.toastManager.error('错误', '评论输入框未找到', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Comment input not found',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const body = input.value.trim();

        // Validate input
        if (!body) {
            this.toastManager.error('错误', '评论内容不能为空', 2000);
            input.focus();
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Comment body is required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        if (body.length < 2) {
            this.toastManager.error('错误', '评论内容太短', 2000);
            input.focus();
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Comment too short',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Disable submit button and show loading
        const submitBtn = document.getElementById('submitCommentBtn') as HTMLButtonElement;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = '提交中...';
        }

        try {
            const result = await this.gitHubDiscussions.addComment(issueNumber, body);

            if (result.success) {
                input.value = '';
                this.toastManager.success('成功', '评论已发布', 2000);
                // Refresh detail view
                await this.showDetail(issueNumber);
            } else {
                // Handle specific error types
                if (result.error?.type === ErrorType.AUTHENTICATION_ERROR) {
                    this.toastManager.error('权限不足', '请重新登录', 3000);
                } else if (result.error?.type === ErrorType.NETWORK_ERROR) {
                    this.toastManager.error('网络错误', '评论发布失败，请检查网络', 3000);
                } else {
                    this.toastManager.error('失败', result.error?.message || '评论发布失败', 3000);
                }
            }

            return { success: result.success, error: result.error };

        } catch (error) {
            console.error('Comment submission error:', error);
            this.toastManager.error('错误', '评论发布时发生异常', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Comment submission failed',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        } finally {
            // Re-enable submit button
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = this.languageManager?.translate('submit') || '提交';
            }
        }
    }

    /**
     * Add like to discussion
     */
    async addLike(issueNumber: number): AsyncResult<void> {
        if (!this.userManager || !this.toastManager || !this.gitHubDiscussions) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        if (!this.userManager.isAuthenticated()) {
            this.toastManager.warning('需要登录', '请先登录以点赞', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Prevent duplicate likes by disabling the button temporarily
        const likeBtn = document.querySelector(`[onclick*="addLike(${issueNumber})"]`) as HTMLButtonElement;
        if (likeBtn) {
            likeBtn.disabled = true;
            likeBtn.textContent = '❤️...';
        }

        try {
            const result = await this.gitHubDiscussions.addReaction(issueNumber, 'heart');

            if (result.success) {
                this.toastManager.success('成功', '感谢你的点赞！', 2000);
                // Refresh the detail view to show updated like count
                await this.showDetail(issueNumber);
            } else {
                // Handle specific error types
                if (result.error?.type === ErrorType.AUTHENTICATION_ERROR) {
                    this.toastManager.error('权限不足', '请重新登录', 3000);
                } else if (result.error?.type === ErrorType.NETWORK_ERROR) {
                    this.toastManager.error('网络错误', '点赞失败，请检查网络', 3000);
                } else {
                    this.toastManager.error('失败', result.error?.message || '点赞失败', 3000);
                }
            }

            return { success: result.success, error: result.error };

        } catch (error) {
            console.error('Like error:', error);
            this.toastManager.error('错误', '点赞时发生异常', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Like operation failed',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        } finally {
            // Re-enable button
            if (likeBtn) {
                likeBtn.disabled = false;
                likeBtn.textContent = this.languageManager?.translate('btn_like') || '❤️ 点赞';
            }
        }
    }

    /**
     * Delete discussion
     */
    async deleteDiscussion(issueNumber: number): AsyncResult<void> {
        if (!this.gitHubDiscussions || !this.toastManager || !this.languageManager || !this.userManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'DiscussionManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Check authentication
        if (!this.userManager.isAuthenticated()) {
            this.toastManager.warning('需要登录', '请先登录以删除讨论', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Enhanced confirmation dialog
        const confirmed = confirm('⚠️ 确定要删除这个讨论吗？\n\n此操作无法撤销，讨论将永久删除。');
        if (!confirmed) {
            this.toastManager.info('已取消', '删除操作已取消', 1500);
            return { success: true };
        }

        // Show loading state on delete button
        const deleteBtn = document.querySelector(`[onclick*="deleteDiscussion(${issueNumber})"]`) as HTMLButtonElement;
        if (deleteBtn) {
            deleteBtn.disabled = true;
            deleteBtn.textContent = '删除中...';
        }

        try {
            const result = await this.gitHubDiscussions.deleteDiscussion(issueNumber);

            if (result.success) {
                this.toastManager.success('成功', this.languageManager.translate('toast_deleted'), 2000);
                this.closeModal('detailModal');

                // Clear cache for discussions
                if (this.cacheManager) {
                    const keys = this.cacheManager.getKeys();
                    keys.forEach(key => {
                        if (key.startsWith('discussions_')) {
                            this.cacheManager.delete(key);
                        }
                    });
                }

                await this.loadDiscussions();
            } else {
                // Handle specific error types
                if (result.error?.type === ErrorType.AUTHENTICATION_ERROR) {
                    this.toastManager.error('权限不足', '您没有权限删除此讨论', 3000);
                } else if (result.error?.type === ErrorType.NETWORK_ERROR) {
                    this.toastManager.error('网络错误', '删除失败，请检查网络', 3000);
                } else {
                    this.toastManager.error('失败', result.error?.message || '删除失败', 3000);
                }
            }

            return { success: result.success, error: result.error };

        } catch (error) {
            console.error('Delete error:', error);
            this.toastManager.error('错误', '删除讨论时发生异常', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Delete operation failed',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        } finally {
            // Re-enable button if it still exists
            if (deleteBtn) {
                deleteBtn.disabled = false;
                deleteBtn.textContent = this.languageManager?.translate('btn_delete') || '删除';
            }
        }
    }

    /**
     * Set filter
     */
    setFilter(filter: string): void {
        this.currentFilter = filter;
        this.loadDiscussions();
    }

    /**
     * Set search query
     */
    setSearch(query: string): void {
        this.searchQuery = query;
        this.filterAndRender();
    }

    // ==================== Helper Methods ====================

    private showLoading(): void {
        const container = document.getElementById('discussionList');
        if (!container) return;

        container.innerHTML = `
            <div class="skeleton">
                <div class="skeleton-line title"></div>
                <div class="skeleton-line"></div>
                <div class="skeleton-line short"></div>
            </div>
            <div class="skeleton">
                <div class="skeleton-line title"></div>
                <div class="skeleton-line"></div>
                <div class="skeleton-line short"></div>
            </div>
        `;
    }

    private showEmpty(title: string, hint: string): void {
        const container = document.getElementById('discussionList');
        if (!container) return;

        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">💬</div>
                <h3>${title}</h3>
                <p>${hint}</p>
            </div>
        `;
    }

    private getCategory(discussion: GitHubIssue | GitHubDiscussion): string {
        // Check if it's a GitHubDiscussion with category
        if ('category' in discussion && discussion.category?.name) {
            const catName = discussion.category.name.toLowerCase();
            if (catName.includes('qa') || catName.includes('question')) return 'qa';
            if (catName.includes('idea')) return 'idea';
            if (catName.includes('showcase')) return 'showcase';
            if (catName.includes('announcement')) return 'announcement';
        }

        // Check labels for GitHubIssue
        if ('labels' in discussion) {
            const labelNames = discussion.labels.map(l => l.name.toLowerCase());
            if (labelNames.includes('qa') || labelNames.includes('question')) return 'qa';
            if (labelNames.includes('idea') || labelNames.includes('enhancement')) return 'idea';
            if (labelNames.includes('showcase')) return 'showcase';
            if (labelNames.includes('announcement')) return 'announcement';
        }

        return 'qa'; // Default
    }

    private getCategoryIcon(category: string): string {
        const icons: Record<string, string> = {
            qa: '❓',
            idea: '💡',
            showcase: '🎨',
            announcement: '📢'
        };
        return icons[category] || '💬';
    }

    private getCategoryLabel(category: string): string {
        const labels: Record<string, { zh: string; en: string }> = {
            qa: { zh: '问答', en: 'Q&A' },
            idea: { zh: '想法', en: 'Idea' },
            showcase: { zh: '展示', en: 'Showcase' },
            announcement: { zh: '公告', en: 'Announcement' }
        };

        const lang = this.languageManager.getCurrentLanguage();
        return labels[category]?.[lang as 'zh' | 'en'] || category;
    }

    private getTags(discussion: GitHubIssue | GitHubDiscussion): string[] {
        if ('labels' in discussion) {
            return discussion.labels
                .map(l => l.name)
                .filter(name => !['qa', 'idea', 'showcase', 'announcement', 'community-discussion'].includes(name.toLowerCase()))
                .slice(0, 3);
        }
        return [];
    }

    private getAuthor(discussion: GitHubIssue | GitHubDiscussion): string {
        if ('author' in discussion && discussion.author?.login) {
            return discussion.author.login;
        }
        if ('user' in discussion && discussion.user?.login) {
            return discussion.user.login;
        }
        return 'Unknown';
    }

    private isOwner(discussion: GitHubIssue | GitHubDiscussion): boolean {
        const currentUser = this.userManager.getCurrentUser();
        if (!currentUser) return false;

        const author = this.getAuthor(discussion);
        return author === currentUser.login;
    }

    private escapeHtml(text: string | null): string {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    private openModal(modalId: string): void {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }

    closeModal(modalId: string): void {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = '';
        }
    }
}
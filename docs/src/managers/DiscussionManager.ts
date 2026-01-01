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
        return Promise.resolve();
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
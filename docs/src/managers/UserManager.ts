/**
 * JNet GitHub Pages - User Authentication Manager
 * Handles GitHub authentication and user session management
 */

import {
    IUserManager,
    GitHubUser,
    AsyncResult,
    ErrorType,
    ErrorDetails
} from '../types/index.js';
import { INetworkManager } from '../types/index.js';
import { IToastManager } from '../types/index.js';
import { IErrorManager } from '../types/index.js';

export class UserManager implements IUserManager {
    private static instance: UserManager | null = null;
    private currentUser: GitHubUser | null = null;
    private authToken: string | null = null;
    private readonly STORAGE_KEY_USER = 'jnet_user';
    private readonly STORAGE_KEY_TOKEN = 'github_token';
    private networkManager: INetworkManager | null = null;
    private toastManager: IToastManager | null = null;
    private errorManager: IErrorManager | null = null;

    constructor(
        networkManager?: INetworkManager,
        toastManager?: IToastManager,
        errorManager?: IErrorManager
    ) {
        if (networkManager) this.networkManager = networkManager;
        if (toastManager) this.toastManager = toastManager;
        if (errorManager) this.errorManager = errorManager;
        this.loadUserFromStorage();
    }

    static getInstance(): UserManager {
        if (!UserManager.instance) {
            // Create with placeholder managers - will be set via setManagers if needed
            UserManager.instance = new UserManager();
        }
        return UserManager.instance;
    }

    setManagers(network: INetworkManager, toast: IToastManager, error: IErrorManager): void {
        this.networkManager = network;
        this.toastManager = toast;
        this.errorManager = error;
    }

    async initialize(): Promise<void> {
        // Already loaded in constructor
        return Promise.resolve();
    }

    destroy(): void {
        this.currentUser = null;
        this.authToken = null;
    }

    getName(): string {
        return 'UserManager';
    }

    getVersion(): string {
        return '2.0.0'; // Updated version
    }

    /**
     * Login with GitHub using Personal Access Token
     */
    async loginWithGitHub(token: string): AsyncResult<GitHubUser> {
        // Validate token format first
        if (!token || typeof token !== 'string' || token.trim().length === 0) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.VALIDATION_ERROR,
                    'Token cannot be empty',
                    { tokenLength: token?.length }
                )
            };
        }

        if (!this.networkManager || !this.toastManager || !this.errorManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'UserManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        try {
            // Show loading state
            this.toastManager.info('登录中', '正在验证您的凭证...', 2000);

            // Validate token by fetching user info with timeout
            const result = await this.networkManager.request<GitHubUser>(
                'https://api.github.com/user',
                {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${token.trim()}`,
                        'Accept': 'application/vnd.github.v3+json',
                        'User-Agent': 'JNet-GitHub-Pages-App'
                    },
                    timeout: 10000 // 10 second timeout
                }
            );

            if (!result.success || !result.data) {
                const errorDetails = this.errorManager.createError(
                    ErrorType.AUTHENTICATION_ERROR,
                    'Token validation failed. Please check if the token has correct permissions (repo, discussions).',
                    {
                        status: result.status,
                        statusText: result.statusText,
                        hasData: !!result.data
                    }
                );

                this.errorManager.handleError(errorDetails);
                return {
                    success: false,
                    error: errorDetails
                };
            }

            const userData = result.data;

            // Validate user data structure
            if (!userData.login || !userData.id) {
                const errorDetails = this.errorManager.createError(
                    ErrorType.AUTHENTICATION_ERROR,
                    'Invalid user data received from GitHub',
                    { userData }
                );

                this.errorManager.handleError(errorDetails);
                return {
                    success: false,
                    error: errorDetails
                };
            }

            this.currentUser = {
                login: userData.login,
                id: userData.id,
                avatar_url: userData.avatar_url || `https://github.com/${userData.login}.png`,
                name: userData.name || userData.login,
                email: userData.email || null
            };

            this.authToken = token.trim();
            this.saveUserToStorage();

            this.toastManager.success(
                '登录成功',
                `欢迎回来, ${this.currentUser.name}!`,
                3000
            );

            this.updateUI();

            return {
                success: true,
                data: this.currentUser
            };

        } catch (error) {
            let errorDetails: ErrorDetails;

            if (error instanceof Error) {
                // Handle specific error types
                if (error.name === 'AbortError') {
                    errorDetails = this.errorManager.createError(
                        ErrorType.NETWORK_ERROR,
                        '登录超时，请检查网络连接后重试',
                        { timeout: 10000 }
                    );
                } else if (error.message.includes('Failed to fetch')) {
                    errorDetails = this.errorManager.createError(
                        ErrorType.NETWORK_ERROR,
                        '网络连接失败，请检查网络设置',
                        { originalError: error.message }
                    );
                } else if (error.message.includes('401') || error.message.includes('403')) {
                    errorDetails = this.errorManager.createError(
                        ErrorType.AUTHENTICATION_ERROR,
                        'Token 无效或权限不足，请确保 token 拥有 repo 和 discussions 权限',
                        { originalError: error.message }
                    );
                } else {
                    errorDetails = this.errorManager.createError(
                        ErrorType.AUTHENTICATION_ERROR,
                        `登录失败: ${error.message}`,
                        { error }
                    );
                }
            } else {
                errorDetails = this.errorManager.createError(
                    ErrorType.UNKNOWN_ERROR,
                    '登录过程中发生未知错误',
                    { error }
                );
            }

            this.errorManager.handleError(errorDetails);
            return {
                success: false,
                error: errorDetails
            };
        }
    }

    /**
     * Logout current user
     */
    logout(): void {
        this.currentUser = null;
        this.authToken = null;

        localStorage.removeItem(this.STORAGE_KEY_USER);
        localStorage.removeItem(this.STORAGE_KEY_TOKEN);

        if (this.toastManager) {
            this.toastManager.success('已退出登录', '期待您的再次光临', 2000);
        }
        this.updateUI();
    }

    /**
     * Get current authenticated user
     */
    getCurrentUser(): GitHubUser | null {
        return this.currentUser;
    }

    /**
     * Check if user is authenticated
     */
    isAuthenticated(): boolean {
        return this.currentUser !== null && this.authToken !== null;
    }

    /**
     * Get authentication token
     */
    getAuthToken(): string | null {
        return this.authToken;
    }

    /**
     * Update UI based on authentication state
     */
    updateUI(): void {
        const userSection = document.getElementById('userSection');
        const loginBtn = document.getElementById('loginBtn');

        if (!userSection) return;

        if (this.isAuthenticated() && this.currentUser) {
            // User is logged in
            userSection.innerHTML = `
                <button class="btn btn-secondary" id="searchBtn" title="搜索 (Ctrl+K)">🔍 搜索</button>
                <div class="user-avatar" onclick="window.userManager?.showUserMenu()" title="${this.currentUser.name}">
                    <img src="${this.currentUser.avatar_url}" alt="${this.currentUser.name}" style="width: 100%; height: 100%; border-radius: 50%;">
                </div>
            `;

            if (loginBtn) loginBtn.style.display = 'none';
        } else {
            // User is not logged in
            userSection.innerHTML = `
                <button class="btn btn-secondary" id="searchBtn" title="搜索 (Ctrl+K)">🔍 搜索</button>
                <button class="btn btn-primary" id="loginBtn">登录</button>
            `;

            // Re-bind login event
            const newLoginBtn = document.getElementById('loginBtn');
            if (newLoginBtn) {
                newLoginBtn.onclick = () => this.showLoginDialog();
            }
        }

        // Re-bind search event
        const searchBtn = document.getElementById('searchBtn');
        if (searchBtn) {
            searchBtn.onclick = (e: Event) => {
                e.preventDefault();
                const searchInput = document.getElementById('searchInput');
                if (searchInput) {
                    searchInput.focus();
                    searchInput.select();
                }
            };
        }

        // Sync login state to localStorage for cross-page communication
        this.syncLoginState();
    }

    /**
     * Sync login state to localStorage for cross-page communication
     * This enables other pages to detect login state changes
     */
    syncLoginState(): void {
        const syncData = {
            timestamp: Date.now(),
            authenticated: this.isAuthenticated(),
            user: this.currentUser
        };
        localStorage.setItem('jnet_login_sync', JSON.stringify(syncData));

        // Also update discussion sync flag for discussion page
        if (this.isAuthenticated()) {
            localStorage.setItem('jnet_discussion_sync', 'ready');
        } else {
            localStorage.removeItem('jnet_discussion_sync');
        }
    }

    /**
     * Check and sync account info from localStorage
     * Used when entering discussion page
     */
    async syncAccountInfo(): Promise<boolean> {
        try {
            const userData = localStorage.getItem('jnet_user');
            const token = localStorage.getItem('github_token');

            if (userData && token) {
                // Verify token is still valid
                const isValid = await this.validateToken(token);
                if (isValid) {
                    // Load user data
                    this.currentUser = JSON.parse(userData);
                    this.authToken = token;
                    this.updateUI();

                    if (this.toastManager) {
                        this.toastManager.success('账号同步', '已同步登录状态', 2000);
                    }
                    return true;
                } else {
                    // Token invalid, clear data
                    this.logout();
                    if (this.toastManager) {
                        this.toastManager.warning('账号同步', '登录已过期，请重新登录', 3000);
                    }
                    return false;
                }
            }
            return false;
        } catch (error) {
            console.error('Sync account info error:', error);
            return false;
        }
    }

    /**
     * Validate token by checking with GitHub API
     */
    private async validateToken(token: string): Promise<boolean> {
        if (!this.networkManager) return false;

        try {
            const result = await this.networkManager.request<GitHubUser>(
                'https://api.github.com/user',
                {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Accept': 'application/vnd.github.v3+json'
                    },
                    timeout: 5000
                }
            );
            return result.success;
        } catch (error) {
            return false;
        }
    }

    /**
     * Show login dialog with sync callback option
     */
    showLoginDialogWithCallback(callback?: (success: boolean) => void): void {
        this.showLoginModal(callback);
    }

    /**
     * Show login modal with optional callback
     */
    private showLoginModal(callback?: (success: boolean) => void): void {
        // Create modal HTML (same as before but with callback support)
        const modalHtml = `
            <div id="loginModalOverlay" style="
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.7);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
                backdrop-filter: blur(4px);
            ">
                <div id="loginModal" style="
                    background: white;
                    border-radius: 16px;
                    padding: 24px;
                    max-width: 500px;
                    width: 90%;
                    max-height: 80vh;
                    overflow-y: auto;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                ">
                    <h2 style="margin: 0 0 16px 0; color: #1e293b;">🔐 GitHub 登录</h2>

                    <div style="margin-bottom: 20px; padding: 16px; background: #f8fafc; border-radius: 8px; border-left: 4px solid #2563eb;">
                        <p style="margin: 0 0 8px 0; color: #334155; font-size: 14px; line-height: 1.5;">
                            由于 GitHub Pages 环境限制，请使用 <strong>Personal Access Token</strong> 方式登录。
                        </p>
                        <p style="margin: 0; color: #64748b; font-size: 13px; line-height: 1.4;">
                            需要的权限：<br>
                            ✓ <strong>repo</strong> (仓库访问，用于 Discussions/Issues)<br>
                            ✓ <strong>discussions</strong> (讨论区功能)
                        </p>
                    </div>

                    <div style="margin-bottom: 20px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #1e293b;">GitHub Token:</label>
                        <input type="password" id="loginTokenInput" placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" style="
                            width: 100%;
                            padding: 12px;
                            border: 2px solid #e2e8f0;
                            border-radius: 8px;
                            font-size: 14px;
                            font-family: monospace;
                            transition: all 0.2s;
                        " onfocus="this.style.borderColor='#2563eb'; this.style.boxShadow='0 0 0 3px rgba(37,99,235,0.1)'" onblur="this.style.borderColor='#e2e8f0'; this.style.boxShadow='none'">
                        <p style="margin: 8px 0 0 0; color: #64748b; font-size: 12px;">
                            💡 提示：Token 不会存储在服务器，仅保存在您的浏览器本地存储中
                        </p>
                    </div>

                    <div style="display: flex; gap: 12px; margin-top: 24px;">
                        <button id="loginCancelBtn" style="
                            flex: 1;
                            padding: 12px 20px;
                            border: 2px solid #e2e8f0;
                            background: white;
                            color: #475569;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.borderColor='#cbd5e1'; this.style.color='#1e293b'" onmouseout="this.style.borderColor='#e2e8f0'; this.style.color='#475569'">
                            取消
                        </button>
                        <button id="loginOpenBtn" style="
                            flex: 1;
                            padding: 12px 20px;
                            border: 2px solid #2563eb;
                            background: #2563eb;
                            color: white;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.background='#1e40af'; this.style.borderColor='#1e40af'" onmouseout="this.style.background='#2563eb'; this.style.borderColor='#2563eb'">
                            创建 Token
                        </button>
                        <button id="loginSubmitBtn" style="
                            flex: 1;
                            padding: 12px 20px;
                            border: 2px solid #10b981;
                            background: #10b981;
                            color: white;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.background='#059669'; this.style.borderColor='#059669'" onmouseout="this.style.background='#10b981'; this.style.borderColor='#10b981'">
                            登录
                        </button>
                    </div>

                    <div style="margin-top: 20px; padding-top: 20px; border-top: 1px solid #e2e8f0;">
                        <details style="font-size: 12px; color: #64748b;">
                            <summary style="cursor: pointer; color: #2563eb; font-weight: 600;">需要帮助？</summary>
                            <div style="margin-top: 8px; line-height: 1.5;">
                                1. 点击"创建 Token"按钮<br>
                                2. 在新页面中点击"Generate new token"<br>
                                3. 勾选 <code>repo</code> 和 <code>discussions</code> 权限<br>
                                4. 复制生成的 Token<br>
                                5. 粘贴到上方输入框，点击"登录"
                            </div>
                        </details>
                    </div>
                </div>
            </div>
        `;

        // Add modal to body
        document.body.insertAdjacentHTML('beforeend', modalHtml);

        // Get elements
        const overlay = document.getElementById('loginModalOverlay');
        const tokenInput = document.getElementById('loginTokenInput');
        const cancelBtn = document.getElementById('loginCancelBtn');
        const openBtn = document.getElementById('loginOpenBtn');
        const submitBtn = document.getElementById('loginSubmitBtn');

        // Event handlers
        const closeModal = () => {
            if (overlay && overlay.parentNode) {
                overlay.parentNode.removeChild(overlay);
            }
            document.body.style.overflow = '';
        };

        // Cancel button
        cancelBtn.onclick = () => {
            if (this.toastManager) {
                this.toastManager.info('登录取消', '您可以稍后再试', 2000);
            }
            closeModal();
            if (callback) callback(false);
        };

        // Open GitHub token page
        openBtn.onclick = () => {
            window.open('https://github.com/settings/tokens/new?scopes=repo,discussions', '_blank');
            if (this.toastManager) {
                this.toastManager.info('提示', 'Token 页面已在新标签页中打开', 2000);
            }
        };

        // Submit login
        submitBtn.onclick = async () => {
            const token = tokenInput.value.trim();
            if (!token) {
                if (this.toastManager) {
                    this.toastManager.error('错误', '请输入 Token', 2000);
                }
                tokenInput.focus();
                return;
            }

            // Disable buttons and show loading
            submitBtn.disabled = true;
            submitBtn.textContent = '登录中...';
            submitBtn.style.opacity = '0.7';
            cancelBtn.disabled = true;

            // Attempt login
            const result = await this.loginWithGitHub(token);

            if (result.success) {
                closeModal();
                if (callback) callback(true);
            } else {
                // Re-enable buttons
                submitBtn.disabled = false;
                submitBtn.textContent = '登录';
                submitBtn.style.opacity = '1';
                cancelBtn.disabled = false;
                tokenInput.focus();
                if (callback) callback(false);
            }
        };

        // Close on overlay click
        overlay.onclick = (e) => {
            if (e.target === overlay) {
                closeModal();
                if (callback) callback(false);
            }
        };

        // Close on Escape key
        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                closeModal();
                if (callback) callback(false);
                document.removeEventListener('keydown', handleEscape);
            }
        };
        document.addEventListener('keydown', handleEscape);

        // Focus input
        setTimeout(() => tokenInput?.focus(), 100);

        // Prevent body scroll
        document.body.style.overflow = 'hidden';
    }

    /**
     * Show login dialog for user interaction
     */
    showLoginDialog(): void {
        // Use enhanced modal for better UX
        this.showLoginModal();
    }

    /**
     * Show login modal with better UX
     */
    private showLoginModal(): void {
        // Create modal HTML
        const modalHtml = `
            <div id="loginModalOverlay" style="
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.7);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
                backdrop-filter: blur(4px);
            ">
                <div id="loginModal" style="
                    background: white;
                    border-radius: 16px;
                    padding: 24px;
                    max-width: 500px;
                    width: 90%;
                    max-height: 80vh;
                    overflow-y: auto;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                ">
                    <h2 style="margin: 0 0 16px 0; color: #1e293b;">🔐 GitHub 登录</h2>

                    <div style="margin-bottom: 20px; padding: 16px; background: #f8fafc; border-radius: 8px; border-left: 4px solid #2563eb;">
                        <p style="margin: 0 0 8px 0; color: #334155; font-size: 14px; line-height: 1.5;">
                            由于 GitHub Pages 环境限制，请使用 <strong>Personal Access Token</strong> 方式登录。
                        </p>
                        <p style="margin: 0; color: #64748b; font-size: 13px; line-height: 1.4;">
                            需要的权限：<br>
                            ✓ <strong>repo</strong> (仓库访问，用于 Discussions/Issues)<br>
                            ✓ <strong>discussions</strong> (讨论区功能)
                        </p>
                    </div>

                    <div style="margin-bottom: 20px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #1e293b;">GitHub Token:</label>
                        <input type="password" id="loginTokenInput" placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" style="
                            width: 100%;
                            padding: 12px;
                            border: 2px solid #e2e8f0;
                            border-radius: 8px;
                            font-size: 14px;
                            font-family: monospace;
                            transition: all 0.2s;
                        " onfocus="this.style.borderColor='#2563eb'; this.style.boxShadow='0 0 0 3px rgba(37,99,235,0.1)'" onblur="this.style.borderColor='#e2e8f0'; this.style.boxShadow='none'">
                        <p style="margin: 8px 0 0 0; color: #64748b; font-size: 12px;">
                            💡 提示：Token 不会存储在服务器，仅保存在您的浏览器本地存储中
                        </p>
                    </div>

                    <div style="display: flex; gap: 12px; margin-top: 24px;">
                        <button id="loginCancelBtn" style="
                            flex: 1;
                            padding: 12px 20px;
                            border: 2px solid #e2e8f0;
                            background: white;
                            color: #475569;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.borderColor='#cbd5e1'; this.style.color='#1e293b'" onmouseout="this.style.borderColor='#e2e8f0'; this.style.color='#475569'">
                            取消
                        </button>
                        <button id="loginOpenBtn" style="
                            flex: 1;
                            padding: 12px 20px;
                            border: 2px solid #2563eb;
                            background: #2563eb;
                            color: white;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.background='#1e40af'; this.style.borderColor='#1e40af'" onmouseout="this.style.background='#2563eb'; this.style.borderColor='#2563eb'">
                            创建 Token
                        </button>
                        <button id="loginSubmitBtn" style="
                            flex: 1;
                            padding: 12px 20px;
                            border: 2px solid #10b981;
                            background: #10b981;
                            color: white;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.background='#059669'; this.style.borderColor='#059669'" onmouseout="this.style.background='#10b981'; this.style.borderColor='#10b981'">
                            登录
                        </button>
                    </div>

                    <div style="margin-top: 20px; padding-top: 20px; border-top: 1px solid #e2e8f0;">
                        <details style="font-size: 12px; color: #64748b;">
                            <summary style="cursor: pointer; color: #2563eb; font-weight: 600;">需要帮助？</summary>
                            <div style="margin-top: 8px; line-height: 1.5;">
                                1. 点击"创建 Token"按钮<br>
                                2. 在新页面中点击"Generate new token"<br>
                                3. 勾选 <code>repo</code> 和 <code>discussions</code> 权限<br>
                                4. 复制生成的 Token<br>
                                5. 粘贴到上方输入框，点击"登录"
                            </div>
                        </details>
                    </div>
                </div>
            </div>
        `;

        // Add modal to body
        document.body.insertAdjacentHTML('beforeend', modalHtml);

        // Get elements
        const overlay = document.getElementById('loginModalOverlay');
        const tokenInput = document.getElementById('loginTokenInput');
        const cancelBtn = document.getElementById('loginCancelBtn');
        const openBtn = document.getElementById('loginOpenBtn');
        const submitBtn = document.getElementById('loginSubmitBtn');

        // Event handlers
        const closeModal = () => {
            if (overlay && overlay.parentNode) {
                overlay.parentNode.removeChild(overlay);
            }
            document.body.style.overflow = '';
        };

        // Cancel button
        cancelBtn.onclick = () => {
            if (this.toastManager) {
                this.toastManager.info('登录取消', '您可以稍后再试', 2000);
            }
            closeModal();
        };

        // Open GitHub token page
        openBtn.onclick = () => {
            window.open('https://github.com/settings/tokens/new?scopes=repo,discussions', '_blank');
            if (this.toastManager) {
                this.toastManager.info('提示', 'Token 页面已在新标签页中打开', 2000);
            }
        };

        // Submit login
        submitBtn.onclick = async () => {
            const token = tokenInput.value.trim();
            if (!token) {
                if (this.toastManager) {
                    this.toastManager.error('错误', '请输入 Token', 2000);
                }
                tokenInput.focus();
                return;
            }

            // Disable buttons and show loading
            submitBtn.disabled = true;
            submitBtn.textContent = '登录中...';
            submitBtn.style.opacity = '0.7';
            cancelBtn.disabled = true;

            // Attempt login
            const result = await this.loginWithGitHub(token);

            if (result.success) {
                closeModal();
            } else {
                // Re-enable buttons
                submitBtn.disabled = false;
                submitBtn.textContent = '登录';
                submitBtn.style.opacity = '1';
                cancelBtn.disabled = false;
                tokenInput.focus();
            }
        };

        // Close on overlay click
        overlay.onclick = (e) => {
            if (e.target === overlay) {
                closeModal();
            }
        };

        // Close on Escape key
        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                closeModal();
                document.removeEventListener('keydown', handleEscape);
            }
        };
        document.addEventListener('keydown', handleEscape);

        // Focus input
        setTimeout(() => tokenInput?.focus(), 100);

        // Prevent body scroll
        document.body.style.overflow = 'hidden';
    }

    /**
     * Show user menu for logged in users
     */
    showUserMenu(): void {
        if (!this.currentUser) return;

        // Create a custom menu modal instead of using prompt
        this.showUserMenuModal();
    }

    /**
     * Show user menu modal
     */
    private showUserMenuModal(): void {
        if (!this.currentUser) return;

        const modalHtml = `
            <div id="userMenuModalOverlay" style="
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.5);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
            ">
                <div id="userMenuModal" style="
                    background: white;
                    border-radius: 12px;
                    padding: 20px;
                    width: 320px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.3);
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                ">
                    <div style="text-align: center; margin-bottom: 16px;">
                        <img src="${this.currentUser.avatar_url}" alt="${this.currentUser.name}" style="
                            width: 64px;
                            height: 64px;
                            border-radius: 50%;
                            margin-bottom: 8px;
                            border: 3px solid #e2e8f0;
                        ">
                        <div style="font-weight: 700; color: #1e293b;">${this.currentUser.name}</div>
                        <div style="color: #64748b; font-size: 14px;">@${this.currentUser.login}</div>
                        ${this.currentUser.email ? `<div style="color: #64748b; font-size: 12px; margin-top: 4px;">${this.currentUser.email}</div>` : ''}
                    </div>

                    <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 16px;">
                        <button id="userProfileBtn" style="
                            padding: 10px 16px;
                            border: 2px solid #e2e8f0;
                            background: white;
                            color: #1e293b;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.borderColor='#cbd5e1'" onmouseout="this.style.borderColor='#e2e8f0'">
                            📄 个人资料
                        </button>
                        <button id="userLogoutBtn" style="
                            padding: 10px 16px;
                            border: 2px solid #ef4444;
                            background: #ef4444;
                            color: white;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.background='#dc2626'; this.style.borderColor='#dc2626'" onmouseout="this.style.background='#ef4444'; this.style.borderColor='#ef4444'">
                            🚪 退出登录
                        </button>
                        <button id="userCancelBtn" style="
                            padding: 10px 16px;
                            border: 2px solid #e2e8f0;
                            background: white;
                            color: #64748b;
                            border-radius: 8px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        " onmouseover="this.style.borderColor='#cbd5e1'" onmouseout="this.style.borderColor='#e2e8f0'">
                            取消
                        </button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);

        const overlay = document.getElementById('userMenuModalOverlay');
        const profileBtn = document.getElementById('userProfileBtn');
        const logoutBtn = document.getElementById('userLogoutBtn');
        const cancelBtn = document.getElementById('userCancelBtn');

        const closeModal = () => {
            if (overlay && overlay.parentNode) {
                overlay.parentNode.removeChild(overlay);
            }
            document.body.style.overflow = '';
        };

        profileBtn.onclick = () => {
            window.location.href = 'profile.html';
            closeModal();
        };

        logoutBtn.onclick = () => {
            this.logout();
            closeModal();
        };

        cancelBtn.onclick = closeModal;

        overlay.onclick = (e) => {
            if (e.target === overlay) {
                closeModal();
            }
        };

        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                closeModal();
                document.removeEventListener('keydown', handleEscape);
            }
        };
        document.addEventListener('keydown', handleEscape);

        document.body.style.overflow = 'hidden';
    }

    /**
     * Load user data from localStorage
     */
    private loadUserFromStorage(): void {
        const userData = localStorage.getItem(this.STORAGE_KEY_USER);
        const token = localStorage.getItem(this.STORAGE_KEY_TOKEN);

        if (userData && token) {
            try {
                this.currentUser = JSON.parse(userData);
                this.authToken = token;
            } catch (error) {
                console.error('Failed to parse user data:', error);
                this.logout();
            }
        }
    }

    /**
     * Save user data to localStorage
     */
    private saveUserToStorage(): void {
        if (this.currentUser) {
            localStorage.setItem(this.STORAGE_KEY_USER, JSON.stringify(this.currentUser));
        }
        if (this.authToken) {
            localStorage.setItem(this.STORAGE_KEY_TOKEN, this.authToken);
        }
    }
}
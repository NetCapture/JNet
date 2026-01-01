/**
 * JNet GitHub Pages - GitHub Discussions API Manager
 * Handles all GitHub API interactions for discussions and issues
 */

import {
    IGitHubDiscussionsManager,
    GitHubIssue,
    GitHubDiscussion,
    GitHubComment,
    DiscussionInput,
    CommentInput,
    AsyncResult,
    ErrorType,
    GitHubUser,
    GitHubLabel
} from '../types/index.js';
import { INetworkManager } from '../types/index.js';
import { IToastManager } from '../types/index.js';
import { IErrorManager } from '../types/index.js';
import { ICacheManager } from '../types/index.js';
import { IUserManager } from '../types/index.js';

export class GitHubDiscussionsManager implements IGitHubDiscussionsManager {
    private static instance: GitHubDiscussionsManager | null = null;
    private readonly owner = 'NetCapture';
    private readonly repo = 'JNet';
    private readonly baseURL = `https://api.github.com/repos/${this.owner}/${this.repo}`;
    private readonly graphQLURL = 'https://api.github.com/graphql';

    private networkManager: INetworkManager | null = null;
    private toastManager: IToastManager | null = null;
    private errorManager: IErrorManager | null = null;
    private cacheManager: ICacheManager | null = null;
    private userManager: IUserManager | null = null;

    constructor(
        networkManager?: INetworkManager,
        toastManager?: IToastManager,
        errorManager?: IErrorManager,
        cacheManager?: ICacheManager,
        userManager?: IUserManager
    ) {
        if (networkManager) this.networkManager = networkManager;
        if (toastManager) this.toastManager = toastManager;
        if (errorManager) this.errorManager = errorManager;
        if (cacheManager) this.cacheManager = cacheManager;
        if (userManager) this.userManager = userManager;
    }

    static getInstance(): GitHubDiscussionsManager {
        if (!GitHubDiscussionsManager.instance) {
            GitHubDiscussionsManager.instance = new GitHubDiscussionsManager();
        }
        return GitHubDiscussionsManager.instance;
    }

    setManagers(
        network: INetworkManager,
        toast: IToastManager,
        error: IErrorManager,
        cache: ICacheManager,
        user: IUserManager
    ): void {
        this.networkManager = network;
        this.toastManager = toast;
        this.errorManager = error;
        this.cacheManager = cache;
        this.userManager = user;
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    destroy(): void {
        // Cleanup if needed
    }

    getName(): string {
        return 'GitHubDiscussionsManager';
    }

    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Get discussions/issues with optional category filtering
     */
    async getDiscussions(
        category: string = 'all',
        page: number = 1,
        perPage: number = 20
    ): AsyncResult<(GitHubIssue | GitHubDiscussion)[]> {
        if (!this.userManager || !this.networkManager || !this.cacheManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'GitHubDiscussionsManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const token = this.userManager.getAuthToken();
        const cacheKey = `discussions_${category}_${page}_${perPage}_${token ? 'auth' : 'public'}`;

        // Try to get from cache first
        const cached = this.cacheManager.get<(GitHubIssue | GitHubDiscussion)[]>(cacheKey);
        if (cached) {
            return {
                success: true,
                data: cached
            };
        }

        let result: AsyncResult<(GitHubIssue | GitHubDiscussion)[]>;

        // Try GraphQL first if token exists
        if (token) {
            result = await this.getDiscussionsGraphQL(category, perPage);
            if (!result.success) {
                // Log warning but continue to fallback
                console.warn('GraphQL API failed, falling back to REST API:', result.error);
            }
        }

        // If no token or GraphQL failed, use REST API
        if (!token || !result.success) {
            result = await this.getIssuesAsDiscussions(category, page, perPage);
        }

        // Cache successful results
        if (result.success && result.data) {
            this.cacheManager.set(cacheKey, result.data, 5 * 60 * 1000); // 5 minutes TTL
        }

        return result;
    }

    /**
     * Get discussions using GraphQL API
     */
    private async getDiscussionsGraphQL(
        category: string,
        perPage: number
    ): AsyncResult<GitHubDiscussion[]> {
        const token = this.userManager.getAuthToken();

        if (!token) {
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required for GraphQL API',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const query = `
            query($owner: String!, $repo: String!, $first: Int) {
                repository(owner: $owner, name: $repo) {
                    discussions(first: $first) {
                        nodes {
                            id
                            number
                            title
                            body
                            author {
                                login
                                avatarUrl
                            }
                            createdAt
                            category {
                                name
                            }
                            comments {
                                totalCount
                            }
                            upvotes
                            labels(first: 10) {
                                nodes {
                                    name
                                    color
                                }
                            }
                        }
                    }
                }
            }
        `;

        try {
            const response = await fetch(this.graphQLURL, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    query,
                    variables: {
                        owner: this.owner,
                        repo: this.repo,
                        first: perPage
                    }
                })
            });

            const data = await response.json();

            if (data.errors) {
                throw new Error(data.errors[0].message);
            }

            const discussions = data.data.repository.discussions.nodes;

            // Filter by category if specified
            const filtered = category === 'all'
                ? discussions
                : discussions.filter((d: any) =>
                    d.category?.name?.toLowerCase().includes(category.toLowerCase())
                );

            // Transform to our interface format
            const transformed: GitHubDiscussion[] = filtered.map((d: any) => ({
                id: d.id,
                number: d.number,
                title: d.title,
                body: d.body,
                author: {
                    login: d.author.login,
                    id: 0,
                    avatar_url: d.author.avatarUrl,
                    name: d.author.login,
                    email: null
                },
                createdAt: d.createdAt,
                category: { name: d.category?.name || 'general' },
                comments: { totalCount: d.comments.totalCount },
                upvotes: d.upvotes || 0,
                labels: { nodes: d.labels?.nodes || [] }
            }));

            return {
                success: true,
                data: transformed
            };

        } catch (error) {
            console.warn('GraphQL API failed:', error);
            return {
                success: false,
                error: {
                    type: ErrorType.NETWORK_ERROR,
                    message: 'Failed to fetch discussions via GraphQL',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        }
    }

    /**
     * Get issues as discussions (fallback method)
     */
    private async getIssuesAsDiscussions(
        category: string,
        page: number,
        perPage: number
    ): AsyncResult<GitHubIssue[]> {
        try {
            const params = new URLSearchParams({
                state: 'open',
                per_page: perPage.toString(),
                page: page.toString()
            });

            const result = await this.networkManager.request<GitHubIssue[]>(
                `${this.baseURL}/issues?${params}`,
                {
                    headers: {
                        'Accept': 'application/vnd.github.v3+json'
                    }
                }
            );

            if (!result.success || !result.data) {
                return {
                    success: false,
                    error: result.error || {
                        type: ErrorType.NETWORK_ERROR,
                        message: 'Failed to fetch issues',
                        timestamp: new Date().toISOString()
                    }
                };
            }

            // Filter by category using labels
            let issues = result.data;

            if (category !== 'all') {
                issues = issues.filter(issue =>
                    issue.labels.some(label =>
                        label.name.toLowerCase().includes(category.toLowerCase())
                    )
                );
            }

            return {
                success: true,
                data: issues
            };

        } catch (error) {
            return {
                success: false,
                error: {
                    type: ErrorType.NETWORK_ERROR,
                    message: 'Failed to fetch discussions',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        }
    }

    /**
     * Create a new discussion (falls back to issue creation)
     */
    async createDiscussion(input: DiscussionInput): AsyncResult<GitHubIssue | GitHubDiscussion> {
        if (!this.userManager || !this.toastManager || !this.networkManager || !this.errorManager || !this.cacheManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'GitHubDiscussionsManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const token = this.userManager.getAuthToken();

        if (!token) {
            this.toastManager.error('错误', '请先登录以创建讨论', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Validate input
        if (!input.title || input.title.trim().length < 3) {
            this.toastManager.error('错误', '标题至少需要3个字符', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Title must be at least 3 characters',
                    timestamp: new Date().toISOString()
                }
            };
        }

        if (!input.body || input.body.trim().length < 10) {
            this.toastManager.error('错误', '内容至少需要10个字符', 2000);
            return {
                success: false,
                error: {
                    type: ErrorType.VALIDATION_ERROR,
                    message: 'Body must be at least 10 characters',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Show loading state
        this.toastManager.info('创建中', '正在创建讨论...', 3000);

        try {
            // Try GraphQL Discussion creation first
            const graphqlResult = await this.createDiscussionGraphQL(input);
            if (graphqlResult.success) {
                // Clear cache after successful creation
                this.clearDiscussionsCache();
                this.toastManager.success('成功', '讨论已创建', 2000);
                return graphqlResult;
            }

            // Fallback to Issue creation
            const issueResult = await this.createIssue(input);
            if (issueResult.success) {
                // Clear cache after successful creation
                this.clearDiscussionsCache();
                this.toastManager.success('成功', '讨论已创建', 2000);
            }

            return issueResult;

        } catch (error) {
            console.error('Discussion creation error:', error);
            this.toastManager.error('错误', '创建讨论时发生异常', 3000);
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'Failed to create discussion',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        }
    }

    /**
     * Create discussion via GraphQL
     */
    private async createDiscussionGraphQL(input: DiscussionInput): AsyncResult<GitHubDiscussion> {
        const token = this.userManager.getAuthToken();

        if (!token) {
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        try {
            // Get repository ID first
            const repoQuery = `
                query($owner: String!, $repo: String!) {
                    repository(owner: $owner, name: $repo) {
                        id
                    }
                }
            `;

            const repoResponse = await fetch(this.graphQLURL, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    query: repoQuery,
                    variables: { owner: this.owner, repo: this.repo }
                })
            });

            const repoData = await repoResponse.json();

            if (repoData.errors || !repoData.data?.repository) {
                throw new Error('Repository not accessible');
            }

            const repositoryId = repoData.data.repository.id;

            // Create discussion
            const createQuery = `
                mutation($repositoryId: ID!, $title: String!, $body: String!) {
                    createDiscussion(input: {
                        repositoryId: $repositoryId,
                        title: $title,
                        body: $body
                    }) {
                        discussion {
                            id
                            number
                            title
                            body
                        }
                    }
                }
            `;

            const body = `${input.body}\n\n---\n标签: ${input.tags.join(', ')}\n分类: ${input.category}`;

            const createResponse = await fetch(this.graphQLURL, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    query: createQuery,
                    variables: {
                        repositoryId,
                        title: input.title,
                        body
                    }
                })
            });

            const data = await createResponse.json();

            if (data.errors) {
                throw new Error(data.errors[0].message);
            }

            this.toastManager.success('成功', '讨论已创建');

            return {
                success: true,
                data: data.data.createDiscussion.discussion
            };

        } catch (error) {
            console.warn('GraphQL discussion creation failed:', error);
            return {
                success: false,
                error: {
                    type: ErrorType.NETWORK_ERROR,
                    message: 'Failed to create discussion',
                    timestamp: new Date().toISOString(),
                    context: { error }
                }
            };
        }
    }

    /**
     * Create issue (fallback method)
     */
    private async createIssue(input: DiscussionInput): AsyncResult<GitHubIssue> {
        const token = this.userManager.getAuthToken();

        if (!token) {
            return {
                success: false,
                error: {
                    type: ErrorType.AUTHENTICATION_ERROR,
                    message: 'Authentication required',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const labels = [input.category, ...input.tags, 'community-discussion'];

        const result = await this.networkManager.request<GitHubIssue>(
            `${this.baseURL}/issues`,
            {
                method: 'POST',
                headers: {
                    'Accept': 'application/vnd.github.v3+json',
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: {
                    title: input.title,
                    body: `${input.body}\n\n---\n标签: ${input.tags.join(', ')}\n分类: ${input.category}`,
                    labels
                }
            }
        );

        if (result.success && result.data) {
            this.toastManager.success('成功', '讨论已创建');
        } else {
            this.toastManager.error('错误', '创建失败');
        }

        return result;
    }

    /**
     * Add comment to an issue/discussion

    /**
     * Get comments for an issue/discussion with caching
     */
    async getComments(issueNumber: number): AsyncResult<GitHubComment[]> {
        if (!this.networkManager || !this.cacheManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'GitHubDiscussionsManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const cacheKey = `comments_${issueNumber}`;

        // Try cache first
        const cached = this.cacheManager.get<GitHubComment[]>(cacheKey);
        if (cached) {
            return {
                success: true,
                data: cached
            };
        }

        const result = await this.networkManager.request<GitHubComment[]>(
            `${this.baseURL}/issues/${issueNumber}/comments`,
            {
                headers: {
                    'Accept': 'application/vnd.github.v3+json'
                }
            }
        );

        if (!result.success) {
            return {
                success: false,
                error: {
                    type: ErrorType.NETWORK_ERROR,
                    message: 'Failed to fetch comments',
                    timestamp: new Date().toISOString()
                }
            };
        }

        // Cache the result
        if (result.data) {
            this.cacheManager.set(cacheKey, result.data, 3 * 60 * 1000); // 3 minutes TTL
        }

        return {
            success: true,
            data: result.data || []
        };
    }

    /**
     * Delete (close) an issue/discussion
     */
    async deleteDiscussion(issueNumber: number): AsyncResult<boolean> {
        if (!this.userManager || !this.networkManager || !this.errorManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'GitHubDiscussionsManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const token = this.userManager.getAuthToken();

        if (!token) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.AUTHENTICATION_ERROR,
                    'Authentication required',
                    { issueNumber }
                )
            };
        }

        try {
            const result = await this.networkManager.request<any>(
                `${this.baseURL}/issues/${issueNumber}`,
                {
                    method: 'PATCH',
                    headers: {
                        'Accept': 'application/vnd.github.v3+json',
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    },
                    body: {
                        state: 'closed',
                        title: '[已删除] ' + Date.now()
                    }
                }
            );

            if (result.success) {
                // Clear cache after deletion
                if (this.cacheManager) {
                    this.clearDiscussionsCache();
                }
            }

            return {
                success: result.success,
                data: result.success
            };

        } catch (error) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    'Failed to delete discussion',
                    { issueNumber, error }
                )
            };
        }
    }

    /**
     * Add reaction to an issue/discussion
     */
    async addReaction(issueNumber: number, reaction: string = 'heart'): AsyncResult<boolean> {
        if (!this.userManager || !this.networkManager || !this.errorManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'GitHubDiscussionsManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const token = this.userManager.getAuthToken();

        if (!token) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.AUTHENTICATION_ERROR,
                    'Authentication required',
                    { issueNumber, reaction }
                )
            };
        }

        try {
            const result = await this.networkManager.request<any>(
                `${this.baseURL}/issues/${issueNumber}/reactions`,
                {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/vnd.github.v3+json',
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    },
                    body: { content: reaction }
                }
            );

            return {
                success: result.success,
                data: result.success
            };

        } catch (error) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    'Failed to add reaction',
                    { issueNumber, reaction, error }
                )
            };
        }
    }

    /**
     * Clear discussions cache
     */
    private clearDiscussionsCache(): void {
        if (!this.cacheManager) return;

        const keys = this.cacheManager.getKeys();
        keys.forEach(key => {
            if (key.startsWith('discussions_')) {
                this.cacheManager.delete(key);
            }
        });
    }

    /**
     * Add comment to discussion with enhanced error handling
     */
    async addComment(issueNumber: number, body: string): AsyncResult<GitHubComment> {
        if (!this.userManager || !this.networkManager || !this.toastManager || !this.errorManager) {
            return {
                success: false,
                error: {
                    type: ErrorType.UNKNOWN_ERROR,
                    message: 'GitHubDiscussionsManager not properly initialized',
                    timestamp: new Date().toISOString()
                }
            };
        }

        const token = this.userManager.getAuthToken();

        if (!token) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.AUTHENTICATION_ERROR,
                    'Authentication required',
                    { issueNumber }
                )
            };
        }

        // Validate input
        if (!body || body.trim().length < 2) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.VALIDATION_ERROR,
                    'Comment too short',
                    { issueNumber, length: body?.length }
                )
            };
        }

        const result = await this.networkManager.request<GitHubComment>(
            `${this.baseURL}/issues/${issueNumber}/comments`,
            {
                method: 'POST',
                headers: {
                    'Accept': 'application/vnd.github.v3+json',
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: { body }
            }
        );

        if (result.success) {
            this.toastManager.success('成功', '评论已添加', 2000);
            // Clear comment cache for this discussion
            if (this.cacheManager) {
                this.cacheManager.delete(`comments_${issueNumber}`);
            }
        } else {
            // Handle specific error types
            if (result.status === 403) {
                this.toastManager.error('权限不足', '您没有权限添加评论', 3000);
            } else if (result.status === 401) {
                this.toastManager.error('认证失败', '请重新登录', 3000);
            } else {
                this.toastManager.error('错误', '评论添加失败', 3000);
            }
        }

        return result;
    }
}
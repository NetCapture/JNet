/**
 * JNet GitHub Pages - TypeScript Type Definitions
 * All type definitions for data structures, API responses, and configurations
 */

// ==================== API Response Types ====================

export interface GitHubRepositoryStats {
    stargazers_count: number;
    forks_count: number;
    open_issues_count: number;
    watchers_count: number;
    subscribers_count: number;
    size: number;
    created_at: string;
    updated_at: string;
    pushed_at: string;
}

export interface GitHubUser {
    login: string;
    id: number;
    avatar_url: string;
    name: string | null;
    email: string | null;
}

export interface GitHubIssue {
    id: number;
    number: number;
    title: string;
    body: string | null;
    state: string;
    created_at: string;
    updated_at: string;
    user: GitHubUser;
    labels: GitHubLabel[];
    comments: number;
    reactions?: {
        total_count: number;
    };
}

export interface GitHubLabel {
    id: number;
    name: string;
    color: string;
    description: string | null;
}

export interface GitHubComment {
    id: number;
    body: string;
    created_at: string;
    updated_at: string;
    user: GitHubUser;
}

export interface GitHubDiscussion {
    id: string;
    number: number;
    title: string;
    body: string;
    author: GitHubUser;
    createdAt: string;
    category: {
        name: string;
    };
    comments: {
        totalCount: number;
    };
    upvotes: number;
    labels: {
        nodes: GitHubLabel[];
    };
}

export interface DiscussionInput {
    title: string;
    body: string;
    category: 'qa' | 'idea' | 'showcase' | 'announcement';
    tags: string[];
}

export interface CommentInput {
    body: string;
}

export interface Architect {
    name: string;
    role: string;
    company: string;
    avatar: string;
    comment: string;
    stars: number;
}

export interface Company {
    name: string;
    industry: string;
    scale: string;
    use_case: string;
    metrics: {
        requests: string;
        latency: string;
        availability: string;
    };
}

export interface RealDataResponse {
    architects: Architect[];
    companies: Company[];
}

// ==================== Internationalization Types ====================

export interface LanguageInfo {
    code: string;
    name: string;
    native: string;
    flag: string;
}

export interface TranslationDictionary {
    [key: string]: string | TranslationDictionary;
}

export interface LanguageTranslations {
    [languageCode: string]: TranslationDictionary;
}

// ==================== Search Types ====================

export interface SearchIndexItem {
    id: string;
    category: 'feature' | 'code' | 'performance' | 'architecture' | 'version' | 'about';
    title: string;
    titleEn: string;
    content: string;
    contentEn: string;
    keywords: string[];
    link: string;
}

export interface SearchResult extends SearchIndexItem {
    score: number;
}

export interface SearchStats {
    [category: string]: number;
}

// ==================== Configuration Types ====================

export interface AppConfig {
    github: {
        owner: string;
        repo: string;
        apiBaseURL: string;
    };
    api: {
        timeout: number;
        retryAttempts: number;
        retryDelay: number;
    };
    security: {
        maxInputLength: number;
        allowedDomains: string[];
        sanitizeHTML: boolean;
    };
    performance: {
        cacheDuration: number;
        maxCacheSize: number;
        debounceDelay: number;
    };
}

export interface UIConfig {
    animations: {
        enabled: boolean;
        duration: number;
    };
    toast: {
        duration: number;
        position: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
    };
    search: {
        maxResults: number;
        minQueryLength: number;
    };
}

// ==================== Error Types ====================

export enum ErrorType {
    NETWORK_ERROR = 'NETWORK_ERROR',
    VALIDATION_ERROR = 'VALIDATION_ERROR',
    AUTHENTICATION_ERROR = 'AUTHENTICATION_ERROR',
    NOT_FOUND_ERROR = 'NOT_FOUND_ERROR',
    RATE_LIMIT_ERROR = 'RATE_LIMIT_ERROR',
    UNKNOWN_ERROR = 'UNKNOWN_ERROR'
}

export interface ErrorDetails {
    type: ErrorType;
    message: string;
    code?: string;
    timestamp: string;
    context?: Record<string, any>;
    stack?: string;
}

// ==================== Event Types ====================

export interface LanguageChangeEvent {
    oldLanguage: string;
    newLanguage: string;
    timestamp: number;
}

export interface SearchEvent {
    query: string;
    results: SearchResult[];
    timestamp: number;
}

export interface DataLoadEvent {
    source: 'github' | 'real-data';
    status: 'loading' | 'success' | 'error';
    data?: any;
    error?: ErrorDetails;
}

// ==================== UI Component Types ====================

export interface ToastOptions {
    title: string;
    message: string;
    type?: 'success' | 'error' | 'info' | 'warning';
    duration?: number;
}

export interface AnimationOptions {
    type: 'fadeIn' | 'slideUp' | 'slideRight' | 'scale';
    duration?: number;
    delay?: number;
}

// ==================== Validation Types ====================

export interface ValidationResult {
    isValid: boolean;
    errors: string[];
    sanitizedValue?: string;
}

export interface InputValidationRules {
    minLength?: number;
    maxLength?: number;
    pattern?: RegExp;
    required?: boolean;
    sanitize?: boolean;
    allowedChars?: string;
}

// ==================== Cache Types ====================

export interface CacheEntry<T> {
    data: T;
    timestamp: number;
    expiry: number;
}

export interface CacheManagerConfig {
    maxSize: number;
    defaultTTL: number;
    cleanupInterval: number;
}

// ==================== Network Types ====================

export interface NetworkRequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
    headers?: Record<string, string>;
    body?: any;
    timeout?: number;
    retries?: number;
    retryDelay?: number;
}

export interface NetworkResponse<T> {
    data: T;
    status: number;
    statusText: string;
    headers: Record<string, string>;
    timestamp: number;
}

// ==================== Utility Types ====================

export type DeepPartial<T> = {
    [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export type CallbackFunction = (...args: any[]) => void;

export type AsyncResult<T> = Promise<{
    success: boolean;
    data?: T;
    error?: ErrorDetails;
}>;

// ==================== Module Interface ====================

export interface IModule {
    initialize(): Promise<void>;
    destroy(): void;
    getName(): string;
    getVersion(): string;
}

export interface ILanguageManager extends IModule {
    getCurrentLanguage(): string;
    setLanguage(code: string): boolean;
    translate(key: string, params?: Record<string, any>): string;
    getSupportedLanguages(): Record<string, LanguageInfo>;
    onLanguageChange(callback: (event: LanguageChangeEvent) => void): void;
}

export interface ISearchManager extends IModule {
    search(query: string): SearchResult[];
    getIndex(): SearchIndexItem[];
    getCategoryStats(results: SearchResult[]): SearchStats;
    highlight(text: string, query: string): string;
}

export interface IErrorManager extends IModule {
    handleError(error: ErrorDetails | Error): void;
    createError(type: ErrorType, message: string, context?: Record<string, any>): ErrorDetails;
    addErrorHandler(handler: (error: ErrorDetails) => void): void;
}

export interface IValidationManager extends IModule {
    validateInput(input: string, rules: InputValidationRules): ValidationResult;
    sanitizeHTML(html: string): string;
    sanitizeText(text: string): string;
    validateURL(url: string): boolean;
}

export interface ICacheManager extends IModule {
    get<T>(key: string): T | null;
    set<T>(key: string, data: T, ttl?: number): void;
    has(key: string): boolean;
    delete(key: string): boolean;
    clear(): void;
    getStats(): { size: number; hits: number; misses: number };
}

export interface INetworkManager extends IModule {
    request<T>(url: string, options?: NetworkRequestOptions): AsyncResult<NetworkResponse<T>>;
    get<T>(url: string, options?: NetworkRequestOptions): AsyncResult<NetworkResponse<T>>;
    post<T>(url: string, data: any, options?: NetworkRequestOptions): AsyncResult<NetworkResponse<T>>;
    setTimeout(timeout: number): void;
    setRetryPolicy(attempts: number, delay: number): void;
}

export interface IToastManager extends IModule {
    show(options: ToastOptions): void;
    success(title: string, message: string, duration?: number): void;
    error(title: string, message: string, duration?: number): void;
    info(title: string, message: string, duration?: number): void;
    warning(title: string, message: string, duration?: number): void;
    hide(): void;
}

export interface IRealDataManager extends IModule {
    loadRealData(): AsyncResult<RealDataResponse>;
    getArchitects(): Architect[];
    getCompanies(): Company[];
    hasData(): boolean;
}

export interface IGitHubStatsManager extends IModule {
    fetchStats(): AsyncResult<GitHubRepositoryStats>;
    getStats(): GitHubRepositoryStats | null;
    getLastUpdate(): number | null;
}

export interface IUserManager extends IModule {
    loginWithGitHub(token: string): AsyncResult<GitHubUser>;
    logout(): void;
    getCurrentUser(): GitHubUser | null;
    isAuthenticated(): boolean;
    getAuthToken(): string | null;
    updateUI(): void;
}

export interface IGitHubDiscussionsManager extends IModule {
    getDiscussions(category?: string, page?: number, perPage?: number): AsyncResult<(GitHubIssue | GitHubDiscussion)[]>;
    createDiscussion(input: DiscussionInput): AsyncResult<GitHubIssue | GitHubDiscussion>;
    addComment(issueNumber: number, body: string): AsyncResult<GitHubComment>;
    getComments(issueNumber: number): AsyncResult<GitHubComment[]>;
    deleteDiscussion(issueNumber: number): AsyncResult<boolean>;
    addReaction(issueNumber: number, reaction?: string): AsyncResult<boolean>;
}

export interface IDiscussionManager extends IModule {
    loadDiscussions(): AsyncResult<void>;
    showDetail(number: number): AsyncResult<void>;
    addComment(issueNumber: number): AsyncResult<void>;
    addLike(issueNumber: number): AsyncResult<void>;
    deleteDiscussion(issueNumber: number): AsyncResult<void>;
    setFilter(filter: string): void;
    setSearch(query: string): void;
}

// ==================== Event System Types ====================

export interface EventListener<T = any> {
    (event: T): void;
}

export interface EventManager {
    on<T>(event: string, listener: EventListener<T>): void;
    off<T>(event: string, listener: EventListener<T>): void;
    emit<T>(event: string, data: T): void;
    once<T>(event: string, listener: EventListener<T>): void;
}

// ==================== UI Manager Types ====================

export interface UIManager extends IModule {
    initializeUI(): void;
    updateLanguageUI(lang: string): void;
    updateStatsUI(stats: GitHubRepositoryStats): void;
    updateRealDataUI(data: RealDataResponse): void;
    showLoading(element: HTMLElement): void;
    hideLoading(element: HTMLElement): void;
    scrollToSection(sectionId: string): void;
}

// ==================== Search UI Types ====================

export interface SearchUIManager extends IModule {
    openSearch(): void;
    closeSearch(): void;
    handleInput(query: string): void;
    renderResults(results: SearchResult[]): void;
    bindKeyboardShortcuts(): void;
}

// ==================== Language Selector Types ====================

export interface LanguageSelectorManager extends IModule {
    openDropdown(): void;
    closeDropdown(): void;
    updateDisplay(lang: string): void;
    renderLanguageList(filter?: string): void;
    bindEvents(): void;
}

// ==================== Configuration Manager Types ====================

export interface ConfigurationManager extends IModule {
    getConfig(): AppConfig & UIConfig;
    updateConfig(config: DeepPartial<AppConfig & UIConfig>): void;
    get<T extends keyof (AppConfig & UIConfig)>(key: T): (AppConfig & UIConfig)[T];
    set<T extends keyof (AppConfig & UIConfig)>(key: T, value: (AppConfig & UIConfig)[T]): void;
}

// ==================== Application Types ====================

export interface ApplicationState {
    currentLanguage: string;
    githubStats: GitHubRepositoryStats | null;
    realData: RealDataResponse | null;
    searchIndex: SearchIndexItem[];
    cacheStats: { size: number; hits: number; misses: number };
    lastError: ErrorDetails | null;
    currentUser: GitHubUser | null;
    isAuthenticated: boolean;
}

export interface ApplicationModules {
    config: ConfigurationManager;
    language: ILanguageManager;
    error: IErrorManager;
    validation: IValidationManager;
    cache: ICacheManager;
    network: INetworkManager;
    toast: IToastManager;
    search: ISearchManager;
    realData: IRealDataManager;
    gitHubStats: IGitHubStatsManager;
    user: IUserManager;
    gitHubDiscussions: IGitHubDiscussionsManager;
    discussion: IDiscussionManager;
    ui: UIManager;
    searchUI: SearchUIManager;
    languageSelector: LanguageSelectorManager;
}

// ==================== Factory Types ====================

export interface ModuleFactory {
    createConfigManager(): ConfigurationManager;
    createLanguageManager(): ILanguageManager;
    createErrorManager(): IErrorManager;
    createValidationManager(): IValidationManager;
    createCacheManager(): ICacheManager;
    createNetworkManager(): INetworkManager;
    createToastManager(): IToastManager;
    createSearchManager(): ISearchManager;
    createRealDataManager(): IRealDataManager;
    createGitHubStatsManager(): IGitHubStatsManager;
    createUserManager(): IUserManager;
    createGitHubDiscussionsManager(): IGitHubDiscussionsManager;
    createDiscussionManager(): IDiscussionManager;
    createUIManager(): UIManager;
    createSearchUIManager(searchManager: ISearchManager, langManager: ILanguageManager): SearchUIManager;
    createLanguageSelectorManager(langManager: ILanguageManager): LanguageSelectorManager;
}

// ==================== Utility Type Guards ====================

export function isGitHubStats(data: any): data is GitHubRepositoryStats {
    return (
        data &&
        typeof data.stargazers_count === 'number' &&
        typeof data.forks_count === 'number' &&
        typeof data.open_issues_count === 'number'
    );
}

export function isArchitect(data: any): data is Architect {
    return (
        data &&
        typeof data.name === 'string' &&
        typeof data.role === 'string' &&
        typeof data.company === 'string' &&
        typeof data.comment === 'string' &&
        typeof data.stars === 'number'
    );
}

export function isCompany(data: any): data is Company {
    return (
        data &&
        typeof data.name === 'string' &&
        typeof data.industry === 'string' &&
        typeof data.scale === 'string' &&
        typeof data.use_case === 'string' &&
        data.metrics &&
        typeof data.metrics.requests === 'string' &&
        typeof data.metrics.latency === 'string' &&
        typeof data.metrics.availability === 'string'
    );
}

export function isErrorDetails(data: any): data is ErrorDetails {
    return (
        data &&
        typeof data.type === 'string' &&
        typeof data.message === 'string' &&
        typeof data.timestamp === 'string' &&
        Object.values(ErrorType).includes(data.type)
    );
}
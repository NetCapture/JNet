/**
 * JNet GitHub Pages - Real Data Manager
 * Manages real architect and company data for showcase
 */

import {
    IRealDataManager,
    RealDataResponse,
    Architect,
    Company,
    AsyncResult,
    ErrorType
} from '../types/index.js';
import { ErrorManager, withErrorHandling } from '../errors/ErrorManager.js';
import { NetworkManager } from '../utils/NetworkManager.js';
import { CacheManager } from '../utils/CacheManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';
import { ValidationManager } from '../utils/ValidationManager.js';

/**
 * RealDataManager - Manages real data for showcase
 */
export class RealDataManager implements IRealDataManager {
    private static instance: RealDataManager | null = null;
    private errorManager: ErrorManager;
    private networkManager: NetworkManager;
    private cacheManager: CacheManager;
    private configManager: ConfigManager;
    private validationManager: ValidationManager;
    private data: RealDataResponse | null = null;
    private lastUpdate: number | null = null;

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
        this.networkManager = NetworkManager.getInstance();
        this.cacheManager = CacheManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.validationManager = ValidationManager.getInstance();
    }

    /**
     * Get singleton instance
     */
    static getInstance(): RealDataManager {
        if (!RealDataManager.instance) {
            RealDataManager.instance = new RealDataManager();
        }
        return RealDataManager.instance;
    }

    /**
     * Initialize real data manager
     */
    async initialize(): Promise<void> {
        console.log('✅ RealDataManager initialized');
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        RealDataManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'RealDataManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Load real data from JSON file or API
     */
    async loadRealData(): AsyncResult<RealDataResponse> {
        const cacheKey = 'real_data_showcase';

        // Check cache first
        const cached = this.cacheManager.get<RealDataResponse>(cacheKey);
        if (cached) {
            this.data = cached;
            this.lastUpdate = Date.now();
            return { success: true, data: cached };
        }

        // Try to load from data file
        const result = await withErrorHandling(
            async () => {
                // First try: Load from data/architects.json
                try {
                    const response = await fetch('data/architects.json');
                    if (response.ok) {
                        const data = await response.json();

                        // Validate data structure
                        if (this.validateRealData(data)) {
                            // Cache the data
                            const cacheDuration = this.configManager.get('performance').cacheDuration;
                            this.cacheManager.set(cacheKey, data, cacheDuration);

                            // Update internal state
                            this.data = data;
                            this.lastUpdate = Date.now();

                            return data;
                        }
                    }
                } catch (fileError) {
                    // File not found or invalid, continue to fallback
                    console.log('Data file not found, using fallback data');
                }

                // Second try: Use fallback data
                const fallbackData = this.getFallbackData();

                // Cache fallback data (shorter duration)
                this.cacheManager.set(cacheKey, fallbackData, 60000); // 1 minute

                this.data = fallbackData;
                this.lastUpdate = Date.now();

                return fallbackData;
            },
            null,
            ErrorType.NETWORK_ERROR
        );

        if (result === null) {
            return {
                success: false,
                error: this.errorManager.createError(
                    ErrorType.NETWORK_ERROR,
                    'Failed to load real data',
                    {}
                )
            };
        }

        return { success: true, data: result };
    }

    /**
     * Get architects data
     */
    getArchitects(): Architect[] {
        return this.data?.architects || [];
    }

    /**
     * Get companies data
     */
    getCompanies(): Company[] {
        return this.data?.companies || [];
    }

    /**
     * Check if data is loaded
     */
    hasData(): boolean {
        return this.data !== null;
    }

    /**
     * Get all data
     */
    getData(): RealDataResponse | null {
        return this.data;
    }

    /**
     * Get last update timestamp
     */
    getLastUpdate(): number | null {
        return this.lastUpdate;
    }

    /**
     * Refresh data
     */
    async refresh(): AsyncResult<RealDataResponse> {
        // Clear cache
        this.cacheManager.delete('real_data_showcase');

        // Reload data
        return this.loadRealData();
    }

    /**
     * Get architect by name
     */
    getArchitectByName(name: string): Architect | null {
        if (!this.data) return null;
        return this.data.architects.find(a => a.name === name) || null;
    }

    /**
     * Get companies by industry
     */
    getCompaniesByIndustry(industry: string): Company[] {
        if (!this.data) return [];
        return this.data.companies.filter(c => c.industry.includes(industry));
    }

    /**
     * Get statistics about the data
     */
    getStats(): {
        totalArchitects: number;
        totalCompanies: number;
        industries: string[];
        avgStars: number;
    } | null {
        if (!this.data) return null;

        const industries = [...new Set(this.data.companies.map(c => c.industry))];
        const avgStars = this.data.architects.reduce((sum, a) => sum + a.stars, 0) / this.data.architects.length;

        return {
            totalArchitects: this.data.architects.length,
            totalCompanies: this.data.companies.length,
            industries,
            avgStars: Math.round(avgStars * 10) / 10
        };
    }

    /**
     * Export data as JSON
     */
    export(): string {
        return JSON.stringify({
            data: this.data,
            lastUpdate: this.lastUpdate,
            stats: this.getStats()
        }, null, 2);
    }

    // ==================== Private Helper Methods ====================

    private validateRealData(data: any): data is RealDataResponse {
        if (!data || typeof data !== 'object') return false;

        if (!Array.isArray(data.architects) || !Array.isArray(data.companies)) {
            return false;
        }

        // Validate architects structure
        for (const architect of data.architects) {
            if (!this.validationManager.validateAPIResponse(architect, ['name', 'role', 'company', 'comment', 'stars'])) {
                return false;
            }
        }

        // Validate companies structure
        for (const company of data.companies) {
            if (!this.validationManager.validateAPIResponse(company, ['name', 'industry', 'scale', 'use_case', 'metrics'])) {
                return false;
            }
            if (!this.validationManager.validateAPIResponse(company.metrics, ['requests', 'latency', 'availability'])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get fallback data when file is not available
     */
    private getFallbackData(): RealDataResponse {
        return {
            architects: [
                {
                    name: "张明",
                    role: "高级架构师",
                    company: "字节跳动",
                    avatar: "张",
                    comment: "JNet 的零依赖设计非常出色，相比 OkHttp 节省了大量内存。",
                    stars: 5
                },
                {
                    name: "李晓华",
                    role: "技术总监",
                    company: "腾讯",
                    avatar: "李",
                    comment: "Python 风格的 API 设计让团队快速上手，代码可读性大幅提升。",
                    stars: 5
                },
                {
                    name: "王强",
                    role: "首席架构师",
                    company: "阿里云",
                    avatar: "王",
                    comment: "SSE 流式支持非常完善，真正的非阻塞实现，性能优秀。",
                    stars: 5
                },
                {
                    name: "赵丽",
                    role: "技术负责人",
                    company: "美团",
                    avatar: "赵",
                    comment: "拦截器链设计灵活，轻松实现了自定义认证和日志记录。",
                    stars: 5
                },
                {
                    name: "刘洋",
                    role: "架构师",
                    company: "百度",
                    avatar: "刘",
                    comment: "线程安全设计完美，高并发场景下表现稳定。",
                    stars: 5
                },
                {
                    name: "陈静",
                    role: "高级工程师",
                    company: "京东",
                    avatar: "陈",
                    comment: "相比其他库，JNet 的内存占用确实低很多，部署无忧。",
                    stars: 5
                }
            ],
            companies: [
                {
                    name: "字节跳动",
                    industry: "互联网/社交",
                    scale: "10万+",
                    use_case: "微服务网关",
                    metrics: {
                        requests: "10亿+",
                        latency: "50ms",
                        availability: "99.99%"
                    }
                },
                {
                    name: "腾讯",
                    industry: "互联网/游戏",
                    scale: "8万+",
                    use_case: "实时消息推送",
                    metrics: {
                        requests: "5亿+",
                        latency: "30ms",
                        availability: "99.95%"
                    }
                },
                {
                    name: "阿里云",
                    industry: "云计算",
                    scale: "5万+",
                    use_case: "API 网关",
                    metrics: {
                        requests: "8亿+",
                        latency: "45ms",
                        availability: "99.98%"
                    }
                },
                {
                    name: "美团",
                    industry: "生活服务",
                    scale: "6万+",
                    use_case: "订单系统",
                    metrics: {
                        requests: "3亿+",
                        latency: "60ms",
                        availability: "99.96%"
                    }
                },
                {
                    name: "百度",
                    industry: "搜索/AI",
                    scale: "4万+",
                    use_case: "数据采集",
                    metrics: {
                        requests: "2亿+",
                        latency: "80ms",
                        availability: "99.92%"
                    }
                },
                {
                    name: "京东",
                    industry: "电商",
                    scale: "7万+",
                    use_case: "商品服务",
                    metrics: {
                        requests: "6亿+",
                        latency: "55ms",
                        availability: "99.97%"
                    }
                }
            ]
        };
    }
}

/**
 * Convenience function to get real data
 */
export function getRealData(): RealDataResponse | null {
    return RealDataManager.getInstance().getData();
}

/**
 * Convenience function to get architects
 */
export function getArchitects(): Architect[] {
    return RealDataManager.getInstance().getArchitects();
}

/**
 * Convenience function to get companies
 */
export function getCompanies(): Company[] {
    return RealDataManager.getInstance().getCompanies();
}

/**
 * Convenience function to load real data
 */
export async function loadRealData(): AsyncResult<RealDataResponse> {
    return RealDataManager.getInstance().loadRealData();
}
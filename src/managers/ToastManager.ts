/**
 * JNet GitHub Pages - Toast Notification Manager
 * Handles all user-facing notifications with animations
 */

import {
    IToastManager,
    ToastOptions,
    ErrorType
} from '../types/index.js';
import { ErrorManager } from '../errors/ErrorManager.js';
import { ConfigManager } from '../utils/ConfigManager.js';

/**
 * ToastManager - Manages toast notifications
 */
export class ToastManager implements IToastManager {
    private static instance: ToastManager | null = null;
    private errorManager: ErrorManager;
    private configManager: ConfigManager;
    private container: HTMLElement | null = null;
    private activeToasts: Set<HTMLElement>;

    private constructor() {
        this.errorManager = ErrorManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.activeToasts = new Set();
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ToastManager {
        if (!ToastManager.instance) {
            ToastManager.instance = new ToastManager();
        }
        return ToastManager.instance;
    }

    /**
     * Initialize toast manager
     */
    async initialize(): Promise<void> {
        console.log('✅ ToastManager initialized');
        this.createContainer();
        this.setupStyles();
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        this.hideAll();
        if (this.container && this.container.parentNode) {
            this.container.parentNode.removeChild(this.container);
        }
        this.container = null;
        this.activeToasts.clear();
        ToastManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'ToastManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Show a toast notification
     */
    show(options: ToastOptions): void {
        const {
            title,
            message,
            type = 'info',
            duration
        } = options;

        const config = this.configManager.get('toast');
        const finalDuration = duration || config.duration;

        // Validate input
        const titleValidation = this.validateInput(title, 'title');
        const messageValidation = this.validateInput(message, 'message');

        if (!titleValidation.valid || !messageValidation.valid) {
            const error = this.errorManager.createError(
                ErrorType.VALIDATION_ERROR,
                'Toast content validation failed',
                { title, message, errors: [titleValidation.error, messageValidation.error].filter(Boolean) }
            );
            this.errorManager.handleError(error);
            return;
        }

        // Create toast element
        const toast = this.createToastElement(
            titleValidation.sanitized,
            messageValidation.sanitized,
            type
        );

        // Add to container
        if (this.container) {
            this.container.appendChild(toast);
            this.activeToasts.add(toast);

            // Animate in
            requestAnimationFrame(() => {
                toast.classList.add('show');
            });

            // Auto dismiss
            if (finalDuration > 0) {
                setTimeout(() => {
                    this.dismissToast(toast);
                }, finalDuration);
            }
        }
    }

    /**
     * Show success toast
     */
    success(title: string, message: string, duration?: number): void {
        this.show({ title, message, type: 'success', duration });
    }

    /**
     * Show error toast
     */
    error(title: string, message: string, duration?: number): void {
        this.show({ title, message, type: 'error', duration });
    }

    /**
     * Show info toast
     */
    info(title: string, message: string, duration?: number): void {
        this.show({ title, message, type: 'info', duration });
    }

    /**
     * Show warning toast
     */
    warning(title: string, message: string, duration?: number): void {
        this.show({ title, message, type: 'warning', duration });
    }

    /**
     * Hide specific toast
     */
    hide(toast: HTMLElement): void {
        this.dismissToast(toast);
    }

    /**
     * Hide all toasts
     */
    hideAll(): void {
        this.activeToasts.forEach(toast => {
            this.dismissToast(toast);
        });
    }

    /**
     * Get active toast count
     */
    getActiveCount(): number {
        return this.activeToasts.size;
    }

    // ==================== Private Helper Methods ====================

    private createContainer(): void {
        if (typeof document === 'undefined') return;

        this.container = document.createElement('div');
        this.container.className = 'toast-container';
        this.container.setAttribute('role', 'region');
        this.container.setAttribute('aria-live', 'polite');

        // Position based on config
        const config = this.configManager.get('toast');
        const positionClass = this.getPositionClass(config.position);
        this.container.classList.add(positionClass);

        document.body.appendChild(this.container);
    }

    private getPositionClass(position: string): string {
        const positionMap: Record<string, string> = {
            'top-right': 'toast-top-right',
            'top-left': 'toast-top-left',
            'bottom-right': 'toast-bottom-right',
            'bottom-left': 'toast-bottom-left'
        };
        return positionMap[position] || 'toast-top-right';
    }

    private createToastElement(title: string, message: string, type: string): HTMLElement {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.setAttribute('role', 'alert');

        const icon = this.getIconForType(type);
        const config = this.configManager.get('toast');

        toast.innerHTML = `
            <div class="toast-icon">${icon}</div>
            <div class="toast-content">
                <div class="toast-title">${this.escapeHtml(title)}</div>
                <div class="toast-message">${this.escapeHtml(message)}</div>
            </div>
            <button class="toast-close" aria-label="Close notification">×</button>
        `;

        // Add close button handler
        const closeBtn = toast.querySelector('.toast-close');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.dismissToast(toast));
        }

        // Add click to dismiss (optional)
        toast.addEventListener('click', (e) => {
            if (e.target === toast || e.target.classList.contains('toast-content')) {
                this.dismissToast(toast);
            }
        });

        return toast;
    }

    private getIconForType(type: string): string {
        const icons: Record<string, string> = {
            success: '✓',
            error: '✕',
            warning: '⚠',
            info: 'ℹ'
        };
        return icons[type] || '•';
    }

    private dismissToast(toast: HTMLElement): void {
        if (!toast || !this.activeToasts.has(toast)) return;

        toast.classList.remove('show');
        toast.classList.add('hide');

        // Wait for animation to complete
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
            this.activeToasts.delete(toast);
        }, 300);
    }

    private validateInput(content: string, field: string): { valid: boolean; sanitized: string; error?: string } {
        if (!content || typeof content !== 'string') {
            return { valid: false, sanitized: '', error: `${field} must be a non-empty string` };
        }

        if (content.length > 500) {
            return { valid: false, sanitized: '', error: `${field} exceeds maximum length of 500` };
        }

        // Sanitize HTML
        const sanitized = this.escapeHtml(content);

        return { valid: true, sanitized };
    }

    private escapeHtml(text: string): string {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    private setupStyles(): void {
        if (typeof document === 'undefined') return;

        // Check if styles already exist
        if (document.getElementById('toast-styles')) return;

        const style = document.createElement('style');
        style.id = 'toast-styles';
        style.textContent = `
            /* Toast Container */
            .toast-container {
                position: fixed;
                z-index: 9999;
                display: flex;
                flex-direction: column;
                gap: 12px;
                pointer-events: none;
                max-width: 400px;
            }

            .toast-container.toast-top-right {
                top: 20px;
                right: 20px;
            }

            .toast-container.toast-top-left {
                top: 20px;
                left: 20px;
            }

            .toast-container.toast-bottom-right {
                bottom: 20px;
                right: 20px;
            }

            .toast-container.toast-bottom-left {
                bottom: 20px;
                left: 20px;
            }

            /* Toast Item */
            .toast {
                display: flex;
                align-items: flex-start;
                gap: 12px;
                padding: 16px;
                background: white;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                border-left: 4px solid;
                pointer-events: auto;
                cursor: pointer;
                min-width: 280px;
                max-width: 400px;
                transition: all 0.3s ease;
                opacity: 0;
                transform: translateX(100%);
            }

            .toast.show {
                opacity: 1;
                transform: translateX(0);
            }

            .toast.hide {
                opacity: 0;
                transform: translateX(100%);
            }

            /* Toast Types */
            .toast-success {
                border-left-color: #10b981;
            }

            .toast-error {
                border-left-color: #ef4444;
            }

            .toast-warning {
                border-left-color: #f59e0b;
            }

            .toast-info {
                border-left-color: #3b82f6;
            }

            /* Toast Icon */
            .toast-icon {
                font-size: 20px;
                font-weight: bold;
                flex-shrink: 0;
                line-height: 1;
                margin-top: 2px;
            }

            .toast-success .toast-icon {
                color: #10b981;
            }

            .toast-error .toast-icon {
                color: #ef4444;
            }

            .toast-warning .toast-icon {
                color: #f59e0b;
            }

            .toast-info .toast-icon {
                color: #3b82f6;
            }

            /* Toast Content */
            .toast-content {
                flex: 1;
                min-width: 0;
            }

            .toast-title {
                font-weight: 600;
                font-size: 14px;
                color: #1f2937;
                margin-bottom: 4px;
                line-height: 1.3;
            }

            .toast-message {
                font-size: 13px;
                color: #6b7280;
                line-height: 1.4;
                word-wrap: break-word;
            }

            /* Close Button */
            .toast-close {
                background: none;
                border: none;
                font-size: 20px;
                color: #9ca3af;
                cursor: pointer;
                padding: 0;
                width: 20px;
                height: 20px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 4px;
                transition: all 0.2s;
                flex-shrink: 0;
            }

            .toast-close:hover {
                background: #f3f4f6;
                color: #374151;
            }

            /* Hover Effects */
            .toast:hover {
                transform: translateY(-2px);
                box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
            }

            /* Mobile Responsive */
            @media (max-width: 768px) {
                .toast-container {
                    left: 10px;
                    right: 10px;
                    max-width: none;
                }

                .toast {
                    min-width: 0;
                    width: 100%;
                }
            }

            /* Accessibility */
            @media (prefers-reduced-motion: reduce) {
                .toast,
                .toast.show,
                .toast.hide {
                    transition: none;
                    transform: none;
                }
            }

            /* Focus styles */
            .toast:focus {
                outline: 2px solid #3b82f6;
                outline-offset: 2px;
            }

            .toast-close:focus {
                outline: 2px solid #3b82f6;
                outline-offset: 1px;
            }
        `;

        document.head.appendChild(style);
    }
}

/**
 * Convenience functions for quick notifications
 */
export function showToast(title: string, message: string, type?: string, duration?: number): void {
    ToastManager.getInstance().show({ title, message, type: type as any, duration });
}

export function showSuccess(message: string, duration?: number): void {
    ToastManager.getInstance().success('Success', message, duration);
}

export function showError(message: string, duration?: number): void {
    ToastManager.getInstance().error('Error', message, duration);
}

export function showInfo(message: string, duration?: number): void {
    ToastManager.getInstance().info('Info', message, duration);
}

export function showWarning(message: string, duration?: number): void {
    ToastManager.getInstance().warning('Warning', message, duration);
}

/**
 * Error notification helper
 */
export function notifyError(error: any, customMessage?: string): void {
    const message = customMessage || (error.message || 'An unexpected error occurred');
    showError(message);
}
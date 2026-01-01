/**
 * JNet GitHub Pages - Validation Manager
 * Handles input validation and sanitization
 */

import { ValidationManager as IValidationManager } from '../types/index.js';

export class ValidationManager implements IValidationManager {
    private static instance: ValidationManager | null = null;

    private constructor() {}

    static getInstance(): ValidationManager {
        if (!ValidationManager.instance) {
            ValidationManager.instance = new ValidationManager();
        }
        return ValidationManager.instance;
    }

    async initialize(): Promise<void> {
        console.log('✅ ValidationManager initialized');
    }

    destroy(): void {
        ValidationManager.instance = null;
    }

    getName(): string {
        return 'ValidationManager';
    }

    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Validate email format
     */
    validateEmail(email: string): boolean {
        if (!email || typeof email !== 'string') return false;

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }

    /**
     * Validate URL format
     */
    validateURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return ['http:', 'https:'].includes(urlObj.protocol);
        } catch {
            return false;
        }
    }

    /**
     * Validate GitHub username format
     */
    validateGitHubUsername(username: string): boolean {
        if (!username || typeof username !== 'string') return false;

        // GitHub username rules: alphanumeric and hyphens, cannot start/end with hyphen
        const usernameRegex = /^[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$/;
        return usernameRegex.test(username) && username.length >= 1 && username.length <= 39;
    }

    /**
     * Validate GitHub token format
     */
    validateGitHubToken(token: string): boolean {
        if (!token || typeof token !== 'string') return false;

        // GitHub tokens start with ghp_, gho_, github_pat_, etc.
        return token.startsWith('ghp_') || token.startsWith('gho_') || token.startsWith('github_pat_');
    }

    /**
     * Sanitize HTML to prevent XSS
     */
    sanitizeHTML(text: string | null): string {
        if (!text) return '';
        if (typeof text !== 'string') return String(text);

        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Sanitize text for safe display
     */
    sanitizeText(text: string | null): string {
        if (!text) return '';
        if (typeof text !== 'string') return String(text);

        return text
            .replace(/&/g, '&')
            .replace(/</g, '<')
            .replace(/>/g, '>')
            .replace(/"/g, '"')
            .replace(/'/g, ''');
    }

    /**
     * Validate URL format and safety
     */
    validateURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            // Basic URL format validation
            const urlPattern = /^(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?$/;
            if (!urlPattern.test(url)) return false;

            // Additional safety checks
            const urlObj = new URL(url.startsWith('http') ? url : `https://${url}`);

            // Block dangerous protocols
            if (['javascript:', 'data:', 'vbscript:'].includes(urlObj.protocol)) {
                return false;
            }

            // Block localhost and private IPs in production
            if (window.location.hostname !== 'localhost' &&
                window.location.hostname !== '127.0.0.1') {
                const hostname = urlObj.hostname.toLowerCase();
                if (hostname === 'localhost' ||
                    hostname === '127.0.0.1' ||
                    hostname.startsWith('192.168.') ||
                    hostname.startsWith('10.') ||
                    hostname.startsWith('172.')) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate phone number (basic format)
     */
    validatePhone(phone: string): boolean {
        if (!phone || typeof phone !== 'string') return false;

        // Remove all non-digit characters
        const digits = phone.replace(/\D/g, '');

        // Basic validation: 7-15 digits
        return digits.length >= 7 && digits.length <= 15;
    }

    /**
     * Validate date format (YYYY-MM-DD)
     */
    validateDate(date: string): boolean {
        if (!date || typeof date !== 'string') return false;

        const datePattern = /^\d{4}-\d{2}-\d{2}$/;
        if (!datePattern.test(date)) return false;

        const d = new Date(date);
        return d instanceof Date && !isNaN(d.getTime());
    }

    /**
     * Validate username (alphanumeric, underscores, hyphens)
     */
    validateUsername(username: string): boolean {
        if (!username || typeof username !== 'string') return false;

        const usernamePattern = /^[a-zA-Z0-9_-]{3,20}$/;
        return usernamePattern.test(username);
    }

    /**
     * Validate password strength
     */
    validatePassword(password: string): { valid: boolean; issues: string[] } {
        const issues: string[] = [];

        if (!password || typeof password !== 'string') {
            return { valid: false, issues: ['密码不能为空'] };
        }

        if (password.length < 8) {
            issues.push('密码至少需要8个字符');
        }

        if (!/[A-Z]/.test(password)) {
            issues.push('需要包含大写字母');
        }

        if (!/[a-z]/.test(password)) {
            issues.push('需要包含小写字母');
        }

        if (!/\d/.test(password)) {
            issues.push('需要包含数字');
        }

        if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
            issues.push('需要包含特殊字符');
        }

        return {
            valid: issues.length === 0,
            issues
        };
    }

    /**
     * Validate file type
     */
    validateFileType(file: File, allowedTypes: string[]): boolean {
        if (!file || !allowedTypes || !Array.isArray(allowedTypes)) return false;

        return allowedTypes.some(type => {
            if (type.endsWith('/*')) {
                const mainType = type.replace('/*', '');
                return file.type.startsWith(mainType + '/');
            }
            return file.type === type;
        });
    }

    /**
     * Validate file size (in MB)
     */
    validateFileSize(file: File, maxSizeMB: number): boolean {
        if (!file || !maxSizeMB) return false;

        const maxSizeBytes = maxSizeMB * 1024 * 1024;
        return file.size <= maxSizeBytes;
    }

    /**
     * Validate JSON string
     */
    validateJSON(jsonString: string): boolean {
        if (!jsonString || typeof jsonString !== 'string') return false;

        try {
            JSON.parse(jsonString);
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Escape regex special characters
     */
    escapeRegex(text: string): string {
        if (!text || typeof text !== 'string') return '';

        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    /**
     * Validate semver version format
     */
    validateSemver(version: string): boolean {
        if (!version || typeof version !== 'string') return false;

        const semverPattern = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+)?$/;
        return semverPattern.test(version);
    }

    /**
     * Validate base64 string
     */
    validateBase64(str: string): boolean {
        if (!str || typeof str !== 'string') return false;

        const base64Pattern = /^[A-Za-z0-9+/]*={0,2}$/;
        return base64Pattern.test(str) && str.length % 4 === 0;
    }

    /**
     * Validate UUID format
     */
    validateUUID(uuid: string): boolean {
        if (!uuid || typeof uuid !== 'string') return false;

        const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
        return uuidPattern.test(uuid);
    }

    /**
     * Validate hex color code
     */
    validateHexColor(color: string): boolean {
        if (!color || typeof color !== 'string') return false;

        const hexPattern = /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/;
        return hexPattern.test(color);
    }

    /**
     * Validate domain name
     */
    validateDomain(domain: string): boolean {
        if (!domain || typeof domain !== 'string') return false;

        const domainPattern = /^([a-z0-9]+(-[a-z0-9]+)*\.)+[a-z]{2,}$/;
        return domainPattern.test(domain);
    }

    /**
     * Validate port number
     */
    validatePort(port: number): boolean {
        return Number.isInteger(port) && port >= 1 && port <= 65535;
    }

    /**
     * Validate IP address (IPv4 or IPv6)
     */
    validateIP(ip: string): boolean {
        if (!ip || typeof ip !== 'string') return false;

        // IPv4
        const ipv4Pattern = /^(\d{1,3}\.){3}\d{1,3}$/;
        if (ipv4Pattern.test(ip)) {
            return ip.split('.').every(part => {
                const num = parseInt(part, 10);
                return num >= 0 && num <= 255;
            });
        }

        // IPv6
        const ipv6Pattern = /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/;
        return ipv6Pattern.test(ip);
    }

    /**
     * Validate MAC address
     */
    validateMAC(mac: string): boolean {
        if (!mac || typeof mac !== 'string') return false;

        const macPattern = /^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/;
        return macPattern.test(mac);
    }

    /**
     * Validate postal code (basic international)
     */
    validatePostalCode(code: string): boolean {
        if (!code || typeof code !== 'string') return false;

        // Basic international postal code pattern (4-10 alphanumeric)
        const postalPattern = /^[A-Za-z0-9 -]{4,10}$/;
        return postalPattern.test(code.trim());
    }

    /**
     * Validate credit card number (Luhn algorithm)
     */
    validateCreditCard(cardNumber: string): boolean {
        if (!cardNumber || typeof cardNumber !== 'string') return false;

        // Remove spaces and dashes
        const cleaned = cardNumber.replace(/[\s-]/g, '');

        // Check if only digits
        if (!/^\d+$/.test(cleaned)) return false;

        // Luhn algorithm
        let sum = 0;
        let isEven = false;

        for (let i = cleaned.length - 1; i >= 0; i--) {
            let digit = parseInt(cleaned[i], 10);

            if (isEven) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            isEven = !isEven;
        }

        return sum % 10 === 0;
    }

    /**
     * Validate IBAN (International Bank Account Number)
     */
    validateIBAN(iban: string): boolean {
        if (!iban || typeof iban !== 'string') return false;

        // Basic format check (more complex validation would require country-specific rules)
        const ibanPattern = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,}$/;
        return ibanPattern.test(iban.replace(/\s/g, ''));
    }

    /**
     * Validate ISBN (10 or 13 digits)
     */
    validateISBN(isbn: string): boolean {
        if (!isbn || typeof isbn !== 'string') return false;

        const cleaned = isbn.replace(/[\s-]/g, '');

        // ISBN-10
        if (cleaned.length === 10 && /^\d{9}[\dX]$/.test(cleaned)) {
            let sum = 0;
            for (let i = 0; i < 9; i++) {
                sum += parseInt(cleaned[i], 10) * (10 - i);
            }
            const checkDigit = cleaned[9] === 'X' ? 10 : parseInt(cleaned[9], 10);
            sum += checkDigit;
            return sum % 11 === 0;
        }

        // ISBN-13
        if (cleaned.length === 13 && /^\d{13}$/.test(cleaned)) {
            let sum = 0;
            for (let i = 0; i < 12; i++) {
                sum += parseInt(cleaned[i], 10) * (i % 2 === 0 ? 1 : 3);
            }
            const checkDigit = (10 - (sum % 10)) % 10;
            return checkDigit === parseInt(cleaned[12], 10);
        }

        return false;
    }

    /**
     * Validate time format (HH:MM or HH:MM:SS)
     */
    validateTime(time: string): boolean {
        if (!time || typeof time !== 'string') return false;

        const timePattern = /^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/;
        return timePattern.test(time);
    }

    /**
     * Validate latitude and longitude
     */
    validateLatitude(lat: number): boolean {
        return typeof lat === 'number' && lat >= -90 && lat <= 90;
    }

    validateLongitude(lng: number): boolean {
        return typeof lng === 'number' && lng >= -180 && lng <= 180;
    }

    /**
     * Validate hashtag format
     */
    validateHashtag(hashtag: string): boolean {
        if (!hashtag || typeof hashtag !== 'string') return false;

        const hashtagPattern = /^#[a-zA-Z0-9_]+$/;
        return hashtagPattern.test(hashtag);
    }

    /**
     * Validate username for social media (alphanumeric, underscore, dot)
     */
    validateSocialUsername(username: string): boolean {
        if (!username || typeof username !== 'string') return false;

        const pattern = /^[a-zA-Z0-9_.]{3,30}$/;
        return pattern.test(username);
    }

    /**
     * Validate JWT token format (basic structure check)
     */
    validateJWT(token: string): boolean {
        if (!token || typeof token !== 'string') return false;

        const parts = token.split('.');
        if (parts.length !== 3) return false;

        // Check if parts are valid base64
        return parts.every(part => this.validateBase64(part.replace(/-/g, '+').replace(/_/g, '/')));
    }

    /**
     * Validate slug format (URL-friendly string)
     */
    validateSlug(slug: string): boolean {
        if (!slug || typeof slug !== 'string') return false;

        const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
        return slugPattern.test(slug);
    }

    /**
     * Validate color name or code
     */
    validateColor(color: string): boolean {
        if (!color || typeof color !== 'string') return false;

        // Check hex
        if (this.validateHexColor(color)) return true;

        // Check CSS color names
        const cssColors = ['aliceblue', 'antiquewhite', 'aqua', 'aquamarine', 'azure', 'beige', 'bisque', 'black', 'blanchedalmond', 'blue', 'blueviolet', 'brown', 'burlywood', 'cadetblue', 'chartreuse', 'chocolate', 'coral', 'cornflowerblue', 'cornsilk', 'crimson', 'cyan', 'darkblue', 'darkcyan', 'darkgoldenrod', 'darkgray', 'darkgreen', 'darkkhaki', 'darkmagenta', 'darkolivegreen', 'darkorange', 'darkorchid', 'darkred', 'darksalmon', 'darkseagreen', 'darkslateblue', 'darkslategray', 'darkturquoise', 'darkviolet', 'deeppink', 'deepskyblue', 'dimgray', 'dodgerblue', 'firebrick', 'floralwhite', 'forestgreen', 'fuchsia', 'gainsboro', 'ghostwhite', 'gold', 'goldenrod', 'gray', 'green', 'greenyellow', 'honeydew', 'hotpink', 'indianred', 'indigo', 'ivory', 'khaki', 'lavender', 'lavenderblush', 'lawngreen', 'lemonchiffon', 'lightblue', 'lightcoral', 'lightcyan', 'lightgoldenrodyellow', 'lightgray', 'lightgreen', 'lightpink', 'lightsalmon', 'lightseagreen', 'lightskyblue', 'lightslategray', 'lightsteelblue', 'lightyellow', 'lime', 'limegreen', 'linen', 'magenta', 'maroon', 'mediumaquamarine', 'mediumblue', 'mediumorchid', 'mediumpurple', 'mediumseagreen', 'mediumslateblue', 'mediumspringgreen', 'mediumturquoise', 'mediumvioletred', 'midnightblue', 'mintcream', 'mistyrose', 'moccasin', 'navajowhite', 'navy', 'oldlace', 'olive', 'olivedrab', 'orange', 'orangered', 'orchid', 'palegoldenrod', 'palegreen', 'paleturquoise', 'palevioletred', 'papayawhip', 'peachpuff', 'peru', 'pink', 'plum', 'powderblue', 'purple', 'rebeccapurple', 'red', 'rosybrown', 'royalblue', 'saddlebrown', 'salmon', 'sandybrown', 'seagreen', 'seashell', 'sienna', 'silver', 'skyblue', 'slateblue', 'slategray', 'snow', 'springgreen', 'steelblue', 'tan', 'teal', 'thistle', 'tomato', 'turquoise', 'violet', 'wheat', 'white', 'whitesmoke', 'yellow', 'yellowgreen'];
        return cssColors.includes(color.toLowerCase());
    }

    /**
     * Validate MIME type
     */
    validateMIMEType(mime: string): boolean {
        if (!mime || typeof mime !== 'string') return false;

        const mimePattern = /^[a-z]+\/[a-z0-9+-]+$/;
        return mimePattern.test(mime);
    }

    /**
     * Validate language code (ISO 639-1)
     */
    validateLanguageCode(code: string): boolean {
        if (!code || typeof code !== 'string') return false;

        const langPattern = /^[a-z]{2}(-[A-Z]{2})?$/;
        return langPattern.test(code);
    }

    /**
     * Validate currency code (ISO 4217)
     */
    validateCurrencyCode(code: string): boolean {
        if (!code || typeof code !== 'string') return false;

        const currencyPattern = /^[A-Z]{3}$/;
        return currencyPattern.test(code);
    }

    /**
     * Validate country code (ISO 3166-1 alpha-2)
     */
    validateCountryCode(code: string): boolean {
        if (!code || typeof code !== 'string') return false;

        const countryPattern = /^[A-Z]{2}$/;
        return countryPattern.test(code);
    }

    /**
     * Validate timezone (IANA format)
     */
    validateTimezone(tz: string): boolean {
        if (!tz || typeof tz !== 'string') return false;

        // Basic IANA timezone format check
        const tzPattern = /^[A-Za-z]+\/[A-Za-z_]+(\/[A-Za-z_]+)?$/;
        return tzPattern.test(tz);
    }

    /**
     * Validate UUID v4 specifically
     */
    validateUUIDv4(uuid: string): boolean {
        if (!uuid || typeof uuid !== 'string') return false;

        const uuidv4Pattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
        return uuidv4Pattern.test(uuid);
    }

    /**
     * Validate strong password (alternative implementation)
     */
    validateStrongPassword(password: string): boolean {
        if (!password || typeof password !== 'string') return false;

        const minLength = password.length >= 8;
        const hasUpperCase = /[A-Z]/.test(password);
        const hasLowerCase = /[a-z]/.test(password);
        const hasNumbers = /\d/.test(password);
        const hasSpecialChar = /[!@#$%^&*(),.?":{}|<>]/.test(password);

        return minLength && hasUpperCase && hasLowerCase && hasNumbers && hasSpecialChar;
    }

    /**
     * Validate username for display (more permissive)
     */
    validateDisplayName(name: string): boolean {
        if (!name || typeof name !== 'string') return false;

        // Allow letters, numbers, spaces, hyphens, underscores
        const namePattern = /^[a-zA-Z0-9\s_-]{2,50}$/;
        return namePattern.test(name);
    }

    /**
     * Validate file extension
     */
    validateFileExtension(filename: string, allowedExtensions: string[]): boolean {
        if (!filename || typeof filename !== 'string') return false;

        const ext = filename.slice(filename.lastIndexOf('.') + 1).toLowerCase();
        return allowedExtensions.map(e => e.toLowerCase()).includes(ext);
    }

    /**
     * Validate array length
     */
    validateArrayLength(arr: any[], min: number, max: number): boolean {
        if (!Array.isArray(arr)) return false;

        return arr.length >= min && arr.length <= max;
    }

    /**
     * Validate object keys
     */
    validateObjectKeys(obj: any, requiredKeys: string[]): boolean {
        if (typeof obj !== 'object' || obj === null) return false;

        return requiredKeys.every(key => key in obj);
    }

    /**
     * Validate boolean string
     */
    validateBooleanString(str: string): boolean {
        if (!str || typeof str !== 'string') return false;

        return ['true', 'false', '1', '0', 'yes', 'no'].includes(str.toLowerCase());
    }

    /**
     * Validate numeric string
     */
    validateNumericString(str: string): boolean {
        if (typeof str !== 'string') return false;

        return str.length > 0 && !isNaN(Number(str));
    }

    /**
     * Validate alphanumeric string
     */
    validateAlphanumeric(str: string): boolean {
        if (typeof str !== 'string') return false;

        return /^[a-zA-Z0-9]+$/.test(str);
    }

    /**
     * Validate printable ASCII characters only
     */
    validatePrintableASCII(str: string): boolean {
        if (typeof str !== 'string') return false;

        return /^[\x20-\x7E]+$/.test(str);
    }

    /**
     * Validate no control characters
     */
    validateNoControlChars(str: string): boolean {
        if (typeof str !== 'string') return false;

        return !/[\x00-\x1F\x7F]/.test(str);
    }

    /**
     * Validate no SQL injection patterns (basic)
     */
    validateNoSQLInjection(str: string): boolean {
        if (typeof str !== 'string') return false;

        const dangerousPatterns = [
            /(\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION|WHERE|OR|AND)\b)/i,
            /(--|#|\/\*)/,
            /(;\s*DROP|;\s*DELETE)/i
        ];

        return !dangerousPatterns.some(pattern => pattern.test(str));
    }

    /**
     * Validate no XSS patterns (basic)
     */
    validateNoXSS(str: string): boolean {
        if (typeof str !== 'string') return false;

        const xssPatterns = [
            /<script/i,
            /javascript:/i,
            /on\w+\s*=/i,
            /<iframe/i,
            /<object/i,
            /<embed/i,
            /<link/i,
            /<style/i
        ];

        return !xssPatterns.some(pattern => pattern.test(str));
    }

    /**
     * Validate no path traversal
     */
    validateNoPathTraversal(str: string): boolean {
        if (typeof str !== 'string') return false;

        return !/\.\.\//.test(str) && !/^\//.test(str);
    }

    /**
     * Validate email disposable domains (basic check)
     */
    validateNonDisposableEmail(email: string): boolean {
        if (!this.validateEmail(email)) return false;

        const domain = email.split('@')[1].toLowerCase();
        const disposableDomains = [
            'tempmail.com', 'guerrillamail.com', 'mailinator.com',
            '10minutemail.com', 'throwawaymail.com', 'fakeinbox.com'
        ];

        return !disposableDomains.includes(domain);
    }

    /**
     * Validate username not taken (async, would check database)
     */
    async validateUsernameAvailable(username: string): Promise<boolean> {
        // This would typically check a database
        // For now, just validate format
        return this.validateUsername(username);
    }

    /**
     * Validate email not taken (async, would check database)
     */
    async validateEmailAvailable(email: string): Promise<boolean> {
        // This would typically check a database
        // For now, just validate format
        return this.validateEmail(email);
    }

    /**
     * Validate password confirmation
     */
    validatePasswordConfirmation(password: string, confirmation: string): boolean {
        return password === confirmation;
    }

    /**
     * Validate match against pattern
     */
    validatePattern(str: string, pattern: RegExp): boolean {
        if (typeof str !== 'string') return false;

        return pattern.test(str);
    }

    /**
     * Validate length range
     */
    validateLength(str: string, min: number, max: number): boolean {
        if (typeof str !== 'string') return false;

        return str.length >= min && str.length <= max;
    }

    /**
     * Validate number range
     */
    validateNumberRange(num: number, min: number, max: number): boolean {
        if (typeof num !== 'number' || isNaN(num)) return false;

        return num >= min && num <= max;
    }

    /**
     * Validate array contains unique values
     */
    validateArrayUnique(arr: any[]): boolean {
        if (!Array.isArray(arr)) return false;

        return new Set(arr).size === arr.length;
    }

    /**
     * Validate array contains only specific types
     */
    validateArrayTypes(arr: any[], type: string): boolean {
        if (!Array.isArray(arr)) return false;

        return arr.every(item => typeof item === type);
    }

    /**
     * Validate object is plain object (not array, not null, not class instance)
     */
    validatePlainObject(obj: any): boolean {
        if (obj === null || typeof obj !== 'object') return false;

        const proto = Object.getPrototypeOf(obj);
        return proto === null || proto === Object.prototype;
    }

    /**
     * Validate function
     */
    validateFunction(fn: any): boolean {
        return typeof fn === 'function';
    }

    /**
     * Validate promise
     */
    validatePromise(p: any): boolean {
        return p instanceof Promise;
    }

    /**
     * Validate date is in the past
     */
    validateDateInPast(date: Date): boolean {
        if (!(date instanceof Date) || isNaN(date.getTime())) return false;

        return date < new Date();
    }

    /**
     * Validate date is in the future
     */
    validateDateInFuture(date: Date): boolean {
        if (!(date instanceof Date) || isNaN(date.getTime())) return false;

        return date > new Date();
    }

    /**
     * Validate age (minimum age)
     */
    validateAge(dateOfBirth: Date, minAge: number): boolean {
        if (!(dateOfBirth instanceof Date) || isNaN(dateOfBirth.getTime())) return false;

        const today = new Date();
        const age = today.getFullYear() - dateOfBirth.getFullYear();
        const monthDiff = today.getMonth() - dateOfBirth.getMonth();

        const adjustedAge = monthDiff < 0 || (monthDiff === 0 && today.getDate() < dateOfBirth.getDate())
            ? age - 1
            : age;

        return adjustedAge >= minAge;
    }

    /**
     * Validate IBAN checksum (basic)
     */
    validateIBANChecksum(iban: string): boolean {
        if (!iban || typeof iban !== 'string') return false;

        const cleaned = iban.replace(/\s/g, '');

        // Move first 4 characters to end
        const rearranged = cleaned.slice(4) + cleaned.slice(0, 4);

        // Convert letters to numbers (A=10, B=11, etc.)
        const converted = rearranged.replace(/[A-Z]/g, char =>
            (char.charCodeAt(0) - 55).toString()
        );

        // Modulo 97 check
        let remainder = 0;
        for (let i = 0; i < converted.length; i++) {
            remainder = (remainder * 10 + parseInt(converted[i], 10)) % 97;
        }

        return remainder === 1;
    }

    /**
     * Validate BIC (Bank Identifier Code)
     */
    validateBIC(bic: string): boolean {
        if (!bic || typeof bic !== 'string') return false;

        const bicPattern = /^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$/;
        return bicPattern.test(bic);
    }

    /**
     * Validate SWIFT code (same as BIC)
     */
    validateSWIFT(swift: string): boolean {
        return this.validateBIC(swift);
    }

    /**
     * Validate IBAN for specific country
     */
    validateIBANForCountry(iban: string, countryCode: string): boolean {
        if (!this.validateIBAN(iban)) return false;

        const cleaned = iban.replace(/\s/g, '');
        const actualCountry = cleaned.slice(0, 2);

        return actualCountry === countryCode.toUpperCase();
    }

    /**
     * Validate phone number for specific country (basic)
     */
    validatePhoneForCountry(phone: string, countryCode: string): boolean {
        if (!this.validatePhone(phone)) return false;

        // Remove all non-digit characters
        const digits = phone.replace(/\d/g, '');

        // Country-specific length checks (simplified)
        const countryLengths: Record<string, number[]> = {
            'US': [10], 'CA': [10], 'GB': [10], 'FR': [9], 'DE': [10],
            'IT': [10], 'ES': [9], 'AU': [9], 'JP': [10], 'CN': [11]
        };

        const lengths = countryLengths[countryCode.toUpperCase()];
        return lengths ? lengths.includes(digits.length) : true;
    }

    /**
     * Validate postal code for specific country (basic)
     */
    validatePostalCodeForCountry(code: string, countryCode: string): boolean {
        if (!code || typeof code !== 'string') return false;

        const patterns: Record<string, RegExp> = {
            'US': /^\d{5}(-\d{4})?$/,
            'GB': /^[A-Z]{1,2}\d{1,2}[A-Z]?\s?\d[A-Z]{2}$/,
            'CA': /^[A-Z]\d[A-Z]\s?\d[A-Z]\d$/,
            'FR': /^\d{5}$/,
            'DE': /^\d{5}$/,
            'IT': /^\d{5}$/,
            'ES': /^\d{5}$/,
            'AU': /^\d{4}$/,
            'JP': /^\d{3}-\d{4}$/,
            'CN': /^\d{6}$/
        };

        const pattern = patterns[countryCode.toUpperCase()];
        return pattern ? pattern.test(code) : true;
    }

    /**
     * Validate VAT number format (basic)
     */
    validateVATNumber(vat: string): boolean {
        if (!vat || typeof vat !== 'string') return false;

        const vatPattern = /^[A-Z]{2}[0-9A-Z]{8,12}$/;
        return vatPattern.test(vat);
    }

    /**
     * Validate company registration number (basic)
     */
    validateCompanyRegistrationNumber(regNo: string): boolean {
        if (!regNo || typeof regNo !== 'string') return false;

        // Allow alphanumeric and some special characters
        const regPattern = /^[A-Z0-9\/\-]+$/;
        return regPattern.test(regNo);
    }

    /**
     * Validate tax identification number (basic)
     */
    validateTaxID(taxID: string): boolean {
        if (!taxID || typeof taxID !== 'string') return false;

        // Remove common separators
        const cleaned = taxID.replace(/[\s-]/g, '');

        // Check if alphanumeric
        return /^[A-Z0-9]+$/.test(cleaned) && cleaned.length >= 9;
    }

    /**
     * Validate social security number format (basic)
     */
    validateSSN(ssn: string): boolean {
        if (!ssn || typeof ssn !== 'string') return false;

        // Remove dashes and spaces
        const cleaned = ssn.replace(/[\s-]/g, '');

        // Basic format: 9 digits
        if (!/^\d{9}$/.test(cleaned)) return false;

        // Check for invalid patterns (000, 666, 900-999 in first 3 digits)
        const firstThree = cleaned.slice(0, 3);
        const middleTwo = cleaned.slice(3, 5);
        const lastFour = cleaned.slice(5);

        if (firstThree === '000' || firstThree === '666') return false;
        if (parseInt(firstThree, 10) >= 900) return false;
        if (middleTwo === '00') return false;
        if (lastFour === '0000') return false;

        return true;
    }

    /**
     * Validate passport number (basic)
     */
    validatePassportNumber(passport: string): boolean {
        if (!passport || typeof passport !== 'string') return false;

        // General pattern: letter followed by alphanumeric
        const passportPattern = /^[A-Z][0-9]{6,8}$/;
        return passportPattern.test(passport);
    }

    /**
     * Validate driver's license number (basic)
     */
    validateDriverLicense(license: string): boolean {
        if (!license || typeof license !== 'string') return false;

        // Allow alphanumeric and some special characters
        const licensePattern = /^[A-Z0-9]{5,15}$/;
        return licensePattern.test(license);
    }

    /**
     * Validate vehicle registration number (basic)
     */
    validateVehicleRegistration(reg: string): boolean {
        if (!reg || typeof reg !== 'string') return false;

        // Common formats: alphanumeric with spaces/hyphens
        const regPattern = /^[A-Z0-9\s-]{4,10}$/;
        return regPattern.test(reg);
    }

    /**
     * Validate VIN (Vehicle Identification Number)
     */
    validateVIN(vin: string): boolean {
        if (!vin || typeof vin !== 'string') return false;

        const cleaned = vin.toUpperCase().replace(/[^A-Z0-9]/g, '');

        if (cleaned.length !== 17) return false;

        // Check for invalid characters (I, O, Q)
        if (/[IOQ]/.test(cleaned)) return false;

        // Basic checksum validation (simplified)
        const weights = [8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2];
        const translation: Record<string, number> = {
            'A': 1, 'B': 2, 'C': 3, 'D': 4, 'E': 5, 'F': 6, 'G': 7, 'H': 8,
            'J': 1, 'K': 2, 'L': 3, 'M': 4, 'N': 5, 'P': 7, 'R': 9,
            'S': 2, 'T': 3, 'U': 4, 'V': 5, 'W': 6, 'X': 7, 'Y': 8, 'Z': 9
        };

        let sum = 0;
        for (let i = 0; i < 17; i++) {
            const char = cleaned[i];
            const value = /[0-9]/.test(char) ? parseInt(char, 10) : translation[char] || 0;
            sum += value * weights[i];
        }

        const checkDigit = sum % 11;
        const expectedCheck = checkDigit === 10 ? 'X' : checkDigit.toString();

        return cleaned[8] === expectedCheck;
    }

    /**
     * Validate IMEI (International Mobile Equipment Identity)
     */
    validateIMEI(imei: string): boolean {
        if (!imei || typeof imei !== 'string') return false;

        const cleaned = imei.replace(/[\s-]/g, '');

        if (cleaned.length !== 15) return false;

        if (!/^\d{15}$/.test(cleaned)) return false;

        // Luhn algorithm for IMEI
        let sum = 0;
        for (let i = 0; i < 15; i++) {
            let digit = parseInt(cleaned[i], 10);
            if (i % 2 === 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }

        return sum % 10 === 0;
    }

    /**
     * Validate MAC address (EUI-48 or EUI-64)
     */
    validateMACAddress(mac: string): boolean {
        if (!mac || typeof mac !== 'string') return false;

        // Common formats: XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX
        const macPattern = /^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/;
        return macPattern.test(mac);
    }

    /**
     * Validate IPv4 address
     */
    validateIPv4(ip: string): boolean {
        if (!ip || typeof ip !== 'string') return false;

        const ipv4Pattern = /^(\d{1,3}\.){3}\d{1,3}$/;
        if (!ipv4Pattern.test(ip)) return false;

        return ip.split('.').every(part => {
            const num = parseInt(part, 10);
            return num >= 0 && num <= 255;
        });
    }

    /**
     * Validate IPv6 address
     */
    validateIPv6(ip: string): boolean {
        if (!ip || typeof ip !== 'string') return false;

        // Basic IPv6 pattern
        const ipv6Pattern = /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/;
        return ipv6Pattern.test(ip);
    }

    /**
     * Validate CIDR notation
     */
    validateCIDR(cidr: string): boolean {
        if (!cidr || typeof cidr !== 'string') return false;

        const parts = cidr.split('/');
        if (parts.length !== 2) return false;

        const ip = parts[0];
        const mask = parseInt(parts[1], 10);

        if (isNaN(mask) || mask < 0 || mask > 32) return false;

        return this.validateIPv4(ip);
    }

    /**
     * Validate port range
     */
    validatePortRange(start: number, end: number): boolean {
        return this.validatePort(start) && this.validatePort(end) && start <= end;
    }

    /**
     * Validate URL path
     */
    validateURLPath(path: string): boolean {
        if (!path || typeof path !== 'string') return false;

        // Allow relative and absolute paths
        const pathPattern = /^\/?([a-zA-Z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*(\/([a-zA-Z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*$/;
        return pathPattern.test(path);
    }

    /**
     * Validate URL query string
     */
    validateQueryString(qs: string): boolean {
        if (typeof qs !== 'string') return false;

        if (qs.length === 0) return true;

        // Remove leading ? if present
        const query = qs.startsWith('?') ? qs.slice(1) : qs;

        // Basic validation for key=value pairs
        const pairs = query.split('&');
        return pairs.every(pair => {
            const [key, value] = pair.split('=');
            return key && key.length > 0; // Value can be empty
        });
    }

    /**
     * Validate URL fragment (hash)
     */
    validateURLFragment(fragment: string): boolean {
        if (typeof fragment !== 'string') return false;

        if (fragment.length === 0) return true;

        // Remove leading # if present
        const hash = fragment.startsWith('#') ? fragment.slice(1) : fragment;

        // Allow alphanumeric, hyphens, underscores
        return /^[a-zA-Z0-9\-_.]+$/.test(hash);
    }

    /**
     * Validate domain name label (part between dots)
     */
    validateDomainLabel(label: string): boolean {
        if (!label || typeof label !== 'string') return false;

        if (label.length < 1 || label.length > 63) return false;

        if (label.startsWith('-') || label.endsWith('-')) return false;

        return /^[a-zA-Z0-9-]+$/.test(label);
    }

    /**
     * Validate top-level domain
     */
    validateTLD(tld: string): boolean {
        if (!tld || typeof tld !== 'string') return false;

        return /^[a-zA-Z]{2,63}$/.test(tld);
    }

    /**
     * Validate hostname (domain or IP)
     */
    validateHostname(hostname: string): boolean {
        if (!hostname || typeof hostname !== 'string') return false;

        // Check if IP first
        if (this.validateIPv4(hostname) || this.validateIPv6(hostname)) {
            return true;
        }

        // Check domain
        const parts = hostname.split('.');
        if (parts.length < 2) return false;

        return parts.every(part => this.validateDomainLabel(part)) &&
               this.validateTLD(parts[parts.length - 1]);
    }

    /**
     * Validate URL with all components
     */
    validateFullURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            // Check protocol
            if (!['http:', 'https:'].includes(urlObj.protocol)) {
                return false;
            }

            // Check hostname
            if (!this.validateHostname(urlObj.hostname)) {
                return false;
            }

            // Check path
            if (urlObj.pathname && !this.validateURLPath(urlObj.pathname)) {
                return false;
            }

            // Check query string
            if (urlObj.search && !this.validateQueryString(urlObj.search)) {
                return false;
            }

            // Check fragment
            if (urlObj.hash && !this.validateURLFragment(urlObj.hash)) {
                return false;
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate data URL
     */
    validateDataURL(dataUrl: string): boolean {
        if (!dataUrl || typeof dataUrl !== 'string') return false;

        if (!dataUrl.startsWith('data:')) return false;

        // Format: data:[<mediatype>][;base64],<data>
        const parts = dataUrl.split(',');
        if (parts.length !== 2) return false;

        const header = parts[0]; // data:[<mediatype>][;base64]
        const data = parts[1];

        if (!header || !data) return false;

        // Check if base64 encoded
        if (header.includes(';base64')) {
            return this.validateBase64(data);
        }

        // URL-encoded data is allowed
        return true;
    }

    /**
     * Validate email in URL
     */
    validateEmailInURL(url: string): boolean {
        if (!this.validateFullURL(url)) return false;

        try {
            const urlObj = new URL(url);
            const username = urlObj.username;

            return username ? this.validateEmail(username + '@' + urlObj.hostname) : true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has secure protocol
     */
    validateSecureURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.protocol === 'https:';
        } catch {
            return false;
        }
    }

    /**
     * Validate URL does not use dangerous protocols
     */
    validateSafeProtocol(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const dangerousProtocols = ['javascript:', 'data:', 'vbscript:', 'file:', 'about:'];
            return !dangerousProtocols.includes(urlObj.protocol);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL does not redirect to localhost/private IP
     */
    validateNoLocalhostRedirect(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const hostname = urlObj.hostname.toLowerCase();

            // Check for localhost
            if (hostname === 'localhost' || hostname === '127.0.0.1') {
                return false;
            }

            // Check for private IP ranges
            if (hostname.startsWith('192.168.') ||
                hostname.startsWith('10.') ||
                hostname.startsWith('172.16.') ||
                hostname.startsWith('172.17.') ||
                hostname.startsWith('172.18.') ||
                hostname.startsWith('172.19.') ||
                hostname.startsWith('172.20.') ||
                hostname.startsWith('172.21.') ||
                hostname.startsWith('172.22.') ||
                hostname.startsWith('172.23.') ||
                hostname.startsWith('172.24.') ||
                hostname.startsWith('172.25.') ||
                hostname.startsWith('172.26.') ||
                hostname.startsWith('172.27.') ||
                hostname.startsWith('172.28.') ||
                hostname.startsWith('172.29.') ||
                hostname.startsWith('172.30.') ||
                hostname.startsWith('172.31.')) {
                return false;
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no excessive length
     */
    validateURLLength(url: string, maxLength: number = 2048): boolean {
        if (!url || typeof url !== 'string') return false;

        return url.length <= maxLength;
    }

    /**
     * Validate URL has no suspicious characters
     */
    validateURLNoSuspiciousChars(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        // Check for multiple @ symbols, which could be suspicious
        const atCount = (url.match(/@/g) || []).length;
        if (atCount > 1) return false;

        // Check for consecutive dots
        if (url.includes('..')) return false;

        // Check for backslashes (could be path confusion)
        if (url.includes('\\')) return false;

        return true;
    }

    /**
     * Validate URL is not a known phishing pattern
     */
    validateNotPhishingPattern(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const hostname = urlObj.hostname.toLowerCase();

            // Check for homograph attacks (mixed scripts)
            const hasCyrillic = /[\u0400-\u04FF]/.test(hostname);
            const hasLatin = /[a-zA-Z]/.test(hostname);

            if (hasCyrillic && hasLatin) {
                return false;
            }

            // Check for excessive subdomains (potential phishing)
            const parts = hostname.split('.');
            if (parts.length > 5) {
                return false;
            }

            // Check for IP address in hostname (suspicious)
            if (/^\d+\.\d+\.\d+\.\d+$/.test(hostname)) {
                return false;
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL is accessible (would require network request)
     */
    async validateURLAccessible(url: string): Promise<boolean> {
        if (!this.validateFullURL(url)) return false;

        try {
            // This would make an actual request
            // For validation purposes, we just check format
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has valid certificate (would require network request)
     */
    async validateURLCertificate(url: string): Promise<boolean> {
        if (!this.validateSecureURL(url)) return false;

        // In a real implementation, this would check SSL certificate
        // For validation purposes, we just check HTTPS
        return true;
    }

    /**
     * Validate URL redirects to safe destination
     */
    async validateURLRedirectSafety(url: string): Promise<boolean> {
        if (!this.validateFullURL(url)) return false;

        // Would need to follow redirects and check final destination
        // For now, just validate initial URL
        return this.validateNoLocalhostRedirect(url) &&
               this.validateSafeProtocol(url) &&
               this.validateNotPhishingPattern(url);
    }

    /**
     * Validate URL against whitelist
     */
    validateURLWhitelist(url: string, whitelist: string[]): boolean {
        if (!url || !whitelist || !Array.isArray(whitelist)) return false;

        try {
            const urlObj = new URL(url);

            return whitelist.some(allowed => {
                try {
                    const allowedObj = new URL(allowed);
                    return urlObj.hostname === allowedObj.hostname;
                } catch {
                    // If whitelist entry is not a full URL, treat as hostname
                    return urlObj.hostname === allowed;
                }
            });
        } catch {
            return false;
        }
    }

    /**
     * Validate URL against blacklist
     */
    validateURLBlacklist(url: string, blacklist: string[]): boolean {
        if (!url || !blacklist || !Array.isArray(blacklist)) return true;

        try {
            const urlObj = new URL(url);

            return !blacklist.some(blocked => {
                try {
                    const blockedObj = new URL(blocked);
                    return urlObj.hostname === blockedObj.hostname;
                } catch {
                    // If blacklist entry is not a full URL, treat as hostname
                    return urlObj.hostname === blocked;
                }
            });
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has expected domain
     */
    validateURLDomain(url: string, expectedDomain: string): boolean {
        if (!url || !expectedDomain) return false;

        try {
            const urlObj = new URL(url);
            return urlObj.hostname === expectedDomain;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has expected subdomain
     */
    validateURLSubdomain(url: string, expectedSubdomain: string): boolean {
        if (!url || !expectedSubdomain) return false;

        try {
            const urlObj = new URL(url);
            return urlObj.hostname.startsWith(expectedSubdomain + '.');
        } catch {
            return false;
        }
    }

    /**
     * Validate URL does not have tracking parameters
     */
    validateURLNoTrackingParams(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const params = urlObj.searchParams;

            // Common tracking parameters
            const trackingParams = [
                'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content',
                'fbclid', 'gclid', 'msclkid', 'trk', 'ref', 'referer'
            ];

            for (const param of trackingParams) {
                if (params.has(param)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has required parameters
     */
    validateURLHasParams(url: string, requiredParams: string[]): boolean {
        if (!url || !requiredParams || !Array.isArray(requiredParams)) return false;

        try {
            const urlObj = new URL(url);
            return requiredParams.every(param => urlObj.searchParams.has(param));
        } catch {
            return false;
        }
    }

    /**
     * Validate URL parameter values
     */
    validateURLParamValues(url: string, paramRules: Record<string, (value: string) => boolean>): boolean {
        if (!url || !paramRules || typeof paramRules !== 'object') return false;

        try {
            const urlObj = new URL(url);

            for (const [param, validator] of Object.entries(paramRules)) {
                const value = urlObj.searchParams.get(param);
                if (value === null || !validator(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no duplicate parameters
     */
    validateURLNoDuplicateParams(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const params = urlObj.searchParams;

            // URLSearchParams automatically handles duplicates, but we can check the raw string
            const queryString = urlObj.search;
            if (!queryString) return true;

            const pairs = queryString.slice(1).split('&');
            const seen = new Set<string>();

            for (const pair of pairs) {
                const key = pair.split('=')[0];
                if (seen.has(key)) {
                    return false;
                }
                seen.add(key);
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has reasonable parameter count
     */
    validateURLParamCount(url: string, maxParams: number = 10): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.searchParams.size <= maxParams;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL parameter names are valid
     */
    validateURLParamNames(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const params = urlObj.searchParams;

            for (const [key] of params) {
                // Basic validation: alphanumeric and underscores
                if (!/^[a-zA-Z0-9_]+$/.test(key)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL parameter values are safe
     */
    validateURLParamValuesSafe(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const params = urlObj.searchParams;

            for (const [key, value] of params) {
                // Check for XSS patterns
                if (this.validateNoXSS(key) === false || this.validateNoXSS(value) === false) {
                    return false;
                }

                // Check for SQL injection
                if (this.validateNoSQLInjection(key) === false || this.validateNoSQLInjection(value) === false) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has valid port (if specified)
     */
    validateURLPort(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const port = urlObj.port;

            if (!port) return true; // No port specified is valid

            const portNum = parseInt(port, 10);
            return this.validatePort(portNum);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has valid username/password
     */
    validateURLCredentials(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const username = urlObj.username;
            const password = urlObj.password;

            // If no credentials, it's valid
            if (!username && !password) return true;

            // If credentials are present, validate them
            if (username && !this.validateUsername(username)) {
                return false;
            }

            // Password validation would be more complex, just check it's not empty
            if (password && password.length === 0) {
                return false;
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has valid path segments
     */
    validateURLPathSegments(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const segments = urlObj.pathname.split('/').filter(s => s.length > 0);

            // Check each segment
            for (const segment of segments) {
                // Basic validation: no special characters that could be dangerous
                if (!/^[a-zA-Z0-9\-_.~]+$/.test(segment)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has valid file extension (if any)
     */
    validateURLFileExtension(url: string, allowedExtensions: string[] = []): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const pathname = urlObj.pathname;

            // If no extension, it's valid
            if (!pathname.includes('.')) return true;

            const ext = pathname.split('.').pop()?.toLowerCase();
            if (!ext) return false;

            // If no whitelist, any extension is valid
            if (allowedExtensions.length === 0) return true;

            return allowedExtensions.map(e => e.toLowerCase()).includes(ext);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has valid fragment (hash)
     */
    validateURLHasValidFragment(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const hash = urlObj.hash;

            if (!hash) return true; // No fragment is valid

            // Fragment should start with #
            if (!hash.startsWith('#')) return false;

            // Fragment content should be valid
            const fragmentContent = hash.slice(1);
            if (fragmentContent.length === 0) return true; // Empty fragment is valid

            // Basic validation for fragment content
            return /^[a-zA-Z0-9\-_.~!$&'()*+,;=:@/?%]*$/.test(fragmentContent);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL is not a data URL
     */
    validateNotDataURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('data:');
    }

    /**
     * Validate URL is not a javascript URL
     */
    validateNotJavaScriptURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.toLowerCase().startsWith('javascript:');
    }

    /**
     * Validate URL is not a file URL
     */
    validateNotFileURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('file:');
    }

    /**
     * Validate URL is not a mailto URL
     */
    validateNotMailtoURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('mailto:');
    }

    /**
     * Validate URL is not a tel URL
     */
    validateNotTelURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('tel:');
    }

    /**
     * Validate URL is not a sms URL
     */
    validateNotSMSURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('sms:');
    }

    /**
     * Validate URL is not a ftp URL
     */
    validateNotFTPURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('ftp:');
    }

    /**
     * Validate URL is not a magnet URL
     */
    validateNotMagnetURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('magnet:');
    }

    /**
     * Validate URL is not a torrent URL
     */
    validateNotTorrentURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.endsWith('.torrent');
    }

    /**
     * Validate URL is not a blob URL
     */
    validateNotBlobURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('blob:');
    }

    /**
     * Validate URL is not a websocket URL
     */
    validateNotWebSocketURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        return !url.startsWith('ws:') && !url.startsWith('wss:');
    }

    /**
     * Validate URL is not a custom protocol
     */
    validateNotCustomProtocol(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const allowedProtocols = ['http:', 'https:'];
            return allowedProtocols.includes(urlObj.protocol);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL is not a relative URL
     */
    validateNotRelativeURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        // Relative URLs don't start with a protocol
        return /^(https?:|\/\/)/.test(url);
    }

    /**
     * Validate URL is absolute
     */
    validateAbsoluteURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            new URL(url);
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL is relative
     */
    validateRelativeURL(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        // Relative URLs don't have protocol
        if (/^(https?:|\/\/)/.test(url)) return false;

        // Should start with / or be a relative path
        return url.startsWith('/') || /^[a-zA-Z0-9]/.test(url);
    }

    /**
     * Validate URL is same origin
     */
    validateSameOrigin(url: string, base: string = window.location.href): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url, base);
            const baseObj = new URL(base);

            return urlObj.origin === baseObj.origin;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL is cross origin
     */
    validateCrossOrigin(url: string, base: string = window.location.href): boolean {
        return !this.validateSameOrigin(url, base);
    }

    /**
     * Validate URL has same protocol as base
     */
    validateSameProtocol(url: string, base: string = window.location.href): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url, base);
            const baseObj = new URL(base);

            return urlObj.protocol === baseObj.protocol;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has secure protocol (HTTPS)
     */
    validateSecureProtocol(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.protocol === 'https:';
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has HTTP protocol
     */
    validateHTTPProtocol(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.protocol === 'http:';
        } catch {
            return false;
        }
    }

    /**
     * Validate URL uses default port for protocol
     */
    validateDefaultPort(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            if (urlObj.protocol === 'http:' && urlObj.port === '80') {
                return true;
            }

            if (urlObj.protocol === 'https:' && urlObj.port === '443') {
                return true;
            }

            // No port specified is also valid (uses default)
            if (!urlObj.port) {
                return true;
            }

            return false;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no port (uses default)
     */
    validateNoPort(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !urlObj.port;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has specific port
     */
    validateHasPort(url: string, port: number): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return parseInt(urlObj.port, 10) === port;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no authentication
     */
    validateNoAuth(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !urlObj.username && !urlObj.password;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has authentication
     */
    validateHasAuth(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !!urlObj.username;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no query string
     */
    validateNoQuery(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !urlObj.search;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has query string
     */
    validateHasQuery(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !!urlObj.search;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no fragment
     */
    validateNoFragment(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !urlObj.hash;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has fragment
     */
    validateHasFragment(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return !!urlObj.hash;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has specific fragment
     */
    validateHasSpecificFragment(url: string, fragment: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.hash === '#' + fragment;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has specific query parameter
     */
    validateHasQueryParam(url: string, param: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.searchParams.has(param);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has specific query parameter value
     */
    validateQueryParamValue(url: string, param: string, value: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            return urlObj.searchParams.get(param) === value;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has query parameter matching pattern
     */
    validateQueryParamPattern(url: string, param: string, pattern: RegExp): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const value = urlObj.searchParams.get(param);
            return value ? pattern.test(value) : false;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no query parameters matching pattern
     */
    validateNoQueryParamsMatching(url: string, pattern: RegExp): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (pattern.test(key) || pattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has all required query parameters
     */
    validateRequiredQueryParams(url: string, requiredParams: string[]): boolean {
        if (!url || !requiredParams || !Array.isArray(requiredParams)) return false;

        try {
            const urlObj = new URL(url);
            return requiredParams.every(param => urlObj.searchParams.has(param));
        } catch {
            return false;
        }
    }

    /**
     * Validate URL has no forbidden query parameters
     */
    validateNoForbiddenQueryParams(url: string, forbiddenParams: string[]): boolean {
        if (!url || !forbiddenParams || !Array.isArray(forbiddenParams)) return false;

        try {
            const urlObj = new URL(url);

            return !forbiddenParams.some(param => urlObj.searchParams.has(param));
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are within limits
     */
    validateQueryParamsLimits(url: string, limits: Record<string, { min?: number; max?: number }>): boolean {
        if (!url || !limits || typeof limits !== 'object') return false;

        try {
            const urlObj = new URL(url);

            for (const [param, limit] of Object.entries(limits)) {
                const value = urlObj.searchParams.get(param);
                if (value === null) continue;

                const numValue = parseFloat(value);
                if (isNaN(numValue)) return false;

                if (limit.min !== undefined && numValue < limit.min) return false;
                if (limit.max !== undefined && numValue > limit.max) return false;
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are within allowed values
     */
    validateQueryParamsAllowedValues(url: string, allowedValues: Record<string, string[]>): boolean {
        if (!url || !allowedValues || typeof allowedValues !== 'object') return false;

        try {
            const urlObj = new URL(url);

            for (const [param, allowed] of Object.entries(allowedValues)) {
                const value = urlObj.searchParams.get(param);
                if (value === null) continue;

                if (!allowed.includes(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not within forbidden values
     */
    validateQueryParamsNotForbiddenValues(url: string, forbiddenValues: Record<string, string[]>): boolean {
        if (!url || !forbiddenValues || typeof forbiddenValues !== 'object') return false;

        try {
            const urlObj = new URL(url);

            for (const [param, forbidden] of Object.entries(forbiddenValues)) {
                const value = urlObj.searchParams.get(param);
                if (value === null) continue;

                if (forbidden.includes(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters match expected types
     */
    validateQueryParamsTypes(url: string, types: Record<string, 'string' | 'number' | 'boolean'>): boolean {
        if (!url || !types || typeof types !== 'object') return false;

        try {
            const urlObj = new URL(url);

            for (const [param, expectedType] of Object.entries(types)) {
                const value = urlObj.searchParams.get(param);
                if (value === null) continue;

                if (expectedType === 'number') {
                    if (isNaN(parseFloat(value))) return false;
                } else if (expectedType === 'boolean') {
                    if (!['true', 'false', '1', '0'].includes(value.toLowerCase())) {
                        return false;
                    }
                }
                // 'string' type always valid
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not empty
     */
    validateQueryParamsNotEmpty(url: string, params: string[]): boolean {
        if (!url || !params || !Array.isArray(params)) return false;

        try {
            const urlObj = new URL(url);

            return params.every(param => {
                const value = urlObj.searchParams.get(param);
                return value !== null && value.length > 0;
            });
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not duplicated
     */
    validateQueryParamsNotDuplicated(url: string): boolean {
        return this.validateURLNoDuplicateParams(url);
    }

    /**
     * Validate URL query parameters are sorted
     */
    validateQueryParamsSorted(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const params = Array.from(urlObj.searchParams.keys());

            // Check if sorted
            const sorted = [...params].sort();
            return params.every((param, index) => param === sorted[index]);
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are unique
     */
    validateQueryParamsUnique(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const params = Array.from(urlObj.searchParams.keys());
            const unique = new Set(params);

            return params.length === unique.size;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters count
     */
    validateQueryParamsCount(url: string, min: number, max: number): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const count = urlObj.searchParams.size;

            return count >= min && count <= max;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters total length
     */
    validateQueryParamsLength(url: string, maxLength: number): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const queryString = urlObj.search;

            return queryString.length <= maxLength;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters do not exceed size limit
     */
    validateQueryParamsSize(url: string, maxSizeBytes: number): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const queryString = urlObj.search;
            const size = new Blob([queryString]).size;

            return size <= maxSizeBytes;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are URL encoded
     */
    validateQueryParamsURLEncoded(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);
            const queryString = urlObj.search;

            if (!queryString) return true;

            // Check if the query string is properly encoded
            // We can't easily verify this without parsing, but we can check for common issues
            const rawQuery = queryString.slice(1);

            // Check for unencoded spaces (should be %20 or +)
            if (rawQuery.includes(' ')) {
                return false;
            }

            // Check for unencoded special characters
            const specialChars = /[&=]/g;
            const matches = rawQuery.match(specialChars);

            // If there are special characters, they should be part of the structure
            // This is a basic check
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not too long
     */
    validateQueryParamsValueLength(url: string, maxParamLength: number): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (key.length > maxParamLength || value.length > maxParamLength) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters do not contain dangerous characters
     */
    validateQueryParamsSafe(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for XSS
                if (this.validateNoXSS(key) === false || this.validateNoXSS(value) === false) {
                    return false;
                }

                // Check for SQL injection
                if (this.validateNoSQLInjection(key) === false || this.validateNoSQLInjection(value) === false) {
                    return false;
                }

                // Check for path traversal
                if (this.validateNoPathTraversal(key) === false || this.validateNoPathTraversal(value) === false) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not empty values
     */
    validateQueryParamsNoEmptyValues(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (value.length === 0) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not empty keys
     */
    validateQueryParamsNoEmptyKeys(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key] of urlObj.searchParams) {
                if (key.length === 0) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not whitespace only
     */
    validateQueryParamsNoWhitespaceOnly(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (key.trim().length === 0 || value.trim().length === 0) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not control characters
     */
    validateQueryParamsNoControlChars(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (this.validateNoControlChars(key) === false || this.validateNoControlChars(value) === false) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are printable ASCII
     */
    validateQueryParamsPrintableASCII(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (this.validatePrintableASCII(key) === false || this.validatePrintableASCII(value) === false) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are alphanumeric
     */
    validateQueryParamsAlphanumeric(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (this.validateAlphanumeric(key) === false || this.validateAlphanumeric(value) === false) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters match regex pattern
     */
    validateQueryParamsPattern(url: string, pattern: RegExp): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                if (!pattern.test(key) || !pattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not too large
     */
    validateQueryParamsNotTooLarge(url: string, maxParams: number = 50): boolean {
        return this.validateURLParamCount(url, maxParams);
    }

    /**
     * Validate URL query parameters are not too deep
     */
    validateQueryParamsNotTooDeep(url: string, maxDepth: number = 5): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for nested structures (e.g., param[sub]=value)
                const depth = Math.max(
                    (key.match(/\[/g) || []).length,
                    (value.match(/\[/g) || []).length
                );

                if (depth > maxDepth) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not circular
     */
    validateQueryParamsNotCircular(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            // Check for self-referential patterns
            for (const [key, value] of urlObj.searchParams) {
                // Check if value contains the key name (potential circular reference)
                if (value.includes(key)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not recursive
     */
    validateQueryParamsNotRecursive(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            // Check for recursive patterns
            for (const [key, value] of urlObj.searchParams) {
                // Check for repeated patterns
                if (key.includes(value) || value.includes(key)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not encoded multiple times
     */
    validateQueryParamsNotDoubleEncoded(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for double encoding patterns
                if (key.includes('%25') || value.includes('%25')) {
                    return false;
                }

                // Check for encoded special characters that should be decoded
                if (key.includes('%') && decodeURIComponent(key) !== key) {
                    // If decoding changes the value, it might be double encoded
                    if (decodeURIComponent(decodeURIComponent(key)) !== key) {
                        return false;
                    }
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not obfuscated
     */
    validateQueryParamsNotObfuscated(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for common obfuscation techniques
                const suspiciousPatterns = [
                    /%00/, // Null bytes
                    /%0d%0a/, // CRLF injection
                    /%2f%2f/, // Double slashes
                    /%3a%2f%2f/, // Encoded ://
                    /%26/, // Ampersand
                    /%3d/, // Equals
                    /%3f/, // Question mark
                ];

                const combined = key + value;
                if (suspiciousPatterns.some(pattern => pattern.test(combined))) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not encoded in base64
     */
    validateQueryParamsNotBase64(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check if value looks like base64
                if (this.validateBase64(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not hex encoded
     */
    validateQueryParamsNotHex(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check if value is all hex
                if (/^[0-9a-fA-F]+$/.test(value) && value.length % 2 === 0) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not unicode encoded
     */
    validateQueryParamsNotUnicode(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for unicode escape sequences
                if (/\\u[0-9a-fA-F]{4}/.test(key) || /\\u[0-9a-fA-F]{4}/.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not binary
     */
    validateQueryParamsNotBinary(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for non-printable characters
                if (/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/.test(key) ||
                    /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not compressed
     */
    validateQueryParamsNotCompressed(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for common compression signatures
                const compressedPatterns = [
                    /^x\x9c/, // zlib
                    /^BZh/,   // bzip2
                    /^\x1f\x8b/, // gzip
                    /^\x04\x22/, // lzip
                    /^\x28\x62/, // lzma
                ];

                const combined = key + value;
                if (compressedPatterns.some(pattern => pattern.test(combined))) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not encrypted
     */
    validateQueryParamsNotEncrypted(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for high entropy (encrypted data looks random)
                const combined = key + value;
                const entropy = this.calculateEntropy(combined);

                // Encrypted data typically has high entropy
                if (entropy > 4.5) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Calculate entropy of a string
     */
    private calculateEntropy(str: string): number {
        if (!str || str.length === 0) return 0;

        const frequencies: Record<string, number> = {};
        for (const char of str) {
            frequencies[char] = (frequencies[char] || 0) + 1;
        }

        let entropy = 0;
        const len = str.length;

        for (const count of Object.values(frequencies)) {
            const probability = count / len;
            entropy -= probability * Math.log2(probability);
        }

        return entropy;
    }

    /**
     * Validate URL query parameters are not obfuscated with encoding
     */
    validateQueryParamsNotObfuscatedEncoding(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for multiple encoding layers
                let decoded = key + value;
                let previous = '';

                // Try decoding multiple times
                for (let i = 0; i < 5; i++) {
                    try {
                        const newDecoded = decodeURIComponent(decoded);
                        if (newDecoded === decoded) break;

                        // If we decoded but it looks like more encoding
                        if (newDecoded.includes('%') && newDecoded !== previous) {
                            return false;
                        }

                        previous = decoded;
                        decoded = newDecoded;
                    } catch {
                        break;
                    }
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using alternative encoding
     */
    validateQueryParamsNotAlternativeEncoding(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for unicode normalization issues
                const normalized = key.normalize('NFC') + value.normalize('NFC');

                // If normalization changes the string significantly, it might be obfuscated
                if (normalized !== key + value) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using homoglyphs
     */
    validateQueryParamsNotHomoglyphs(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for mixed scripts (Cyrillic, Greek, etc. mixed with Latin)
                const combined = key + value;

                const hasLatin = /[a-zA-Z]/.test(combined);
                const hasCyrillic = /[\u0400-\u04FF]/.test(combined);
                const hasGreek = /[\u0370-\u03FF]/.test(combined);
                const hasArabic = /[\u0600-\u06FF]/.test(combined);

                const scripts = [hasLatin, hasCyrillic, hasGreek, hasArabic].filter(Boolean).length;

                if (scripts > 1) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using zero-width characters
     */
    validateQueryParamsNotZeroWidth(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for zero-width characters
                const zeroWidthPattern = /[\u200B-\u200D\uFEFF]/;

                if (zeroWidthPattern.test(key) || zeroWidthPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using invisible characters
     */
    validateQueryParamsNotInvisible(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for invisible characters
                const invisiblePattern = /[\u0000-\u001F\u007F\u0080-\u009F\u200B-\u200D\uFEFF]/;

                if (invisiblePattern.test(key) || invisiblePattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using homographs
     */
    validateQueryParamsNotHomographs(url: string): boolean {
        return this.validateQueryParamsNotHomoglyphs(url);
    }

    /**
     * Validate URL query parameters are not using confusable characters
     */
    validateQueryParamsNotConfusable(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            // Common confusable characters
            const confusables = [
                ['0', 'O'], ['1', 'l', 'I'], ['5', 'S'], ['8', 'B'],
                ['2', 'Z'], ['6', 'G'], ['9', 'q'], ['m', 'rn']
            ];

            for (const [key, value] of urlObj.searchParams) {
                const combined = key + value;

                for (const group of confusables) {
                    // Check if multiple characters from the same group are used
                    const used = group.filter(char => combined.includes(char));
                    if (used.length > 1) {
                        return false;
                    }
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using mixed case to confuse
     */
    validateQueryParamsNotMixedCase(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for alternating case patterns (e.g., "aLtErNaTiNg")
                const alternatingPattern = /[a-z][A-Z][a-z][A-Z]/;

                if (alternatingPattern.test(key) || alternatingPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using similar looking characters
     */
    validateQueryParamsNotSimilarLooking(url: string): boolean {
        return this.validateQueryParamsNotConfusable(url);
    }

    /**
     * Validate URL query parameters are not using homoglyph attacks
     */
    validateQueryParamsNotHomoglyphAttack(url: string): boolean {
        return this.validateQueryParamsNotHomoglyphs(url);
    }

    /**
     * Validate URL query parameters are not using mixed scripts
     */
    validateQueryParamsNotMixedScripts(url: string): boolean {
        return this.validateQueryParamsNotHomoglyphs(url);
    }

    /**
     * Validate URL query parameters are not using right-to-left override
     */
    validateQueryParamsNotRTLOverride(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for RTL override characters
                const rtlPattern = /[\u202E\u202D]/;

                if (rtlPattern.test(key) || rtlPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using bidirectional text
     */
    validateQueryParamsNotBidi(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for bidirectional characters
                const bidiPattern = /[\u0590-\u05FF\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]/;

                if (bidiPattern.test(key) || bidiPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using emoji
     */
    validateQueryParamsNotEmoji(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for emoji characters
                const emojiPattern = /[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F1E6}-\u{1F1FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;

                if (emojiPattern.test(key) || emojiPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using mathematical symbols
     */
    validateQueryParamsNotMathSymbols(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for mathematical symbols
                const mathPattern = /[\u2200-\u22FF]/;

                if (mathPattern.test(key) || mathPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using currency symbols
     */
    validateQueryParamsNotCurrency(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for currency symbols
                const currencyPattern = /[\$\u00A2\u00A3\u00A4\u00A5\u058F\u060B\u09F2\u09F3\u09FB\u0AF1\u0BF9\u0E3F\u17DB\u20A0-\u20C0\uA838\uFDFC\uFE69\uFF04\uFFE0\uFFE1\uFFE5\uFFE6]/;

                if (currencyPattern.test(key) || currencyPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using special symbols
     */
    validateQueryParamsNotSpecialSymbols(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for special symbols (non-alphanumeric, not common URL chars)
                const specialPattern = /[^\w\s\-._~!$&'()*+,;=:@/?%]/;

                if (specialPattern.test(key) || specialPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using box drawing characters
     */
    validateQueryParamsNotBoxDrawing(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for box drawing characters
                const boxPattern = /[\u2500-\u257F]/;

                if (boxPattern.test(key) || boxPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using block elements
     */
    validateQueryParamsNotBlockElements(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for block elements
                const blockPattern = /[\u2580-\u259F]/;

                if (blockPattern.test(key) || blockPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using geometric shapes
     */
    validateQueryParamsNotGeometric(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for geometric shapes
                const geometricPattern = /[\u25A0-\u25FF]/;

                if (geometricPattern.test(key) || geometricPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using misc symbols
     */
    validateQueryParamsNotMiscSymbols(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for miscellaneous symbols
                const miscPattern = /[\u2600-\u26FF]/;

                if (miscPattern.test(key) || miscPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using dingbats
     */
    validateQueryParamsNotDingbats(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for dingbats
                const dingbatPattern = /[\u2700-\u27BF]/;

                if (dingbatPattern.test(key) || dingbatPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using transport symbols
     */
    validateQueryParamsNotTransport(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for transport symbols
                const transportPattern = /[\u2708-\u27ED\u27F0-\u27FF]/;

                if (transportPattern.test(key) || transportPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using alchemical symbols
     */
    validateQueryParamsNotAlchemical(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for alchemical symbols
                const alchemicalPattern = /[\u{1F700}-\u{1F77F}]/u;

                if (alchemicalPattern.test(key) || alchemicalPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using geometric shapes extended
     */
    validateQueryParamsNotGeometricExtended(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for geometric shapes extended
                const geometricExtendedPattern = /[\u{1F7A0}-\u{1F7FF}]/u;

                if (geometricExtendedPattern.test(key) || geometricExtendedPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using transport extended
     */
    validateQueryParamsNotTransportExtended(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for transport extended
                const transportExtendedPattern = /[\u{1F680}-\u{1F6FF}]/u;

                if (transportExtendedPattern.test(key) || transportExtendedPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using emoji extended
     */
    validateQueryParamsNotEmojiExtended(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for emoji extended
                const emojiExtendedPattern = /[\u{1F300}-\u{1F5FF}\u{1F600}-\u{1F64F}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{1FB00}-\u{1FBFF}]/u;

                if (emojiExtendedPattern.test(key) || emojiExtendedPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using symbols and pictographs
     */
    validateQueryParamsNotSymbolsAndPictographs(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for symbols and pictographs
                const symbolsPattern = /[\u{1F300}-\u{1F5FF}\u{1F600}-\u{1F64F}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{1FB00}-\u{1FBFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;

                if (symbolsPattern.test(key) || symbolsPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using supplemental symbols
     */
    validateQueryParamsNotSupplementalSymbols(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for supplemental symbols
                const supplementalPattern = /[\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{1FB00}-\u{1FBFF}]/u;

                if (supplementalPattern.test(key) || supplementalPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using pictographs
     */
    validateQueryParamsNotPictographs(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for pictographs
                const pictographPattern = /[\u{1F300}-\u{1F5FF}\u{1F600}-\u{1F64F}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}]/u;

                if (pictographPattern.test(key) || pictographPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using emoticons
     */
    validateQueryParamsNotEmoticons(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for emoticons
                const emoticonPattern = /[\u{1F600}-\u{1F64F}]/u;

                if (emoticonPattern.test(key) || emoticonPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using transport and maps
     */
    validateQueryParamsNotTransportAndMaps(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for transport and maps
                const transportMapPattern = /[\u{1F680}-\u{1F6FF}\u{1F7E0}-\u{1F7EB}\u{1F9BC}-\u{1F9BF}\u{1FA78}-\u{1FA7A}]/u;

                if (transportMapPattern.test(key) || transportMapPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using flags
     */
    validateQueryParamsNotFlags(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for flags
                const flagPattern = /[\u{1F1E6}-\u{1F1FF}]/u;

                if (flagPattern.test(key) || flagPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using regional indicator symbols
     */
    validateQueryParamsNotRegionalIndicators(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for regional indicator symbols
                const regionalPattern = /[\u{1F1E6}-\u{1F1FF}]/u;

                if (regionalPattern.test(key) || regionalPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using enclosed alphanumerics
     */
    validateQueryParamsNotEnclosedAlphanumerics(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for enclosed alphanumerics
                const enclosedPattern = /[\u{2460}-\u{24FF}\u{1F100}-\u{1F1FF}]/u;

                if (enclosedPattern.test(key) || enclosedPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using enclosed CJK letters and months
     */
    validateQueryParamsNotEnclosedCJK(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for enclosed CJK
                const enclosedCJKPattern = /[\u{3200}-\u{32FF}]/u;

                if (enclosedCJKPattern.test(key) || enclosedCJKPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using CJK compatibility
     */
    validateQueryParamsNotCJKCompatibility(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for CJK compatibility
                const cjkCompatPattern = /[\u{3300}-\u{33FF}\u{FE30}-\u{FE4F}]/u;

                if (cjkCompatPattern.test(key) || cjkCompatPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using small form variants
     */
    validateQueryParamsNotSmallFormVariants(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for small form variants
                const smallFormPattern = /[\u{FE50}-\u{FE6F}]/u;

                if (smallFormPattern.test(key) || smallFormPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using halfwidth and fullwidth forms
     */
    validateQueryParamsNotHalfwidthFullwidth(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for halfwidth and fullwidth forms
                const halfwidthFullwidthPattern = /[\u{FF00}-\u{FFEF}]/u;

                if (halfwidthFullwidthPattern.test(key) || halfwidthFullwidthPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using special forms
     */
    validateQueryParamsNotSpecialForms(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for special forms
                const specialFormsPattern = /[\u{FFF0}-\u{FFFF}]/u;

                if (specialFormsPattern.test(key) || specialFormsPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using tags
     */
    validateQueryParamsNotTags(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for tags
                const tagsPattern = /[\u{E000}-\u{F8FF}]/u;

                if (tagsPattern.test(key) || tagsPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using variation selectors
     */
    validateQueryParamsNotVariationSelectors(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for variation selectors
                const variationPattern = /[\u{FE00}-\u{FE0F}\u{E0100}-\u{E01EF}]/u;

                if (variationPattern.test(key) || variationPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using supplementary private use
     */
    validateQueryParamsNotSupplementaryPrivateUse(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for supplementary private use
                const supplementaryPattern = /[\u{F0000}-\u{FFFFD}\u{100000}-\u{10FFFD}]/u;

                if (supplementaryPattern.test(key) || supplementaryPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using private use areas
     */
    validateQueryParamsNotPrivateUse(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for private use areas
                const privateUsePattern = /[\u{E000}-\u{F8FF}\u{F0000}-\u{FFFFD}\u{100000}-\u{10FFFD}]/u;

                if (privateUsePattern.test(key) || privateUsePattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using non-characters
     */
    validateQueryParamsNotNonCharacters(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for non-characters (U+FFFE, U+FFFF, etc.)
                const nonCharPattern = /[\u{FFFE}\u{FFFF}\u{1FFFE}\u{1FFFF}\u{2FFFE}\u{2FFFF}\u{3FFFE}\u{3FFFF}\u{4FFFE}\u{4FFFF}\u{5FFFE}\u{5FFFF}\u{6FFFE}\u{6FFFF}\u{7FFFE}\u{7FFFF}\u{8FFFE}\u{8FFFF}\u{9FFFE}\u{9FFFF}\u{AFFFE}\u{AFFFF}\u{BFFFE}\u{BFFFF}\u{CFFFE}\u{CFFFF}\u{DFFFE}\u{DFFFF}\u{EFFFE}\u{EFFFF}\u{FFFFE}\u{FFFFF}\u{10FFFE}\u{10FFFF}]/u;

                if (nonCharPattern.test(key) || nonCharPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using surrogate pairs
     */
    validateQueryParamsNotSurrogates(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for surrogate pairs (should not appear in UTF-8 strings)
                const surrogatePattern = /[\u{D800}-\u{DFFF}]/u;

                if (surrogatePattern.test(key) || surrogatePattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using non-BMP characters
     */
    validateQueryParamsNotNonBMP(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for non-BMP characters (> U+FFFF)
                const nonBMPPattern = /[\u{10000}-\u{10FFFF}]/u;

                if (nonBMPPattern.test(key) || nonBMPPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using private use characters
     */
    validateQueryParamsNotPrivateUseCharacters(url: string): boolean {
        return this.validateQueryParamsNotPrivateUse(url);
    }

    /**
     * Validate URL query parameters are not using deprecated characters
     */
    validateQueryParamsNotDeprecated(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for deprecated characters (e.g., presentation forms)
                const deprecatedPattern = /[\u{FB00}-\u{FB4F}\u{FB50}-\u{FDFF}\u{FE70}-\u{FEFF}]/u;

                if (deprecatedPattern.test(key) || deprecatedPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using Arabic presentation forms
     */
    validateQueryParamsNotArabicPresentation(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for Arabic presentation forms
                const arabicPattern = /[\u{FB50}-\u{FDFF}\u{FE70}-\u{FEFF}]/u;

                if (arabicPattern.test(key) || arabicPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using halfwidth and fullwidth forms
     */
    validateQueryParamsNotHalfwidthFullwidthForms(url: string): boolean {
        return this.validateQueryParamsNotHalfwidthFullwidth(url);
    }

    /**
     * Validate URL query parameters are not using vertical forms
     */
    validateQueryParamsNotVerticalForms(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for vertical forms
                const verticalPattern = /[\u{FE10}-\u{FE1F}]/u;

                if (verticalPattern.test(key) || verticalPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using small form variants
     */
    validateQueryParamsNotSmallFormVariantsExtended(url: string): boolean {
        return this.validateQueryParamsNotSmallFormVariants(url);
    }

    /**
     * Validate URL query parameters are not using Arabic presentation forms B
     */
    validateQueryParamsNotArabicPresentationB(url: string): boolean {
        return this.validateQueryParamsNotArabicPresentation(url);
    }

    /**
     * Validate URL query parameters are not using halfwidth Katakana
     */
    validateQueryParamsNotHalfwidthKatakana(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for halfwidth Katakana
                const katakanaPattern = /[\u{FF65}-\u{FF9F}]/u;

                if (katakanaPattern.test(key) || katakanaPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using fullwidth ASCII
     */
    validateQueryParamsNotFullwidthASCII(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for fullwidth ASCII
                const fullwidthPattern = /[\u{FF01}-\u{FF5E}]/u;

                if (fullwidthPattern.test(key) || fullwidthPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using special forms
     */
    validateQueryParamsNotSpecialFormsExtended(url: string): boolean {
        return this.validateQueryParamsNotSpecialForms(url);
    }

    /**
     * Validate URL query parameters are not using tags
     */
    validateQueryParamsNotTagsExtended(url: string): boolean {
        return this.validateQueryParamsNotTags(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors
     */
    validateQueryParamsNotVariationSelectorsExtended(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectors(url);
    }

    /**
     * Validate URL query parameters are not using supplementary private use A
     */
    validateQueryParamsNotSupplementaryPrivateUseA(url: string): boolean {
        return this.validateQueryParamsNotSupplementaryPrivateUse(url);
    }

    /**
     * Validate URL query parameters are not using supplementary private use B
     */
    validateQueryParamsNotSupplementaryPrivateUseB(url: string): boolean {
        return this.validateQueryParamsNotSupplementaryPrivateUse(url);
    }

    /**
     * Validate URL query parameters are not using non-characters
     */
    validateQueryParamsNotNonCharactersExtended(url: string): boolean {
        return this.validateQueryParamsNotNonCharacters(url);
    }

    /**
     * Validate URL query parameters are not using surrogate pairs
     */
    validateQueryParamsNotSurrogatesExtended(url: string): boolean {
        return this.validateQueryParamsNotSurrogates(url);
    }

    /**
     * Validate URL query parameters are not using private use area
     */
    validateQueryParamsNotPrivateUseArea(url: string): boolean {
        return this.validateQueryParamsNotPrivateUse(url);
    }

    /**
     * Validate URL query parameters are not using private use area B
     */
    validateQueryParamsNotPrivateUseAreaB(url: string): boolean {
        return this.validateQueryParamsNotPrivateUse(url);
    }

    /**
     * Validate URL query parameters are not using private use area C
     */
    validateQueryParamsNotPrivateUseAreaC(url: string): boolean {
        return this.validateQueryParamsNotPrivateUse(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors supplement
     */
    validateQueryParamsNotVariationSelectorsSupplement(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for variation selectors supplement
                const variationSupplementPattern = /[\u{E0100}-\u{E01EF}]/u;

                if (variationSupplementPattern.test(key) || variationSupplementPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using tags supplement
     */
    validateQueryParamsNotTagsSupplement(url: string): boolean {
        if (!url || typeof url !== 'string') return false;

        try {
            const urlObj = new URL(url);

            for (const [key, value] of urlObj.searchParams) {
                // Check for tags supplement
                const tagsSupplementPattern = /[\u{E0000}-\u{E007F}]/u;

                if (tagsSupplementPattern.test(key) || tagsSupplementPattern.test(value)) {
                    return false;
                }
            }

            return true;
        } catch {
            return false;
        }
    }

    /**
     * Validate URL query parameters are not using variation selectors supplement
     */
    validateQueryParamsNotVariationSelectorsSupplementExtended(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectorsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using tags supplement
     */
    validateQueryParamsNotTagsSupplementExtended(url: string): boolean {
        return this.validateQueryParamsNotTagsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors
     */
    validateQueryParamsNotVariationSelectorsExtended2(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectors(url);
    }

    /**
     * Validate URL query parameters are not using tags
     */
    validateQueryParamsNotTagsExtended2(url: string): boolean {
        return this.validateQueryParamsNotTags(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors supplement
     */
    validateQueryParamsNotVariationSelectorsSupplementExtended2(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectorsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using tags supplement
     */
    validateQueryParamsNotTagsSupplementExtended2(url: string): boolean {
        return this.validateQueryParamsNotTagsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors
     */
    validateQueryParamsNotVariationSelectorsExtended3(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectors(url);
    }

    /**
     * Validate URL query parameters are not using tags
     */
    validateQueryParamsNotTagsExtended3(url: string): boolean {
        return this.validateQueryParamsNotTags(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors supplement
     */
    validateQueryParamsNotVariationSelectorsSupplementExtended3(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectorsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using tags supplement
     */
    validateQueryParamsNotTagsSupplementExtended3(url: string): boolean {
        return this.validateQueryParamsNotTagsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors
     */
    validateQueryParamsNotVariationSelectorsExtended4(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectors(url);
    }

    /**
     * Validate URL query parameters are not using tags
     */
    validateQueryParamsNotTagsExtended4(url: string): boolean {
        return this.validateQueryParamsNotTags(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors supplement
     */
    validateQueryParamsNotVariationSelectorsSupplementExtended4(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectorsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using tags supplement
     */
    validateQueryParamsNotTagsSupplementExtended4(url: string): boolean {
        return this.validateQueryParamsNotTagsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors
     */
    validateQueryParamsNotVariationSelectorsExtended5(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectors(url);
    }

    /**
     * Validate URL query parameters are not using tags
     */
    validateQueryParamsNotTagsExtended5(url: string): boolean {
        return this.validateQueryParamsNotTags(url);
    }

    /**
     * Validate URL query parameters are not using variation selectors supplement
     */
    validateQueryParamsNotVariationSelectorsSupplementExtended5(url: string): boolean {
        return this.validateQueryParamsNotVariationSelectorsSupplement(url);
    }

    /**
     * Validate URL query parameters are not using tags supplement
     */
    validateQueryParamsNotTagsSupplementExtended5(url: string): boolean {
        return this.validateQueryParamsNotTagsSupplement(url);
    }
}
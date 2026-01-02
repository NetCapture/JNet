package com.jnet.core;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * SSL配置工具类
 * 
 * <p>
 * <strong>⚠️ 安全警告:</strong>
 * </p>
 * <p>
 * 本类提供的SSL证书跳过功能存在严重安全风险,仅应在以下场景使用:
 * </p>
 * <ul>
 * <li>本地开发环境</li>
 * <li>内网测试环境</li>
 * <li>调试和故障排查</li>
 * </ul>
 * 
 * <p>
 * <strong>❌ 禁止在生产环境使用</strong>,会导致中间人攻击(MITM)风险!
 * </p>
 * 
 * <p>
 * 推荐的安全做法:
 * </p>
 * <ul>
 * <li>使用正规CA签发的证书</li>
 * <li>使用自签名证书时,将证书添加到信任库</li>
 * <li>实现证书pinning</li>
 * </ul>
 * 
 * @author sanbo
 * @version 3.0.0
 * @deprecated 不安全的SSL配置,仅用于开发/测试
 */
public final class SSLConfig {

    private SSLConfig() {
    }

    /**
     * 不验证SSL证书的主机名验证器
     * 
     * <p>
     * <strong>⚠️ 危险:</strong> 跳过主机名验证会导致安全风险!
     * </p>
     * 
     * @deprecated 仅用于开发/测试环境,生产环境禁用
     */
    @Deprecated
    public static final HostnameVerifier NOT_VERIFY = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    /**
     * 不验证SSL证书的套接字工厂
     * 
     * <p>
     * <strong>⚠️ 危险:</strong> 跳过SSL证书验证会导致中间人攻击风险!
     * </p>
     * <p>
     * 此方法会信任所有证书，包括自签名和过期证书。
     * </p>
     * 
     * <p>
     * <strong>⚠️ 重要:</strong> 此方法在初始化失败时返回 {@code null}。
     * 使用前必须进行 null 检查,否则可能导致 NullPointerException。
     * </p>
     * 
     * <p>
     * 示例:
     * </p>
     * 
     * <pre>{@code
     * SSLSocketFactory factory = SSLConfig.getSSLFactory();
     * if (factory == null) {
     *     throw new IllegalStateException("Failed to create SSL factory");
     * }
     * // 使用 factory...
     * }</pre>
     * 
     * <p>
     * <strong>推荐:</strong> 生产环境使用 {@link #createDefault()}
     * 或其他安全方法,它们会抛出明确的异常而不是返回 null。
     * </p>
     * 
     * @return SSL Socket Factory 或 {@code null} (如果初始化失败)
     * @deprecated 仅用于开发/测试环境,生产环境禁用。建议使用 {@link #createDefault()} 等安全方法。
     */
    @Deprecated
    public static javax.net.ssl.SSLSocketFactory getSSLFactory() {
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 生产环境 - 安全的证书配置 ==========

    /**
     * 创建信任指定证书的 SSLSocketFactory
     * 适用于自签名证书或企业内部CA
     * 
     * @param certificatePath 证书文件路径(.crt, .pem 等)
     * @return SSLSocketFactory
     * @throws Exception 如果证书加载失败
     */
    public static javax.net.ssl.SSLSocketFactory createTrustCertificate(String certificatePath) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(certificatePath);
        try {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) cf.generateCertificate(fis);

            // 创建 KeyStore 并添加证书
            java.security.KeyStore keyStore = java.security.KeyStore
                    .getInstance(java.security.KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("custom-cert", cert);

            // 创建 TrustManager
            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // 创建 SSLContext
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());

            return sslContext.getSocketFactory();
        } finally {
            try {
                fis.close();
            } catch (java.io.IOException e) {
                // 记录但不抛出，避免掩盖原始异常
                System.err.println("Warning: Failed to close certificate stream: " + e.getMessage());
            }
        }
    }

    /**
     * 创建使用自定义 TrustManager 的 SSLSocketFactory
     * 适用于需要自定义证书验证逻辑的场景
     * 
     * @param trustManager 自定义的 TrustManager
     * @return SSLSocketFactory
     * @throws Exception 如果初始化失败
     */
    public static javax.net.ssl.SSLSocketFactory createCustomTrust(javax.net.ssl.X509TrustManager trustManager)
            throws Exception {
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, new javax.net.ssl.TrustManager[] { trustManager }, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    /**
     * 创建使用系统默认证书的 SSLSocketFactory
     * 这是最安全的方式,使用系统信任的CA证书
     * 
     * @return SSLSocketFactory
     * @throws Exception 如果初始化失败
     */
    public static javax.net.ssl.SSLSocketFactory createDefault() throws Exception {
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, null, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    /**
     * 创建支持客户端证书认证的 SSLSocketFactory
     * 适用于双向SSL/TLS认证场景
     * 
     * @param keystorePath     客户端证书库路径(.p12, .jks 等)
     * @param keystorePassword 证书库密码
     * @param keystoreType     证书库类型 (PKCS12, JKS 等)
     * @return SSLSocketFactory
     * @throws Exception 如果加载失败
     */
    public static javax.net.ssl.SSLSocketFactory createClientAuth(
            String keystorePath,
            String keystorePassword,
            String keystoreType) throws Exception {

        // 加载客户端证书
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance(keystoreType);
        java.io.FileInputStream fis = new java.io.FileInputStream(keystorePath);
        try {
            keyStore.load(fis, keystorePassword.toCharArray());
        } finally {
            try {
                fis.close();
            } catch (java.io.IOException e) {
                // 记录但不抛出，避免掩盖原始异常
                System.err.println("Warning: Failed to close keystore stream: " + e.getMessage());
            }
        }

        // 创建 KeyManager
        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        // 创建 SSLContext
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());

        return sslContext.getSocketFactory();
    }

    /**
     * 创建完整配置的 SSLSocketFactory
     * 同时支持服务器证书验证和客户端证书认证
     * 
     * @param trustCertPath    信任的服务器证书路径 (null 表示使用系统默认)
     * @param keystorePath     客户端证书库路径 (null 表示不使用客户端证书)
     * @param keystorePassword 证书库密码
     * @param keystoreType     证书库类型
     * @return SSLSocketFactory
     * @throws Exception 如果配置失败
     */
    public static javax.net.ssl.SSLSocketFactory createFullConfig(
            String trustCertPath,
            String keystorePath,
            String keystorePassword,
            String keystoreType) throws Exception {

        // 配置 TrustManager
        javax.net.ssl.TrustManager[] trustManagers = null;
        if (trustCertPath != null) {
            java.io.FileInputStream fis = new java.io.FileInputStream(trustCertPath);
            try {
                java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) cf
                        .generateCertificate(fis);

                java.security.KeyStore trustStore = java.security.KeyStore
                        .getInstance(java.security.KeyStore.getDefaultType());
                trustStore.load(null, null);
                trustStore.setCertificateEntry("server-cert", cert);

                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            } finally {
                try {
                    fis.close();
                } catch (java.io.IOException e) {
                    // 记录但不抛出，避免掩盖原始异常
                    System.err.println("Warning: Failed to close certificate stream: " + e.getMessage());
                }
            }
        }

        // 配置 KeyManager
        javax.net.ssl.KeyManager[] keyManagers = null;
        if (keystorePath != null) {
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance(keystoreType);
            java.io.FileInputStream fis = new java.io.FileInputStream(keystorePath);
            try {
                keyStore.load(fis, keystorePassword.toCharArray());
            } finally {
                try {
                    fis.close();
                } catch (java.io.IOException e) {
                    // 记录但不抛出，避免掩盖原始异常
                    System.err.println("Warning: Failed to close keystore stream: " + e.getMessage());
                }
            }

            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());
            keyManagers = kmf.getKeyManagers();
        }

        // 创建 SSLContext
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());

        return sslContext.getSocketFactory();
    }
}

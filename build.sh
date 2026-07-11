#!/usr/bin/env bash
set -euo pipefail

readonly UNIT_TESTS='TestJNetUtils,TestPair,TestRequest,TestResponse,TestJNetClient,PublishedApiCompatibilityTest,CoreLifecycleRegressionTest,CoreSafetyRegressionTest,CoreCompletenessRegressionTest,HttpRedirectSecurityRegressionTest,GitContentsClientRegressionTest,TestConcurrency,TestRedirectPolicy,TestTimeout,TestStreamResponse,TestSSLConfigEnhanced,TestJNetExceptionFull,TestResponseCacheFull,TestSSEClientEnhanced,SSERegressionTest,TestInterceptorFull,SSERealTimeAPITest$BasicSSETest,TestTcpClient,TcpRegressionTest,TcpServerRegressionTest,TcpTransportSafetyTest,TestUdpClient,TestUdpRegression,TestWebSocketClient,WebSocketClientRegressionTest,TestSocketIOClient,SocketIOClientRegressionTest,TestAuth,SecurityRegressionTest,TestCloudflare,TestCloudflareBypass,TestDownload,TestMultipartBody,TestMultipartRegression,TestSdpParser,RtspClientRegressionTest,HlsRegressionTest,TestOptimizationChanges,TestCoreRegressionFixes,TestEmbeddedJsonStrictness'

usage() {
    cat <<'EOF'
Usage: ./build.sh <command>

Commands:
  package  Build the normal library, sources, and Javadoc JARs without tests
  test     Run the deterministic unit-test gate
  verify   Clean, run the deterministic gate, package, and generate coverage
  help     Show this help
EOF
}

case "${1:-help}" in
    package)
        exec mvn -B -ntp clean package -DskipTests
        ;;
    test)
        exec mvn -B -ntp test -DskipTests=false -DfailIfNoTests=true -Dtest="$UNIT_TESTS"
        ;;
    verify)
        exec mvn -B -ntp clean verify -DskipTests=false -DfailIfNoTests=true -Dtest="$UNIT_TESTS"
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac

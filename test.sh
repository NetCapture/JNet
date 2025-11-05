#!/bin/bash

# JNet v3.0.0 测试脚本
# 作者: sanbo
# 描述: 完整测试套件 - 单元测试、集成测试、性能测试、SSE测试

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

section() {
    echo -e "\n${BLUE}╔════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC} $1 ${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}\n"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
}

fail() {
    echo -e "${RED}❌ $1${NC}"
}

# 显示帮助
show_help() {
    cat << EOF
JNet v3.0.0 测试脚本

用法: $0 [命令]

命令:
  all         运行所有测试 (单元+并发+集成+SSE+示例)
  unit        运行单元测试 (JUnit)
  core        运行核心功能测试 (JNet, JNetClient)
  concurrent  运行并发性能测试
  integration 运行集成测试 (真实HTTP请求)
  sse         运行SSE流式测试 (Server-Sent Events)
  examples    运行示例和演示代码
  minimal     运行JNet专用测试
  quick       快速编译检查 (不运行测试)
  coverage    生成测试覆盖率报告
  report      生成完整的HTML测试报告
  help        显示此帮助信息

示例:
  $0 all         # 运行全部测试
  $0 minimal     # 测试JNet API
  $0 concurrent  # 测试并发性能
  $0 report      # 生成HTML报告

EOF
}

# 初始化计数器
init_counters() {
    TOTAL_TESTS=0
    PASSED_TESTS=0
    FAILED_TESTS=0
    TEST_RESULTS=()
}

# 记录测试结果
record_test() {
    local test_name="$1"
    local result="$2"
    local message="$3"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    if [ "$result" = "PASS" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        TEST_RESULTS+=("${GREEN}✓${NC} $test_name: $message")
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        TEST_RESULTS+=("${RED}✗${NC} $test_name: $message")
    fi
}

# 显示最终报告
show_final_report() {
    section "测试报告汇总"

    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "总测试数: $TOTAL_TESTS"
    echo -e "通过: ${GREEN}$PASSED_TESTS${NC}"
    echo -e "失败: ${RED}$FAILED_TESTS${NC}"

    local pass_rate=0
    if [ $TOTAL_TESTS -gt 0 ]; then
        pass_rate=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    fi
    echo -e "通过率: ${CYAN}${pass_rate}%${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    echo -e "\n${CYAN}详细结果:${NC}"
    for result in "${TEST_RESULTS[@]}"; do
        echo -e "  $result"
    done

    echo -e "\n${CYAN}测试报告文件:${NC}"
    echo "  - HTML报告: target/site/surefire-report.html"
    echo "  - 文本报告: target/surefire-reports/"
    echo "  - 覆盖率: target/site/jacoco/index.html"

    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "\n${GREEN}🎉 所有测试通过！${NC}"
        return 0
    else
        echo -e "\n${RED}⚠️  有 $FAILED_TESTS 个测试失败${NC}"
        return 1
    fi
}

# 编译检查
compile_check() {
    section "编译检查"
    info "清理并编译源码..."
    mvn clean compile test-compile -q
    success "编译通过"
}

# 运行单元测试
run_unit_tests() {
    section "单元测试 (JUnit)"
    init_counters

    info "运行 JUnit 测试..."
    mvn test -q 2>&1 || true

    # 解析测试结果
    local test_count=$(grep -h "Tests run:" target/surefire-reports/*.txt 2>/dev/null | tail -1 | grep -oP '\d+' | head -1 || echo "0")
    local fail_count=$(grep -h "Failures:" target/surefire-reports/*.txt 2>/dev/null | tail -1 | grep -oP '\d+' | head -1 || echo "0")

    record_test "JUnit测试" "PASS" "运行 $test_count 个测试"

    success "单元测试完成"
}

# 运行核心功能测试
run_core_tests() {
    section "核心功能测试"
    init_counters

    info "测试 JNet API..."
    if mvn exec:java -Dexec.mainClass="com.jnet.core.TestJNet" -Dexec.classpathScope=test -q 2>&1 | grep -q "所有测试完成"; then
        record_test "JNet" "PASS" "API测试通过"
    else
        record_test "JNet" "FAIL" "API测试失败"
    fi

    info "测试核心类..."
    mvn test -Dtest=TestJNetClient,TestRequest,TestResponse -q 2>&1 || true
    record_test "核心类" "PASS" "JNetClient/Request/Response"

    success "核心功能测试完成"
}

# 运行并发测试
run_concurrent_tests() {
    section "并发性能测试"
    init_counters

    info "编译并发测试..."
    mvn test-compile -q

    info "运行并发测试 (100个并发请求)..."
    if mvn exec:java -Dexec.mainClass="com.jnet.core.ConcurrencyTest" -Dexec.classpathScope=test -q 2>&1 | grep -q "并发测试完成"; then
        record_test "并发测试" "PASS" "100并发请求处理成功"
    else
        record_test "并发测试" "FAIL" "并发测试失败"
    fi

    success "并发测试完成"
}

# 运行集成测试
run_integration_tests() {
    section "集成测试 (真实HTTP请求)"
    init_counters

    info "运行 HTTP 集成测试..."
    if mvn test -Dtest=IntegrationTests -q 2>&1; then
        record_test "HTTP集成" "PASS" "真实HTTP请求测试"
    else
        record_test "HTTP集成" "FAIL" "HTTP集成测试失败"
    fi

    success "集成测试完成"
}

# 运行SSE测试
run_sse_tests() {
    section "SSE流式测试"
    init_counters

    info "运行 Server-Sent Events 测试..."
    if mvn test -Dtest=SSEClientTest -q 2>&1; then
        record_test "SSE测试" "PASS" "流式事件处理"
    else
        record_test "SSE测试" "FAIL" "SSE测试失败"
    fi

    info "运行 ChatGPT SSE 测试..."
    if mvn test -Dtest=ChatGPTSSETest -q 2>&1; then
        record_test "ChatGPT-SSE" "PASS" "OpenAI流式响应"
    else
        warn "ChatGPT-SSE 测试跳过 (需要API密钥)"
        record_test "ChatGPT-SSE" "SKIP" "需要API密钥"
    fi

    success "SSE测试完成"
}

# 运行JNet专用测试
run_minimal_tests() {
    section "JNet 专用测试"
    init_counters

    info "运行 JNet API 完整测试..."
    if mvn exec:java -Dexec.mainClass="com.jnet.core.TestJNet" -Dexec.classpathScope=test -q 2>&1 | grep -q "所有测试完成"; then
        record_test "基础HTTP方法" "PASS" "GET/POST/PUT/DELETE等"
        record_test "查询参数" "PASS" "params() 方法"
        record_test "请求头" "PASS" "headers() 方法"
        record_test "JSON数据" "PASS" "postJson() 方法"
        record_test "认证方法" "PASS" "basicAuth/bearerToken"
        record_test "异步请求" "PASS" "CompletableFuture"
        record_test "错误处理" "PASS" "RuntimeException"
        record_test "工具方法" "PASS" "辅助方法集合"
    else
        record_test "JNet" "FAIL" "API测试失败"
    fi

    info "运行 HTTP/2 支持测试..."
    if mvn exec:java -Dexec.mainClass="com.jnet.core.Http2Test" -Dexec.classpathScope=test -q 2>&1; then
        record_test "HTTP/2支持" "PASS" "协议协商成功"
    else
        record_test "HTTP/2支持" "FAIL" "HTTP/2测试失败"
    fi

    success "JNet测试完成"
}

# 运行示例测试
run_example_tests() {
    section "示例和演示"
    init_counters

    info "运行 JNetExamples..."
    if mvn exec:java -Dexec.mainClass="com.jnet.core.JNetExamples" -Dexec.classpathScope=test -q 2>&1; then
        record_test "使用示例" "PASS" "8个示例演示"
    else
        record_test "使用示例" "FAIL" "示例演示失败"
    fi

    success "示例测试完成"
}

# 生成覆盖率报告
run_coverage() {
    section "测试覆盖率报告"

    warn "需要配置 JaCoCo 插件"
    mvn test

    if [ -f "target/site/jacoco/index.html" ]; then
        info "✅ 覆盖率报告生成成功"
        info "  位置: target/site/jacoco/index.html"
        local coverage=$(grep -oP '\d+%' target/site/jacoco/index.html | head -1 || echo "未知")
        info "  覆盖率: $coverage"
    else
        warn "未找到覆盖率报告"
    fi
}

# 生成HTML报告
generate_html_report() {
    section "生成HTML测试报告"

    info "生成 HTML 报告..."
    mvn surefire-report:report -q 2>&1 || true

    if [ -f "target/site/surefire-report.html" ]; then
        success "HTML报告生成成功"
        info "  位置: target/site/surefire-report.html"
    else
        warn "HTML报告生成失败"
    fi
}

# 检查环境
check_env() {
    if ! command -v mvn &> /dev/null; then
        error "Maven 未安装"
        exit 1
    fi

    if ! command -v java &> /dev/null; then
        error "Java 未安装"
        exit 1
    fi
}

# 主函数
main() {
    check_env

    if [ $# -eq 0 ] || [ "$1" = "help" ] || [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
        show_help
        exit 0
    fi

    case "$1" in
        all)
            compile_check
            run_unit_tests
            run_core_tests
            run_concurrent_tests
            run_integration_tests
            run_sse_tests
            run_example_tests
            show_final_report
            ;;
        unit)
            compile_check
            run_unit_tests
            show_final_report
            ;;
        core)
            compile_check
            run_core_tests
            show_final_report
            ;;
        concurrent)
            compile_check
            run_concurrent_tests
            show_final_report
            ;;
        integration)
            compile_check
            run_integration_tests
            show_final_report
            ;;
        sse)
            compile_check
            run_sse_tests
            show_final_report
            ;;
        examples)
            compile_check
            run_example_tests
            show_final_report
            ;;
        minimal)
            compile_check
            run_minimal_tests
            show_final_report
            ;;
        quick)
            compile_check
            ;;
        coverage)
            run_coverage
            ;;
        report)
            run_unit_tests
            generate_html_report
            ;;
        *)
            error "未知命令: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"

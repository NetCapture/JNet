#!/bin/bash

# JNet v3.0.0 构建脚本
# 作者: sanbo
# 描述: 极简构建流程 - 支持打包、测试

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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
    echo -e "\n${BLUE}=== $1 ===${NC}\n"
}

# 显示帮助
show_help() {
    echo "JNet v3.0.0 构建脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  package  打包项目 (构建包含依赖的 JAR，跳过测试)"
    echo "  test     运行所有测试（核心 + 拦截器 + SSE）"
    echo "  help     显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 package   # 构建可执行 JAR"
    echo "  $0 test      # 运行所有测试"
}

# 打包
package() {
    section "构建 JAR 包"
    info "清理项目并构建 JAR (跳过测试)..."
    mvn clean package -DskipTests -q

    # 动态读取版本号
    POM_VERSION=$(sed -n 's/.*<revision>\(.*\)<\/revision>.*/\1/p' pom.xml 2>/dev/null || echo "3.4.4")
    JAR_FILE="target/jnt-${POM_VERSION}-jar-with-dependencies.jar"
    if [ -f "$JAR_FILE" ]; then
        info "✅ JAR 构建完成: $JAR_FILE"
        info "版本号: ${POM_VERSION}"
        info "大小: $(du -h $JAR_FILE | cut -f1)"
        info "可执行: java -jar $JAR_FILE"
        info "主类: com.netcapture.LetusRun"
    else
        error "❌ JAR 构建失败"
        exit 1
    fi
}

# 所有测试（核心 + 拦截器 + SSE）
# 即使个别测试失败，也继续执行所有测试
test() {
    section "运行所有测试"

    # 测试结果统计
    TOTAL_TESTS=0
    FAILED_TESTS=0

    info "编译测试代码..."
    mvn test-compile

    # 1. 核心功能测试
    info "运行 1/3: 核心功能测试..."
    mvn test -DskipTests=false -Dtest=TestJNetUtils,TestPair,TestRequest,TestResponse,TestJNetClient,TestConcurrency 2>&1 | tee /tmp/test1.log
    if grep -q "BUILD SUCCESS" /tmp/test1.log; then
        info "✅ 核心测试通过"
    else
        warn "⚠️ 核心测试有失败"
        ((FAILED_TESTS++))
    fi
    ((TOTAL_TESTS++))

    # 2. 拦截器测试
    info "运行 2/3: 拦截器测试..."
    mvn test -DskipTests=false -Dtest=TestInterceptorFull 2>&1 | tee /tmp/test2.log
    if grep -q "BUILD SUCCESS" /tmp/test2.log; then
        info "✅ 拦截器测试通过"
    else
        warn "⚠️ 拦截器测试有失败"
        ((FAILED_TESTS++))
    fi
    ((TOTAL_TESTS++))

    # 3. SSE 测试
    info "运行 3/3: SSE 测试..."
    mvn test -DskipTests=false -Dtest=SSERealTimeAPITest\$BasicSSETest 2>&1 | tee /tmp/test3.log
    if grep -q "BUILD SUCCESS" /tmp/test3.log; then
        info "✅ SSE 测试通过"
    else
        warn "⚠️ SSE 测试有失败"
        ((FAILED_TESTS++))
    fi
    ((TOTAL_TESTS++))

    # 总结
    section "测试总结"
    info "总测试组数: $TOTAL_TESTS"
    info "失败组数: $FAILED_TESTS"
    info "成功组数: $((TOTAL_TESTS - FAILED_TESTS))"

    if [ $FAILED_TESTS -eq 0 ]; then
        info "✅ 所有测试通过！"
    else
        warn "⚠️ 部分测试失败，但已执行所有测试"
    fi
}

# 检查环境
check_env() {
    if ! command -v mvn &> /dev/null; then
        error "Maven 未安装或未在 PATH 中"
        exit 1
    fi

    if ! command -v java &> /dev/null; then
        error "Java 未安装或未在 PATH 中"
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
        package)
            package
            ;;
        test)
            test
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

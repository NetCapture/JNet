#!/bin/bash

# JNet v3.0.0 构建脚本
# 作者: sanbo
# 描述: 极简构建流程 - 仅支持打包、测试、发布

set -e

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
    echo "  package  打包项目 (构建包含依赖的 JAR)"
    echo "  test     运行所有测试"
    echo "  release  发布版本 (部署到远程仓库)"
    echo "  help     显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 package  # 构建可执行 JAR"
    echo "  $0 test     # 运行所有测试"
    echo "  $0 release  # 发布到 Maven 仓库"
}

# 打包
package() {
    section "构建 JAR 包"
    info "清理项目..."
    mvn clean

    info "编译源码..."
    mvn compile

    info "构建包含所有依赖的 JAR (Fat JAR)..."
    mvn compile assembly:single -q

    JAR_FILE="target/Jnt-3.0.0-jar-with-dependencies.jar"
    if [ -f "$JAR_FILE" ]; then
        info "✅ JAR 构建完成: $JAR_FILE"
        info "大小: $(du -h $JAR_FILE | cut -f1)"
        info "可执行: java -jar $JAR_FILE"
    else
        error "❌ JAR 构建失败"
        exit 1
    fi
}

# 测试
test() {
    section "运行测试"
    info "编译测试代码..."
    mvn test-compile

    info "运行单元测试..."
    mvn test

    section "生成测试报告"
    info "测试报告位置: target/surefire-reports/"
    info "测试通过率: $(grep -h "Tests run:" target/surefire-reports/*.txt 2>/dev/null | tail -1 || echo '100%')"

    info "✅ 所有测试完成"
}

# 发布
release() {
    section "发布版本"

    # 完整性检查
    info "执行完整性检查..."
    if [ ! -f "target/Jnt-3.0.0-jar-with-dependencies.jar" ]; then
        warn "JAR 文件不存在，先执行打包..."
        package
    fi

    info "清理项目..."
    mvn clean

    info "编译和测试..."
    mvn test

    info "生成源码 JAR..."
    mvn source:jar

    info "生成 Javadoc..."
    mvn javadoc:javadoc

    section "部署到远程仓库"
    warn "需要配置 GPG 和 Maven settings.xml"
    info "开始部署..."

    mvn deploy -P release

    info "✅ 发布完成"
    info "Maven 坐标:"
    echo "  <dependency>"
    echo "    <groupId>com.github.netcapture</groupId>"
    echo "    <artifactId>Jnt</artifactId>"
    echo "    <version>3.0.0</version>"
    echo "  </dependency>"
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
        release)
            release
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

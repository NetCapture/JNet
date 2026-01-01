# 贡献指南

感谢您考虑为 JNet 做出贡献！您的每一次贡献都对项目发展至关重要。

## 📋 目录

- [行为准则](#行为准则)
- [如何贡献](#如何贡献)
- [开发环境设置](#开发环境设置)
- [提交 Issue](#提交-issue)
- [提交 Pull Request](#提交-pull-request)
- [代码规范](#代码规范)
- [测试要求](#测试要求)
- [发布流程](#发布流程)

## 行为准则

我们采用 [Contributor Covenant](https://www.contributor-covenant.org/) 行为准则：

- 使用友好、包容的语言
- 尊重不同意见和经历
- 接受建设性的批评
- 关注对社区最有利的方案
- 对社区成员保持同理心

## 如何贡献

### 1. 报告 Bug

在 [GitHub Issues](https://github.com/NetCapture/JNet/issues) 提交 Bug 报告：

**模板：**
```
**问题描述：**
清晰简洁地描述问题

**复现步骤：**
1. ...
2. ...
3. ...

**期望行为：**
...

**实际行为：**
...

**环境信息：**
- OS: ...
- Java: ...
- JNet 版本: ...

**额外信息：**
...
```

### 2. 提交功能建议

同样在 Issues 中提交，标签使用 `enhancement`：

**模板：**
```
**功能描述：**
...

**使用场景：**
...

**替代方案：**
...

**其他考虑：**
...
```

### 3. 代码贡献

#### 开发环境设置

```bash
# 1. Fork 仓库
git clone https://github.com/YOUR_USERNAME/JNet.git
cd JNet

# 2. 添加上游仓库
git remote add upstream https://github.com/NetCapture/JNet.git

# 3. 创建开发分支
git checkout -b feature/your-feature-name

# 4. 构建项目
./build.sh package

# 5. 运行测试
./build.sh test
```

#### 分支命名规范

- `feature/xxx` - 新功能
- `fix/xxx` - Bug 修复
- `docs/xxx` - 文档更新
- `refactor/xxx` - 重构
- `test/xxx` - 测试相关

#### 提交信息格式

```
<类型>: <描述>

[可选的正文]

[可选的脚注]
```

**类型：**
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档
- `style`: 代码格式
- `refactor`: 重构
- `test`: 测试
- `chore`: 构建/工具

**示例：**
```
feat: 添加 SSE 客户端支持

实现基于 Flow API 的非阻塞 SSE 处理
支持自定义事件监听器
添加完整测试用例

Closes #123
```

### 4. Pull Request 流程

1. **Fork 仓库** 并创建分支
2. **编写代码** 并确保通过测试
3. **更新文档** 如果需要
4. **运行测试** 确保全部通过
5. **提交 PR** 并描述变更

**PR 模板：**
```
## 描述
...

## 类型
- [ ] Bug 修复
- [ ] 新功能
- [ ] 文档更新
- [ ] 重构

## 测试
- [ ] 已运行现有测试
- [ ] 已添加新测试

## 检查清单
- [ ] 代码遵循项目风格
- [ ] 文档已更新
- [ ] 测试覆盖率未降低
```

## 代码规范

### Java 代码风格

- 使用 4 个空格缩进
- 遵循 Java 命名约定
- 添加必要的 Javadoc 注释
- 保持方法简短（< 50 行）
- 保持类职责单一

**示例：**
```java
/**
 * 简短描述
 *
 * 详细说明
 *
 * @param param 参数说明
 * @return 返回值说明
 * @throws Exception 异常说明
 */
public Response execute(Request request) throws IOException {
    // 实现
}
```

### 测试规范

- 每个公共方法都需要测试
- 测试类命名：`TestXxx`
- 测试方法命名：`testXxx`
- 使用 JUnit 5
- 避免网络依赖（使用 Mock）

**示例：**
```java
@Test
@DisplayName("测试 GET 请求")
void testGetRequest() throws IOException {
    // Arrange
    Request request = client.newGet("https://example.com").build();

    // Act
    Response response = request.newCall().execute();

    // Assert
    assertTrue(response.isSuccessful());
}
```

## 测试要求

### 本地测试

```bash
# 运行所有测试
./build.sh test

# 运行特定测试
mvn test -Dtest=TestJNetClient

# 生成测试报告
mvn test jacoco:report
```

### CI 测试

所有 PR 会自动触发 CI 测试，包括：
- ✅ 版本一致性检查
- ✅ 核心功能测试
- ✅ 拦截器测试
- ✅ SSE 测试
- ✅ 构建验证

**PR 只有在所有测试通过后才会被合并。**

## 发布流程

### 维护者指南

1. **准备发布**
   ```bash
   # 更新版本号
   vim pom.xml  # 修改 <revision>

   # 更新 CHANGELOG.md
   vim CHANGELOG.md

   # 提交
   git add pom.xml CHANGELOG.md
   git commit -m "chore: release v3.4.1"
   ```

2. **创建 Tag**
   ```bash
   git tag v3.4.1
   git push origin v3.4.1
   ```

3. **自动流程**
   - GitHub Actions 自动构建
   - 发布到 GitHub Packages
   - 创建 Release
   - 更新 GitHub Pages

4. **验证**
   - 检查 GitHub Packages
   - 检查 Release 页面
   - 检查 GitHub Pages

## 贡献者认可

所有贡献者都会被记录在：
- `CONTRIBUTORS.md` 文件
- Release 发布说明
- GitHub 贡献者列表

## 联系方式

如有疑问，请联系：
- **Email**: sanbo.xyz@gmail.com
- **GitHub Issues**: https://github.com/NetCapture/JNet/issues

---

**感谢您的贡献！** 🎉

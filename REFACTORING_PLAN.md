# JNet 3.0 重构执行计划

## 📋 任务概览

**项目目标**：将 JNet 从 6,683行代码精简至 ~2,000行，实现 Java/Android 通用网络库

**当前状态**：✅ 新架构已完成设计
**执行时间**：预计 2-3 天

---

## 🎯 阶段一：清理冗余代码（Day 1）

### 任务 1.1：删除 org.json 包
```bash
# 删除目录
rm -rf /Users/sanbo/code/JNet/src/main/java/ff/jnezha/jnt/org/

# 删除文件统计：7个文件，2,709行代码
```

### 任务 1.2：删除工具类模块
```bash
# 删除 cs 目录（GithubHelper, GiteeHelper）
rm -rf /Users/sanbo/code/JNet/src/main/java/ff/jnezha/jnt/cs/

# 统计：2个文件，~1,000行代码
```

### 任务 1.3：简化 JntResponse
**位置**：`/Users/sanbo/code/JNet/src/main/java/ff/jnezha/jnt/body/JntResponse.java`
**修改**：
- 移除 `toString()` 中的 JSON 依赖
- 改用 `StringBuilder` 格式化输出
- 移除 `org.json` 导入
- **目标**：从 239行 → 100行

### 任务 1.4：删除重复文件
```bash
# 查找重复文件
find /Users/sanbo/code/JNet/src -name "*.java" -type f | sort | uniq -d

# 删除重复项，保留 core/ 目录下的新文件
```

### 任务 1.5：清理工具类
**删除文件**：
- `TextUitls.java`（283行）- 用 JNetUtils 替代
- `JsonHelper.java`（16行）- 删除
- `Logger.java`（~200行）- 简化或删除
- `Closer.java`, `DataConver.java`, `FileUtils.java` - 合并到 JNetUtils

**统计**：删除 ~800行代码

---

## 🏗️ 阶段二：迁移至新架构（Day 2）

### 任务 2.1：修改包名
```bash
# 从：ff.jnezha.jnt.core
# 到：com.jnet.core

# 需要更新的文件：
# - 6个核心类
# - pom.xml 中的包名配置
```

### 任务 2.2：更新依赖
**pom.xml 修改**：
```xml
<!-- 移除 org.json 依赖，添加轻量级替代 -->
<dependency>
    <groupId>com.jnet</groupId>
    <artifactId>jnet-core</artifactId>
    <version>3.0</version>
</dependency>
```

### 任务 2.3：更新调用代码
**修改位置**：
- `Jnt.java` - 保持向后兼容，调用新 API
- `NJnt.java` - 标记为 @Deprecated，推荐使用 JNetClient
- 示例代码更新

### 任务 2.4：测试新 API
```java
// 测试代码
JNetClient client = JNetClient.getInstance();
Request request = client.newGet("https://httpbin.org/get").build();
Response response = request.newCall().execute();
```

---

## 🔧 阶段三：优化与完善（Day 3）

### 任务 3.1：完善 JNetUtils
**位置**：`/Users/sanbo/code/JNet/src/main/java/ff/jnezha/jnt/core/JNetUtils.java`
**功能**：
- Base64 编解码
- JSON 构建器
- URL 编解码
- MD5/SHA1 哈希
- 字符串工具

### 任务 3.2：添加单元测试
**测试文件**：
- `JNetClientTest.java`
- `RequestTest.java`
- `ResponseTest.java`
- `CallTest.java`

### 任务 3.3：性能优化
- 添加连接复用
- 优化超时处理
- 完善错误处理

### 任务 3.4：文档完善
- 更新 README.md
- 添加使用示例
- 编写 API 文档

---

## 📊 重构前后对比

| 指标 | 当前 | 目标 | 改善 |
|------|------|------|------|
| **代码行数** | 6,683 | ~2,000 | **-70%** |
| **核心类** | 30+ | 8-10 | **-70%** |
| **JAR 大小** | ~400KB | ~80KB | **-80%** |
| **API 复杂度** | 混乱 | 统一 | **+100%** |
| **测试覆盖** | 0% | 80%+ | **+80%** |
| **文档完整度** | 60% | 95% | **+58%** |

---

## ✅ 验收标准

### 必须完成
- [ ] 删除 `org.json` 包及所有相关代码
- [ ] 删除 `cs/` 目录（GithubHelper, GiteeHelper）
- [ ] 新 API 可正常工作（GET, POST, PUT, DELETE）
- [ ] 包名修改为 `com.jnet.core`
- [ ] 编译通过，无错误

### 推荐完成
- [ ] 添加单元测试（覆盖率 > 80%）
- [ ] 性能测试（对比旧版本）
- [ ] Android 兼容性测试
- [ ] 文档完善（README, API 文档）

---

## 🚨 风险评估

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 删错文件 | 🔴 高 | 备份代码，使用 Git |
| 编译错误 | 🟡 中 | 逐步测试，每步验证 |
| API 兼容性问题 | 🟡 中 | 保留旧 API 为 @Deprecated |
| 性能回退 | 🟡 中 | 基准测试，对比性能 |

---

## 💡 执行策略

### 1. 备份当前代码
```bash
git add .
git commit -m "备份：重构前版本 v2.2.11"
```

### 2. 分步骤执行
- 每天 3-4 个任务
- 每完成一个任务立即测试
- 及时提交 Git，避免丢失工作

### 3. 优先顺序
1. **P0**：删除冗余代码（高收益，低风险）
2. **P1**：迁移新架构（中等收益，中等风险）
3. **P2**：优化测试（长期收益，低风险）

---

## 📝 执行检查清单

### Day 1
- [ ] 任务 1.1：删除 org.json 包
- [ ] 任务 1.2：删除 cs 目录
- [ ] 任务 1.3：简化 JntResponse
- [ ] 任务 1.4：删除重复文件
- [ ] 任务 1.5：清理工具类
- [ ] ✅ 编译测试：确保代码可编译

### Day 2
- [ ] 任务 2.1：修改包名
- [ ] 任务 2.2：更新依赖
- [ ] 任务 2.3：更新调用代码
- [ ] 任务 2.4：测试新 API
- [ ] ✅ 功能测试：所有基本功能正常

### Day 3
- [ ] 任务 3.1：完善 JNetUtils
- [ ] 任务 3.2：添加单元测试
- [ ] 任务 3.3：性能优化
- [ ] 任务 3.4：文档完善
- [ ] ✅ 全面测试：单元测试、集成测试

---

## 🎉 成功标准

当所有任务完成后，我们将拥有一个：

✅ **轻量**：从 6,683行 → 2,000行
✅ **简洁**：统一 API，参考 OKHttp
✅ **高效**：零依赖，极速启动
✅ **通用**：Java 8+ / Android 24+
✅ **易用**：链式调用，建造者模式
✅ **健壮**：完整测试，错误处理

---

**下一步**：开始执行任务 1.1（删除 org.json 包）

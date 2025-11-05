# GPG 密钥管理指南

## 当前状态

✅ **已解决的问题**
- 忘记了原有 GPG 密钥密码
- 生成了新的无密码保护 GPG 密钥
- 新密钥已发布到公钥服务器
- 项目可以正常构建和发布

## 密钥信息

### 新密钥
- **密钥 ID**: `8F49E97002F45035`
- **指纹**: `E8EF915B2E31EE715C900B048F49E97002F45035`
- **创建时间**: 2025-11-04
- **过期时间**: 2027-11-04
- **密码保护**: 无 (便捷使用)
- **邮箱**: sanbo.xyz@gmail.com

### 原有密钥 (备用)
- **密钥 ID**: `24271C19C32383F2`
- **状态**: 密码忘记，不建议使用

## 便捷使用方案

### 方案一：无密码密钥 (当前采用)
```bash
# 验证无密码签名
echo "test" | gpg --default-key 8F49E97002F45035 --armor --sign

# Maven 构建时自动使用
mvn clean deploy -P release
```

**优点**: 
- 无需输入密码
- 自动化友好
- CI/CD 兼容

**缺点**:
- 安全性较低
- 需要妥善保管私钥文件

### 方案二：配置 GPG Agent (推荐)
```bash
# 启动 GPG Agent
gpg-agent --daemon

# 配置自动缓存 (添加到 ~/.gnupg/gpg-agent.conf)
echo "default-cache-ttl 3600" >> ~/.gnupg/gpg-agent.conf
echo "max-cache-ttl 7200" >> ~/.gnupg/gpg-agent.conf
```

### 方案三：Maven 配置优化
在 `~/.m2/settings.xml` 中配置：
```xml
<settings>
  <profiles>
    <profile>
      <id>ossrh</id>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.keyname>8F49E97002F45035</gpg.keyname>
        <gpg.passphrase></gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>ossrh</activeProfile>
  </activeProfiles>
</settings>
```

## 密钥备份

### 立即备份 (重要)
```bash
# 备份私钥
gpg --export-secret-keys --armor 8F49E97002F45035 > ~/gpg-private.key

# 备份公钥
gpg --export --armor 8F49E97002F45035 > ~/gpg-public.key

# 设置安全权限
chmod 600 ~/gpg-private.key
chmod 644 ~/gpg-public.key
```

### 恢复密钥 (如需要)
```bash
# 导入私钥
gpg --import ~/gpg-private.key

# 导入公钥
gpg --import ~/gpg-public.key
```

## 日常使用命令

### Maven 构建
```bash
# 跳过签名 (开发环境)
mvn clean install -Dgpg.skip=true

# 正常签名 (发布环境)
mvn clean deploy -P release

# 强制使用特定密钥
mvn clean deploy -Dgpg.keyname=8F49E97002F45035
```

### GPG 操作
```bash
# 查看所有密钥
gpg --list-secret-keys --keyid-format=long

# 查看特定密钥详情
gpg --list-keys --fingerprint 8F49E97002F45035

# 签名文件
gpg --default-key 8F49E97002F45035 --armor --sign filename.txt

# 验证签名
gpg --verify filename.txt.asc
```

## 安全建议

### 物理安全
1. **加密存储**: 将密钥文件存储在加密磁盘或安全云存储
2. **离线备份**: 制作离线备份 (USB、纸质记录)
3. **访问控制**: 限制对私钥文件的访问权限

### 使用安全
1. **定期轮换**: 每2-3年更换一次密钥
2. **监控使用**: 定期检查密钥使用情况
3. **撤销预案**: 准备密钥撤销方案

### 环境安全
```bash
# 设置适当的文件权限
chmod 700 ~/.gnupg
chmod 600 ~/.gnupg/*
```

## 故障排除

### 常见问题
1. **GPG 卡死**: 
   ```bash
   gpgconf --kill gpg-agent
   ```

2. **密钥不可用**:
   ```bash
   gpg --edit-key 8F49E97002F45035
   > trust
   > 5 (最终信任)
   > y
   > quit
   ```

3. **签名失败**:
   ```bash
   # 检查密钥状态
   gpg --list-secret-keys
   # 重新导入密钥
   gpg --import ~/gpg-private.key
   ```

## 自动化脚本

### 快速部署脚本
```bash
#!/bin/bash
# deploy.sh
set -e

echo "开始部署..."
mvn clean deploy -P release
echo "部署完成!"
```

### 密钥检查脚本
```bash
#!/bin/bash
# check-gpg.sh
KEY_ID="8F49E97002F45035"

if gpg --list-secret-keys $KEY_ID > /dev/null 2>&1; then
    echo "✅ GPG 密钥可用: $KEY_ID"
else
    echo "❌ GPG 密钥不可用: $KEY_ID"
    echo "请导入密钥: gpg --import ~/gpg-private.key"
    exit 1
fi
```

## 联系信息

- **邮箱**: sanbo.xyz@gmail.com
- **GitHub**: https://github.com/hhhaiai
- **项目**: https://github.com/NetCapture/JNet

---

**创建时间**: 2025-11-05  
**最后更新**: 2025-11-05  
**版本**: 1.0
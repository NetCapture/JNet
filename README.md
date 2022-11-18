# JNet

网络请求

<!-- ## License

- JNet uses software libraries from [Apache Software Foundation](http://apache.org).
- JNet developer Idea enterprise licenses are supported by [Jetbrains](https://www.jetbrains.com?from=JNet).
- [IntelliJ IDEA](https://www.jetbrains.com/idea?from=JNet) can be used to edit JNet sources.

<img src="https://tva1.sinaimg.cn/large/006tNbRwgy1gaskr305czj30u00wjtcz.jpg" width="100"/>  -->

### 编译方法

``` shell
mvn install
```

### 调用方式

> 支持maven和gradle

* **maven集成**

``` xml
<!-- https://mvnrepository.com/artifact/com.github.netcapture/Jnt -->
<!-- https://repo1.maven.org/maven2/com/github/netcapture/Jnt/ -->
<dependency>
    <groupId>com.github.netcapture</groupId>
    <artifactId>Jnt</artifactId>
    <version>2.2.10</version>
</dependency>

```

* **gradle集成**

``` groovy
implementation 'com.github.netcapture:Jnt:2.2.10'
```

#### api类型

api含两种:

* 直接返回请求的结果，此时如网络请求成功(200),返回response text,否则返回error log ,若仍为空，则返回output log, 系列API:

``` java
//  http get request
Jnt.get
//  http post request
Jnt.post
//  http custom request
Jnt.request

//new api
NJnt.xx.get()

```

* 直接返回请求的response, response含状态值，HTTP response HEADER等值，系列API：

``` java

//  http get request
Jnt.getResp
//  http post request
Jnt.postResp
//  http custom request
Jnt.requestResp
```

#### 支持平台的API

* github api

``` java
// 新建文件
GithubHelper.createFile
// 更新文件
GithubHelper.updateContent
// 追加内容
GithubHelper.append
// 查询文件的sha值
GithubHelper.getSha
// 删除文件
GithubHelper.deleteFile
```

* gitee api

``` java
GiteeHelper.createFile
GiteeHelper.updateContent
GiteeHelper.getSha
GiteeHelper.deleteFile
```

* github 已经支持shell上传

该部分api从[uploadGithub](https://github.com/hhhaiai/uploadGithub/)摘录,支持用法如下：

```
github 用法:
	-o:	github[用户]名字
	-u:	github[用户]名字
	-r:	github[项目]名称
	-s:	github[上传目录]名称
	-p:	github[目标文件]名称
	-f:	github即将上传的本地文件名
	-t:	github 个人 token
	-c:	github上传[未base64]内容
	-b:	github上传[已base64]内容
	-m:	github上传commit内容
	-a:	github上传使用的用户名字(auther)
	-l:	github上传使用的邮箱名称
```

示例用法，已用于生产环境

``` shell
java -jar uploadGithubService-1.1-jar-with-dependencies.jar
    -owner hhhaiai -repo Git_result
    -target-dir-full-name  $upload_file_name
    -native-file ${file_name}
    -token ${{ secrets.GTOKEN }}
    -commit-messge  "GitHubAction: Build&Monkey ${{ github.repository }} Job ${{ github.job }}, created by ${{ github.workflow }} "
    -commit-auther "GitHubAction"
    -commit-email "sanbo.xyz@gmail.com"
```

#### 用于项目

* [uploadGithub](https://github.com/hhhaiai/uploadGithub)


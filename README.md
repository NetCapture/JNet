# JNet

网络请求


## Contribute

<img src="https://tva1.sinaimg.cn/large/006tNbRwgy1gaskr305czj30u00wjtcz.jpg" width="100"/> 

supported by [JetBrains](https://www.jetbrains.com/)

### 编译方法


``` shell
mvn install
```


### 调用方式

> 支持maven和gradle

* **maven集成**

``` xml
<!-- https://mvnrepository.com/artifact/com.github.netcapture/Jnt -->
<dependency>
    <groupId>com.github.netcapture</groupId>
    <artifactId>Jnt</artifactId>
    <version>1.0.4</version>
</dependency>

```


* **gradle集成**

``` groovy
implementation 'com.github.netcapture:Jnt:1.0.0'
```

* 具体的使用用法

``` java
String result = Jnt.request(HttpType.POST, timeout, uploadUrl, null, reqHeaderMap, data)
// process result
```



  * github api
  
    ``` java
    GithubHelper.updateContent("owner", "repo", "path", "token", "content has no base64", "commitMsg");
    ```
  
  * gitee api
  
    ``` java
    GiteeHelper.updateContent("owner", "repo", "path", "token", "content has no base64", "commitMsg");
    ```

    

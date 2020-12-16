# JNet

网络请求


### 编译方法


``` shell
mvn install
```


### 调用方式


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

    
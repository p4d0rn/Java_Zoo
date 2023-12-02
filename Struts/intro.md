# Preface

从之前的`SSH`（`Struts2 + Spring + Hibernate`），再到现在的`SSM`（`Spring + SpringMVC + MyBatis`），Java Web的生态经历了一次大的王朝交替，现已经是Spring全家桶一统天下的局面了。`Struts2`算是一个落后的老年框架了，但其对Java Web生态产生了一定的影响，现在的框架也借鉴了许多`Struts2`的思想，`Strut2`的攻防史在历史上也算是精彩的搏斗，因此很有必要“研读经典”，学习早年框架的结构思想。

# Quick Start

跟着这个教程走一遍，挺详细的👉https://struts.apache.org/getting-started/

`Struts`也是一个典型的MVC设计模式，不同于`SpringMVC`使用一个中央处理器`Servlet`来接管所有请求，`Struts2`使用的是`Filter`

如下为两个框架在`web.xml`中的配置

```xml
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <load-on-startup>1</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>dispatcher</servlet-name>
    <url-pattern>/*</url-pattern>
</servlet-mapping>
```

```xml
<filter>
    <filter-name>struts2</filter-name>
    <filter-class>org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>struts2</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

`Struts`中`Action`为控制器，需要继承`com.opensymphony.xwork2.ActionSupport`

```java
package com.demo.struts.action;

import com.demo.struts.model.MessageStore;
import com.opensymphony.xwork2.ActionSupport;

public class HelloWorldAction extends ActionSupport {
    private MessageStore messageStore;
    private String userName;

    public String execute() {
        if (messageStore == null) {
            messageStore = new MessageStore();
        }
        if (userName != null) {
            messageStore.setMessage(messageStore.getMessage() + " " + userName);
        }
        return SUCCESS;
    }

    public MessageStore getMessageStore() {
        return messageStore;
    }

    public void setMessageStore(MessageStore msg) {
        this.messageStore = msg;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
```

在`resources/struts.xml`中配置路由

```xml
<struts>
    <constant name="struts.devMode" value="true"/>
    <package name="basicstruts2" extends="struts-default">
        <action name="index">
            <result>/index.jsp</result>
        </action>

        <action name="hello" class="com.demo.struts.action.HelloWorldAction" method="execute">
            <result name="success">/HelloWorld.jsp</result>
        </action>
    </package>
</struts>
```

路由中指明了控制器类名(C)和对应的视图jsp文件(V)，上面的`HelloWorldAction`中的`MessageStore`和`userName`用于存储数据，即为模型(M)

控制器中的属性成员用于接收GET或POST参数，背后调用的是setter方法。setter方法会在执行`execute`之前调用。

```html
<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Hello World!</title>
</head>
<body>
<h2><s:property value="messageStore.message"/></h2>
</body>
</html>
```

`Struts`提高了一系列标签来方便编写视图文件，如上面的`s:property`标签，`messageStore.message`本质上是调用了`HelloWorldAction.getMessageStore().getMessage()`

`http://127.0.0.1:8080/basic-struts/hello?userName=java&messageStore.message=hate`

返回结果为`hate java`

另外请求路径最后可以加上`action`后缀，比如`hello.action`

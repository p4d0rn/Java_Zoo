# BlackList Bypass

## 0x01 FactoryTransformer plus *Factory

```java
/**
 * Transforms the input by ignoring the input and returning the result of
 * calling the decorated factory.
*/
public Object transform(Object input) {
    return iFactory.create();
}
```

* `InstantiateFactory` + `FactoryTransformer` = `InstantiateTransformer`

```java
/**
 * Creates an object using the stored constructor.
*/
public Object create() {
    if (iConstructor == null) {
        findConstructor();
    }
    try {
        return iConstructor.newInstance(iArgs);
    } // catch error
}
```

* `ConstantFactory` + `FactoryTransformer` = `ConstantTransformer`

```java
/**
 * Always return constant.
*/
public Object create() {
    return iConstant;
}
```

* `ReflectionFactory`  + `FactoryTransformer` = `InstantiateTransformer` but Non-Arg Version

Maybe useful ?

```java
private static class ReflectionFactory implements Factory {
    private final Class clazz;

    public ReflectionFactory(Class clazz) {
        this.clazz = clazz;
    }

    public Object create() {
        try {
            return clazz.newInstance();
        } catch (Exception ex) {
            throw new FunctorException("Cannot instantiate class: " + clazz, ex);
        }
    }
}
```

## 0x02 DefaultedMap

用于代替LazyMap，都是同一个父亲`AbstractMapDecorator`生的，顾名思义对Map进行修饰的类

LazyMap体现在懒加载时对传入的key进行transform

DefaultedMap顾名思义当key不在map中，返回默认值，其构造方法允许传入一个`Transformer`的实现类，会创建一个`ConstantTransformer`作为`value`成员，调用`get`时会触发其`transform`

但这个`value`成员是`Object`类型，因此可以通过反射将`ConstantTransformer`改成其他`Transformer`

```java
public static Map decorate(Map map, Object defaultValue) {
    if (defaultValue instanceof Transformer) {
        defaultValue = ConstantTransformer.getInstance(defaultValue);
    }
    return new DefaultedMap(map, defaultValue);
}
protected DefaultedMap(Map map, Object value) {
    super(map);
    this.value = value;
}
public Object get(Object key) {
    // create value for key if key is not currently in the map
    if (map.containsKey(key) == false) {
        if (value instanceof Transformer) {
            return ((Transformer) value).transform(key);
        }
        return value;
    }
    return map.get(key);
}
```

## 0x03 PredicatedMap

和`TransformedMap`出自同一父类`AbstractInputCheckedMapDecorator`

顾名思义会对放入map的键值对进行检查

```java
/**
  * Implementation of a map entry that checks additions via setValue.
*/
static class MapEntry extends AbstractMapEntryDecorator {

    /** The parent map */
    private final AbstractInputCheckedMapDecorator parent;

    protected MapEntry(Map.Entry entry, AbstractInputCheckedMapDecorator parent) {
        super(entry);
        this.parent = parent;
    }

    public Object setValue(Object value) {
        value = parent.checkSetValue(value);
        return entry.setValue(value);
    }
}
```

在CC1中可以替换`TransformedMap`，不过`TransformedMap`的checkSetValue能直接调用`transform`

`PredicatedMap`调用的是`evalute`

```java
protected Object checkSetValue(Object value) {
    if (valuePredicate.evaluate(value) == false) {
        throw new IllegalArgumentException("Cannot set value - Predicate rejected it");
    }
    return value;
}
```

让`valuePredicate`为`TransformedPredicate`

```java
// TransformedPredicate#evaluate
public boolean evaluate(Object object) {
    Object result = iTransformer.transform(object);
    return iPredicate.evaluate(result);
}
```

由于需要CC1前半段source来调用setValue，因此也受JDK版本限制
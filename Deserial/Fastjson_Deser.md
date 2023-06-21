`FastJson`使用`com.alibaba.fastjson.util.FieldInfo`这个类来给无参构造函数生成的实例赋值

可以看到其是先通过反射调用`getter`、`setter`t方法来实现，若没有`getter`、`setter`方法，通过反射获取`field`

```java
public class FieldInfo implements Comparable<FieldInfo> {

    public Object get(Object javaObject) throws IllegalAccessException, InvocationTargetException {
        return method != null
                ? method.invoke(javaObject)
                : field.get(javaObject);
    }

    public void set(Object javaObject, Object value) throws IllegalAccessException, InvocationTargetException {
        if (method != null) {
            method.invoke(javaObject, new Object[] { value });
            return;
        }

        field.set(javaObject, value);
    }
}
```


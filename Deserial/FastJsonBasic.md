# 0x01 What Is FastJson

fastjson 是阿里巴巴的开源 JSON 解析库，支持将 Java Bean 序列化为 JSON 字符串，也可以从 JSON 字符串反序列化到 JavaBean。顾名思义，FastJson的特点就是快。

# 0x02 Basic Usage

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>1.2.23</version>
</dependency>
```

* POJO => JSON

> `JSON.toJSONString()`
>
> 参数设置：
>
> * SerializerFeature.WriteClassName
>
>   序列化时，会多出一个`@type`跟上类名

```java
package org.example;

public class Person {
    public String name;
    public Integer age;

    public String getName() {
        System.out.println("getName");
        return name;
    }

    public void setName(String name) {
        System.out.println("setName");
        this.name = name;
    }

    public Integer getAge() {
        System.out.println("getAge");
        return age;
    }

    public void setAge(Integer age) {
        System.out.println("setAge");
        this.age = age;
    }

    public Person(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

    public Person(){
        System.out.println("Non-Arg Constructor");
    }

    @Override
    public String toString() {
        return "I am " + this.name + " and " + this.age + " years old";
    }
}
```

```java
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.example.Person;

public class Test {
    public static void main(String[] args) {
        Person person1 = new Person("Tom", 18);
        String str1 = JSON.toJSONString((person1));
        System.out.println(str1);

        Person person2 = new Person("Lisa", 20);
        String str2 = JSONObject.toJSONString(person2, SerializerFeature.WriteClassName);
        System.out.println(str2);
    }
}
```

> getAge
>
> getName
>
> {"age":18,"name":"Tom"}
>
> getAge
>
> getName
>
> {"@type":"org.example.Person","age":20,"name":"Lisa"}

* JSON => POJO

> `JSON.parseObject()`
>
> 参数设置：
>
> * Feature.SupportNonPublicField
>   反序列化时，加上该参数才能还原private属性

```java
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import org.example.Person;

public class Test {
    public static void main(String[] args) {
        Person person = JSON.parseObject("{\"@type\":\"org.example.Person\",\"age\":20,\"name\":\"Lisa\"}", Person.class, Feature.SupportNonPublicField);
        System.out.println(person);
    }
}
```

> Non-Arg Constructor
> setAge
> setName
> I am Lisa and 20 years old

```java
import com.alibaba.fastjson.JSON;

public class Test {
    public static void main(String[] args) {
        String s = "{\"@type\":\"org.example.Person\",\"age\":20,\"name\":\"Lisa\"}";
        Object obj1 = JSON.parse(s);
        System.out.println(obj1);
        Object obj2 = JSON.parseObject(s);
        System.out.println(obj2);
    }
}
```

> Non-Arg Constructor
> setAge
> setName
> I am Lisa and 20 years old
> Non-Arg Constructor
> setAge
> setName
> getAge
> getName
> {"name":"Lisa","age":20}

可以看到`JSON.parseObject()`的打印结果和我们定义的`toString()`不同，说明它不是Person对象。（实际上得到的是JSONObject类对象）

结论：

* parse()会**识别**并调用目标类的setter方法
* parseObject()会触发目标类的getter和setter方法

因此若能找到一个类、在反序列化这个类对象时，fastjson调用其setter或getter方法，且setter或getter方法存在漏洞，可以执行恶意代码。

下面再列举一些FastJson的结论，在后续调试中可以观察得到：

* `JSON.parse(jsonString)` 和 `JSON.parseObject(jsonString, Target.class)`，前者会在 jsonString 中解析字符串获取 `@type` 指定的类，后者则会直接使用参数中的class。
* `JSON.parseObject(jsonString)` 将会返回 JSONObject 对象，且类中的所有 getter 与setter 都被调用。
* 如果目标类中私有变量没有 setter 方法，但是在反序列化时仍想给这个变量赋值，则需要使用 `Feature.SupportNonPublicField` 参数。
* fastjson 在反序列化时，如果 Field 类型为 `byte[]`，将会调用`com.alibaba.fastjson.parser.JSONScanner#bytesValue` 进行 base64 解码，对应的，在序列化时也会进行 base64 编码。
* fastjson 在为类属性寻找 get/set 方法时，调用函数 `com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer#smartMatch()` 方法，会忽略 `_|-` 字符串，假如字段名叫 `_a_g_e_`，getter 方法为 `getAge()`，fastjson 也可以找得到。

配置类：

`com.alibaba.fastjson.parser.ParserConfig`：后面的AutoType开关和黑名单体现在这个类中

满足条件的setter：

* 函数名大于4且以set开头
* 非静态函数
* 返回类型为void或当前类
* 参数个数为1个

满足条件的getter

* 函数名长度大于等于4
* 非静态方法
* 以get开头且第4个字母为大写
* 无参数
* 返回值类型继承自Collection或Map或AtomicBoolean或AtomicInteger








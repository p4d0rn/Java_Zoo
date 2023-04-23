# 0x01 Preface

Java作为一门静态语言，编译时变量的数据类型已经确定。反射给Java带来了一定的动态性。

> “以铜为镜，可以正衣冠”
>
> 以反射为镜，照之对象可得类，照之类可得属性方法，皆可访达

# 0x02 Basic Usage Of Reflect

现有一类Person：

```java
package com.demo;

public class Person {
    public String name;
    private Integer age;

    public Person(){}
    public Person(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

    private void action(String move) {
        System.out.println(move + ' ' +this.age);
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
```

## 获取类

欲获`java.lang.Class`对象，如下三法：

* `Class.forName`：传之类名，无需import
* `obj.getClass()`：上下文该类实例存矣
* `Person.class`：类已加载，直捣`class`属性

```java
        Person person = new Person();
        Class clazzA = Class.forName("com.demo.Person");
        Class clazzB = person.getClass();
        Class clazzC = Person.class;
        System.out.println(clazzA.hashCode());
        System.out.println(clazzB.hashCode());
        System.out.println(clazzC.hashCode());
```

三者`hashCode`无异，同一Class对象也

## 获取属性和方法

既获之类，进而获之属性与方法，实例之。

```java
        Class clazz = Class.forName("com.demo.Person");

        // 获取所有方法
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            System.out.println(method);
        }

        // 获取所有字段
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            System.out.println(field);
        }

        // 实例化类
        Constructor constructor = clazz.getConstructor(String.class, Integer.class);
        Person person = (Person) constructor.newInstance("Billy", 15);

        // 修改私有字段
        Field field = clazz.getDeclaredField("age");
        field.setAccessible(true);
        field.set(person, 18);
        System.out.println(person);

        // 调用私有方法
        Method method = clazz.getDeclaredMethod("action", String.class);
        method.setAccessible(true);
        method.invoke(person,"PRIVATE METHOD GET");
```

* `getMethod`获当前类的public方法（包括从父类继承的方法）
* `getDeclaredMethod`获当前类实打实定义的方法，包括private方法（不包括从父类继承的方法）

`getField`、`getDeclaredField`同理。若加上s 表示获取所有的方法或属性

> - `getMethod(String name, Class<?>... parameterTypes)`
>
>   面向对象支持重载机制，因此获取方法的时候不仅要指定方法名，还得指定参数的类型
>   `...`表示可变长参数，Java底层会将其封装为一个数组
>   这边可以传多个参数，也可以直接传数组 如`new Class[]{A.class, B.class}`
>
> - `method.invoke(Object obj, Object... args)`
>   若method是一个普通方法，则第一个参数是类对象
>   若method是一个静态方法，则第一个参数是类
>   第二个参数也是可变长参数，传入method所需的参数

# 0x03 Best Practice

下面利用反射来实现命令执行

## `java.lang.Runtime`

这个类是设计为`单例模式`的。
在Web开发中，数据库只需连接一次，下次需要用到数据库时，为避免重复连接，只要拿到数据库连接对象即可，所以数据库连接类或许是下面这样的（单例模式）

```java
    public class DBConnector {
        private static DBConnector instance = new DBConnector();
        public static DBConnector getDBConnector() {
            return instance;
        }
        private DBConnector() {
            // code for connecting to DB
        }
    }
```

这样类在初始化的时候就执行会建立连接，执行构造函数，我们通过`getDBConnector` 获取数据库连接对象，避免多次进行连接。

`java.lang.Runtime`的构造方法是私有的，但是却有一个公开方法`getRuntime()`来获取`Runtime`对象。

```java
Class clazz = Class.forName("java.lang.Runtime");
Object runTime = clazz.getMethod("getRuntime").invoke(clazz);
clazz.getMethod("exec", String.class).invoke(runTime, "calc");
```

也可以获取`Runtime`的私有构造器去构造`Runtime`对象

```java
Class clazz = Class.forName("java.lang.Runtime");
Constructor constructor = clazz.getDeclaredConstructor();
constructor.setAccessible(true);
clazz.getMethod("exec", String.class).invoke(constructor.newInstance(), "calc");
```

## `java.lang.ProcessBuilder`

除了`Runtime.exec()`，`ProcessBuilder.start()`是另外一种命令执行的方式。

`java.lang.ProcessBuilder`有两个构造函数

> public ProcessBuilder(List command)
>
> public ProcessBuilder(String... command)

* ```java
  Class clazz = Class.forName("java.lang.ProcessBuilder");
  clazz.getMethod("start").invoke(clazz.getConstructor(List.class).newInstance(Arrays.asList("calc")));
  ```

* ```java
  Class clazz = Class.forName("java.lang.ProcessBuilder");
  clazz.getMethod("start").invoke(clazz.getConstructor(String[].class).newInstance(new String[][]{{"calc"}}));
  ```

# 0x04 Summary

- 动态实例化对象
- 动态调用方法
- 操作类内部私有属性和方法

在反序列化漏洞中的应用

- 通过invoke调用除了同名函数以外的函数
- 创建对象，引入不能序列化的类（比如传入Runtime.class）
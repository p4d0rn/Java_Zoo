# Intro

Java通过反射可以在运行时动态获取或修改类型字段、方法等信息，但反射的执行速度也成一大诟病。Java7开始提供另一套API称为方法句柄（`Method Handle`），其作用与反射类似，但执行效率比反射高，被称为现代化反射。

> A method handle is a typed, directly executable reference to an underlying method, constructor, field, or similar low-level operation, with optional transformations of arguments or return values.

方法句柄是一种指向方法的强类型、可执行的引用，它的类型由方法的参数类型和返回值类型组成，而与方法所在类名和方法名无关。
它作为方法的抽象，使方法调用不再受所在类型的约束，能更好的支持动态类型语言特性。

几个关键的类：

* Lookup：方法句柄的创建工厂类，方法的检查工作是在创建时处理的而非调用时处理
* MethodType：方法的签名，包括返回值类型和参数类型
* MethodHandle：方法句柄，用于动态访问类型的信息

## Lookup

创建一个提供对公共方法访问的查找：

```java
MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
```

`lookup`方法可访问私有和受保护的方法

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
```

`Lookup`对象提供几种查找对象的方法

* invokevirtual

对象方法的查找

```java
public MethodHandle findVirtual(Class<?> refc, String name, MethodType type)
```

* invokestatic

静态方法查找

```java
public MethodHandle findStatic(Class<?> refc, String name, MethodType type)
```

* 查找构造函数

```java
public MethodHandle findConstructor(Class<?> refc, MethodType type)
```

* 查找非私有字段

```java
/**
* Produces a method handle giving read access to a non-static field.
* The type of the method handle will have a return type of the field's
* value type.
* The method handle's single argument will be the instance containing
* the field.
*/
public MethodHandle findGetter(Class<?> refc, String name, Class<?> type)
```

```java
/**
* Produces a method handle giving write access to a non-static field.
* The type of the method handle will have a void return type.
 * The method handle will take two arguments, the instance containing
* the field, and the value to be stored.
*/
public MethodHandle findSetter(Class<?> refc, String name, Class<?> type)
```

## MethodHandle

使用`invoke`家族来调用

`invoke`、`invokeWithArguments`、`invokeExact`

## MethodType

`Lookup`需要方法的签名才能找到方法句柄

```java
public static MethodType methodType(Class<?> rtype, Class<?> ptype0, Class<?>... ptypes)
```

`rtype`为返回值类型，`ptypes`为参数类型

# QuickStart

```java
class Horse {
  public void race() {
    System.out.println("Horse.race()"); 
  }
}

class Deer {
  public void race() {
    System.out.println("Deer.race()");
  }
}

class Cobra {
  public void race() {
    System.out.println("How do you turn this on?");
  }
}
```

如何设计统一的方式调用`race`方法？

使用反射有性能损耗，抽象出一个包含`race`方法的接口则增加了接口约束，这时候就可以考虑方法句柄了。将`race`方法抽象成方法句柄，它们的句柄类型一致，对调用者暴露方法句柄即可。

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MethodHandleTest {
    public void race(Object obj) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle race = lookup.findVirtual(obj.getClass(), "race", MethodType.methodType(void.class));
        race.invoke(obj);
    }

    public static void main(String[] args) throws Throwable {
        MethodHandleTest test = new MethodHandleTest();
        test.race(new Horse());
        test.race(new Deer());
    }
}
```

方法句柄也有权限问题，但与反射在方法调用时检查不同，它是在**句柄创建阶段进行检查**的，如果多次调用句柄，它比反射可以省下权限重复检查的开销。

但需注意的是，**句柄的访问权限不取决于创建句柄的位置，而是 Lookup 对象的位置**。

# Runtime#exec

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();

MethodType mt1 = MethodType.methodType(Runtime.class);
MethodHandle getRuntime = lookup.findStatic(Runtime.class, "getRuntime", mt1);

MethodType mt2 = MethodType.methodType(Process.class, String.class);
MethodHandle exec = lookup.findVirtual(Runtime.class, "exec", mt2);

Object runTime = getRuntime.invokeWithArguments();
exec.invoke(runTime, "calc");
```

`getRuntime`没有参数，对应的`MethodType`只要传一个`Runtime.class`即可
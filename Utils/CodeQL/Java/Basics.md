# Program Elements

## Types

下面是`Type`的几个子类

* `PrimitiveType`: 表示Java中的原始类
  * `boolean`、`byte`、`char`、`double`、`float`、`int`、`long`、`short`
  * QL中把`void`和`null`也作为原始类
* `RefType`: 表示引用类型(reference | non-primitive), 有如下子类
  * `Class`
  * `Interface`
  * `EnumType`
  * `Array`
* `TopLevelType`: 表示声明在一个编译单元最上层的引用类型(不太懂。。。)
* `NestedType`: 声明在另一个类型中的类型
  * `LocalClass`: 声明在一个方法或构造函数中的类
  * `AnonymousClass`: 匿名类

此外还有一些常用的类，顾名思义

`TypeObject`、`TypeCloneable`、`TypeRuntime`、`TypeSerializable`、`TypeString`、`TypeSystem`、`TypeClass`

examples:

* 查找所有double变量

```sql
import java

from Variable v, PrimitiveType pt
where pt = v.getType() and pt.hasName("double")
select v
```

* 查找所有直接继承自Object的内部类

```sql
import java

from InnerClass ic
where ic.getASupertype() instanceof TypeObject
select ic
```

## Generics

`GenericType`表示泛型，分为 `GenericInterface` 或 `GenericClass`

比如`java.util.Map`这个接口

```java
package java.util.;

public interface Map<K, V> {
    int size();
    // ...
}
```

参数`K`、`V`用`TypeVariable`表示

泛型的参数化实例(如`Map<String, File>`)提供了一个具体类型(这里的`String`、`File`)来实例化类型参数，比

这里的参数化实例用`ParameterizedType`表示

通过`getSourceDeclaration`获取`ParameterizedType`对应的`GenericType`

通常，泛型可能会限制类型参数可以绑定到哪些类型，比如下面限制了一个从String到Number的map

```java
class StringToNumMap<N extends Number> implements Map<String, N> {
    // ...
}
```

`StringToNumberMap`的参数化实例只能是`Number`或其子类

使用`getATypeBound`和`getType`来得到`TypeVariable`类型绑定

`RawType`用于处理泛型的原生类型，如下`m1`

```java
Map m1 = new HashMap();
Map<String, String> m2 = new HashMap<String, String>();
```

examples:

* 查找`java.util.Map`的所有参数化实例

```java
import java

from GenericInterface map, ParameterizedType pt
where
    map.hasQualifiedName("java.util", "Map") and
    pt.getSourceDeclaration() = map
select pt
```

* 查找`java.util.Map`的原生类型

```sql
import java

from Variable v, RawType rt
where rt = v.getType() and
    rt.getSourceDeclaration().hasQualifiedName("java.util", "Map")
select v
```

* 查找绑定到`java.lang.Object`的所有泛型的类型变量

```sql
import java

from TypeVariable tv, TypeBound tb
where tb = tv.getATypeBound() and
    tb.getType().hasQualifiedName("java.lang", "Object")
select tv
```

## Variables

`Variable`表示Java中的变量

* `Field`
* `LocalVariableDecl`
* `Parameter`

# AST

抽象语法树中的节点

* statement(`Stmt`)
* expression(`Expr`)

- `Expr.getAChildExpr` 返回子表达式
- `Stmt.getAChild` 返回语句的下一条语句
- `Expr.getParent`、`Stmt.getParent` 返回父节点

```java
import java

from Expr e
where e.getParent() instanceof ReturnStmt
select e
```

返回跟在return语句后面的表达式

比如 `return x+y` 返回 `x+y`

```java
import java

from Stmt s
where s.getParent() instanceof IfStmt
select s
```

返回所有`if`语句的`then`分支和`else`分支

```java
import java

from Stmt s
where s.getParent() instanceof Method
select s
```

返回所有方法体

可见`Expr`和`Stmt`的父节点不一定是`Expr`或`Stmt`，可以使用`ExprParent` 或 `StmtParent`表示`Expr`和`Stmt`可能的父节点

# Metadata

元数据包括注解(`annotation`)和文档(`javadoc`)

对于packages, reference types, fields, methods, constructors, local variable declarations

这些都可以被注解，`Annotatable`是其父类，`getAnAnnotation`获取注解

寻找所有`Callable`（包括`Method`和`Constructor`）的注解

```sql
import java

from Callable c
select c.getAnAnnotation()
```

寻找所有构造器中注解的`@Deprecated`

```sql
import java

from Constructor c, Annotation ann, AnnotationType anntp
where ann = c.getAnAnnotation() and
    anntp = ann.getType() and
    anntp.hasQualifiedName("java.lang", "Deprecated")
select ann
```

寻找私有字段的文档

```sql
import java

from Field f, Javadoc jdoc
where f.isPrivate() and
    jdoc = f.getDoc().getJavadoc()
select jdoc
```

# Call graph

CodeQL根据Java代码生成的数据库包含了预计算的程序调用图

`Callable`包括方法和构造器，`Call`表示调用表达式（包括方法调用、new对象、用`this`或`super`的构造器调用）

下面找出`println`的调用点

```sql
import java

from Call c, Method m
where m = c.getCallee() and
    m.hasName("println")
select c
```

`Callable.getAReference`返回`Call`调用表达式的引用点

下面找出没被调用过的方法

```sql
import java

from Callable c
where not exists(c.getAReference())
select c
```

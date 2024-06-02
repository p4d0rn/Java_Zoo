# Types

QL是静态类型语言（statically typed），即每个变量必须声明类型

类型是值的集合，一个值可能属于一个或多个集合，即一个值可能有多种类型

## Primitive types

原始的内置类型，`boolean`、`float`、`int`、`string`、`date`

QL提供了一系列内置的定义在原始类型的操作

## Classes

类代表单一的值集。QL中的类并不会创建一个新对象，它只是将满足某种逻辑属性的值归为一组。

### definition

自定义类：

* 关键字`class`
* 类名（大写字母开头）
* 超类的继承或实现（extends、instanceof）
* 类的主体（包括`characteristic predicate`、`member predicate`、`fields`）

```sql
class OneTwoThree extends int {
  OneTwoThree() { // characteristic predicate
    this = 1 or this = 2 or this = 3
  }

  string getAString() { // member predicate
    result = "One, two or three: " + this.toString()
  }

  predicate isEven() { // member predicate
    this = 2
  }
}
```

### class bodies

定义一个类后，它会从父类继承非私有的成员谓词和字段

* Characteristic predicates

特征谓词，类似构造函数，使用`this`变量限制了当前类所表示的数据集合（`represent logic properties`）

如上例子中将int集合又进一步限制在了`1、2、3`

* Member predicates

成员谓词，包含特定于该类值集的操作，只能应用于某一`class`的成员的谓词（`class`的成员`member`需要是`characteristic predicate`中声明的）

如`1.(OneTwoThree).getAString()`返回`"One, two or three: 1"`

`(OneTwoThree)`用于强转（`cast`），保证`1`有`OneTwoThree`这个`type`而不只是`int`

* Fields

类里面定义的变量，可以在类的谓词定义中使用这些变量，类似变量`this`，`fields`必须在`characteristic predicate`做出限制。

```sql
class SmallInt extends int {
  SmallInt() { this = [1 .. 10] }
}

class DivisibleInt extends SmallInt {
  SmallInt divisor;   // declaration of the field `divisor`
  DivisibleInt() { this % divisor = 0 }

  SmallInt getADivisor() { result = divisor }
}

from DivisibleInt i
select i, i.getADivisor()
```

QL中的`class`必须至少有一个`supertype`

### inheritance

* extension

如果一个`class`继承了`supertype`，可以重写继承到的`member predicate `

```sql
class OneTwo extends OneTwoThree {
  OneTwo() {
    this = 1 or this = 2
  }

  override string getAString() {
    result = "One or two: " + this.toString()
  }
}
```

```sql
from OneTwoThree o
select o, o.getAString()
```

和其他面向对象语言不同，上面的查询语句会首先遍历子类的值域，对其应用`getAString`，再对父类和子类值域的差集应用`getAString`

| o    | `getAString()` result |
| :--- | :-------------------- |
| 1    | One or two: 1         |
| 2    | One or two: 2         |
| 3    | One, two or three: 3  |

若我们再定义一个子类，若子类值域相交，相交部分会被多次调用

```sql
class TwoThree extends OneTwoThree {
  TwoThree() {
    this = 2 or this = 3
  }

  override string getAString() {
    result = "Two or three: " + this.toString()
  }
}
```

| o    | `getAString()` result |
| :--- | :-------------------- |
| 1    | One or two: 1         |
| 2    | One or two: 2         |
| 2    | Two or three: 2       |
| 3    | Two or three: 3       |

一个类可以继承多种类型，其值取父类的交集

如果父类有相同的方法，该类必须重写该方法

* final extension

一个类可以继承`final`类型，该类会继承父类最终版本的成员谓词和字段，并且通过`final extension`继承到的成员谓词不能重写，但可以被隐藏(`shadowed`)

```sql
final class FinalOneTwoThree = OneTwoThree;

class OneTwoFinalExtension extends FinalOneTwoThree {
  OneTwoFinalExtension() {
    this = 1 or this = 2
  }

  string getAString() {
    result = "One or two: " + this.toString()
  }
}

from OneTwoThree o
select o, o.getAString()
```

| 1    | 1    | One, two or three: 1 |
| ---- | ---- | -------------------- |
| 2    | 2    | One, two or three: 2 |
| 3    | 3    | One, two or three: 3 |

和重写不同，`final extension`保持继承类不变，看到这里并不会调用`OneTwoFinalExtension#getAString`

* instanceof

除了`extend base types`，`class`还能声明`instanceof`建立和其他`types`的关系

```sql
class Foo extends int {
  Foo() { this in [1 .. 10] }

  string fooMethod() { result = "foo" }
}

class Bar instanceof Foo {
  string toString() { result = super.fooMethod() }
}
```

上面实例可以看成在类`Bar`的特征谓词中声明`this instanceof Foo`

`Foo`的特征谓词能作用到`Bar`，但成员谓词不能被`Bar`直接调用，得通过`super`关键字

`select any(Bar b).fooMethod()`编译会报错

此外`instanceof supertypes`并不能重写父类的成员谓词

### concrete class

上面定义的都是具体类，通过在更大范围的父类基础上进行值的限制，即父类跟`characteristic predicate`的交集

(A restriction of the values in a larger type)

### abstract class

由`abstract`修饰的类为抽象类，抽象类是其子类的并集(`union`)

一个抽象类的值必须满足其本身和其子类的`characteristic predicate`

有点OOP多态的赶脚

```sql
abstract class SqlExpr extends Expr {
  ...
}
```


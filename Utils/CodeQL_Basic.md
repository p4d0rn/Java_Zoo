# 0x01 What Is CodeQL

CodeQL是一个支持多种语言及框架的代码分析工具，由`Semmle`公司开发，现已被`Github`收购。其可以从代码中提取信息构成数据库，我们通过编写查询语句来获取信息，分析可能存在的漏洞。

## Install CodeQL

CodeQL包括`引擎`和`库`两部分。核心的解析引擎不开源，用于解析数据库执行查询等操作的库是开源的。

Github提供了CLI和VSCode插件两个选择

安装参考：[CodeQL for VSCode搭建流程-安全客](https://www.anquanke.com/post/id/266823)

## Features

* Logical
* Declarative
* Object-oriented
* Read-only
* Equipped with rich standard libraries for analyzing source code

## CTF

[Capture the flag | GitHub Security Lab](https://securitylab.github.com/ctf/)

# 0x02 QL Syntax

## Predicates

`CodeQL`中的`Predicates`（谓词）充当类似函数的功能

QL内置了一些谓词可以直接使用，不需要导入

如何自定义谓词

* 返回结果类型（若谓词无返回值，前面写关键词predicate）
* 谓词名称（第一个字母小写）
* 谓词参数
* 谓词主体

```sql
predicate name(type arg)
{
  statements
}
```

> 注：谓词的参数和返回值的值域都为有限的元组集合，即在谓词主体中要显式声明出来

* 无返回值：

一般用于简单的数据筛选吧，满足条件的输入才通过

```sql
predicate isSmall(int i) {
  i in [1 .. 9]
}
```

* 有返回值：

将关键字predicate换为返回值的类型，返回值为`result`

```sql
int getSuccessor(int i) {
  result = i + 1 and
  i in [1 .. 9]
}
```

在谓词里面，`result`可以作为参数传入其他谓词，如下面定义了`getAParentOf`这个谓词的”逆“

```sql
Person getAChildOf(Person p) {
  p = getAParentOf(result)  # result的parent是p, 即p的child是result
}
```

谓词可以有多个返回值，如

```sql
string getFunc(string action) {
  action = "rce" and result = "popen"
  or
  action = "rce" and result = "system"
  or
  action = "ssrf" and result = "file_get_contents"
  or
  action = "ssrf" and result = "curl_exec"
}
```

`getFunc("rce")`返回两个结果`popen`、`system`

`getFunc("csrf")`不返回结果

谓词可以递归使用

```sql
string getANeighbor(string country) {
  country = "France" and result = "Belgium"
  or
  country = "France" and result = "Germany"
  or
  country = "Germany" and result = "Austria"
  or
  country = "Germany" and result = "Belgium"
  or
  country = getANeighbor(result)
}
```

`getANeighbor("Belgium")`返回`France`和`Germany`

上面说到谓词的参数和返回结果的值域都要是有限的tuple

如下的谓词编译时就会报错。

```sql
/*
  Compilation errors:
  ERROR: "i" is not bound to a value.
  ERROR: "result" is not bound to a value.
  ERROR: expression "i * 4" is not bound to a value.
*/
int multiplyBy4(int i) {
  result = i * 4
}

/*
  Compilation errors:
  ERROR: "str" is not bound to a value.
  ERROR: expression "str.length()" is not bound to a value.
*/
predicate shortString(string str) {
  str.length() < 10
}
```

可以通过在谓词上使用`bindingset`标志，其声明了该谓词的输入绑定到了有限的数据集合

```sql
bindingset[x] bindingset[y]
predicate plusOne(int x, int y) {
  x + 1 = y
}

from int x, int y
where y = 42 and plusOne(x, y)
select x, y
```

`bindingset[x] bindingset[y]`是`or`关系，即x或y是有限的

而`bindingset[x, y]`是`and`关系，即x和y必须是有限的

```sql
bindingset[str, len]
string truncate(string str, int len) {
  if str.length() > len
  then result = str.prefix(len)
  else result = str
}
```

## Types

QL是静态类型语言（statically typed），即每个变量必须声明类型

### Primitive types

原始的内置类型，`boolean`、`float`、`int`、`string`、`date`

### Classes

类代表单一的值集。QL中的类并不会创建一个新对象，它只是将满足某种逻辑属性的值归为一组。

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

* Characteristic predicates

特征谓词，类似构造函数，限制了当前类所表示的数据集合（`represent logic properties`）

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

* Extend

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

## Queries

两种查询：

* `select clause`
* `query predicates`

### Select Clauses

```sql
from /* ... variable declarations ... */
where /* ... logical formula ... */
select /* ... expressions ... */
```

类似SQL

```sql
from int x, int y
where x = 3 and y in [0 .. 2]
select x, y, x * y as product, "product: " + product
order by y desc
```

| x    | y    | product |            |
| :--- | :--- | :------ | :--------- |
| 3    | 2    | 6       | product: 6 |
| 3    | 1    | 3       | product: 3 |
| 3    | 0    | 0       | product: 0 |

### Query Predicates

带有`query`标识的`non-member predicate`

```sql
query int getProduct(int x, int y) {
  x = 3 and
  y in [0 .. 2] and
  result = x * y
}
```

| x    | y    | result |
| :--- | :--- | :----- |
| 3    | 0    | 0      |
| 3    | 1    | 3      |
| 3    | 2    | 6      |

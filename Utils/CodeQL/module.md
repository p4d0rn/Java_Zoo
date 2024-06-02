# Modules

模块是为了更好地组织QL代码，将有关的类型聚集在一起

可以将模块导入其他文件中来规划代码

## definition

* `explicit module`

直接定义，`module`关键字跟上一个模块名

```sql
module Example {
  class OneTwoThree extends int {
    OneTwoThree() {
      this = 1 or this = 2 or this = 3
    }
  }
}
```

* `file module`

每个查询文件(`.ql`)或库文件(`.qll`)隐式定义了一个模块，模块名就是文件名

查询模块(`query module`)不能被导入，并且在其命名空间必须至少有一个查询语句

`OneTwoThreeLib.qll`

```sql
class OneTwoThree extends int {
  OneTwoThree() {
    this = 1 or this = 2 or this = 3
  }
}
```

`OneTwoQuery.ql`

```sql
import OneTwoThreeLib

from OneTwoThree ott
where ott = 1 or ott = 2
select ott
```

* `parameterized module`

用于泛型编程，在`explicit module`之上增加了参数

```sql
module M<transformer/1 first, transformer/1 second> {
  bindingset[x]
  int applyBoth(int x) { result = second(first(x)) }
}
```

`parameterized module`不能直接被引用，需要传入参数进行实例化

实例化后的`parameterized module`可以作为模块表达式来使用

`parameterized module`的参数是带有签名(`signatures`)的类型

这里的模块`M`接收两个谓词参数，并定义了一个新的谓词`applyBoth`

```sql
bindingset[x]
signature int transformer(int x);

module M<transformer/1 first, transformer/1 second> {
  bindingset[x]
  int applyBoth(int x) { result = second(first(x)) }
}

bindingset[result] bindingset[x]
int increment(int x) { result = x + 1 }

module IncrementTwice = M<increment/1, increment/1>;

select IncrementTwice::applyBoth(40) // 42
```
# Predicates

`CodeQL`中的`Predicates`（谓词）充当类似函数的功能，谓词实际上就是对元组集合的求值操作

QL内置了一些谓词可以直接使用，不需要导入

自定义谓词:

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

一般用于数据筛选吧，满足条件的输入才通过

```sql
predicate isSmall(int i) {
  i in [1 .. 9]
}
```

* 有返回值：

将关键字predicate换为返回值的类型，返回值的变量为`result`

```sql
int getSuccessor(int i) {
  result = i + 1 and
  i in [1 .. 9]
}
```

在谓词里面，`result`可以作为参数传入其他谓词，如下面定义了`getAParentOf`这个谓词的”逆“

只需将`result`和其他变量的关系表达出来即可

```sql
Person getAChildOf(Person p) {
  p = getAParentOf(result)  # result的parent是p, 即p的child是result
}
```

* 多返回值谓词

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

* 递归谓词

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

上面谓词`getANeighbor`作的限定关系并没有直接体现对称关系，即x=getANeighbor(y)同时y=getANeighbor(x)

可以使用递归谓词来解决

`getANeighbor("Belgium")`返回`France`和`Germany`

* 谓词种类

可以分成三种谓词：

1. non-member predicates
2. member predicates
3. characteristic predicates

```sql
int getSuccessor(int i) {  // 1. Non-member predicate
  result = i + 1 and
  i in [1 .. 9]
}

class FavoriteNumbers extends int {
  FavoriteNumbers() {  // 2. Characteristic predicate
    this = 1 or
    this = 4 or
    this = 9
  }

  string getName() {   // 3. Member predicate for the class `FavoriteNumbers`
    this = 1 and result = "one"
    or
    this = 4 and result = "four"
    or
    this = 9 and result = "nine"
  }
}
```

* 集合绑定

上面说到谓词的参数和返回结果的值域都要是有限的tuple，即谓词只能包含有限数量的元组

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
# Queries

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
select x, y, x * y as product, "product: " + product as description
order by y desc
```

|  x   |  y   | product | description |
| :--: | :--: | :-----: | :---------: |
|  3   |  2   |    6    | product: 6  |
|  3   |  1   |    3    | product: 3  |
|  3   |  0   |    0    | product: 0  |

### Query Predicates

带有`query`标识的`non-member predicate`

```sql
query int getProduct(int x, int y) {
  x = 3 and
  y in [0 .. 2] and
  result = x * y
}
```

|  x   |  y   | result |
| :--: | :--: | :----: |
|  3   |  0   |   0    |
|  3   |  1   |   3    |
|  3   |  2   |   6    |

可以在其他位置调用查询谓词

```sql
class MultipleOfThree extends int {
  MultipleOfThree() { this = getProduct(_, _) }
}

from MultipleOfThree m
select m
```

查询子句`select clause`可以看作是匿名查询谓词，不能在其他地方调用
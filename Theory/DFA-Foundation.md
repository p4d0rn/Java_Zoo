# Iterative Algorithm

Here is a classic iterative algorithm template for May & Forward analysis

![image-20240405093548478](./../.gitbook/assets/image-20240405093548478.png)

Let’s view it in another way

* Given a CFG with k nodes, the iterative algorithm updates OUT[n] for every node n in each iteration.
* Assume the domain of the values in data flow analysis is V, let’s define a k-tuple: (OUT[n1], OUT[n2], …, OUT[nk]) as an element of set (V1 × V2 … × Vk) denoted as Vk, to hold the values of the analysis after each iteration.
* Each iteration can be considered as taking an action to map
  an element of Vk to a new element of Vk, through applying
  the transfer functions and control-flow handing, abstracted
  as a function F: Vk → Vk
* Then the algorithm outputs a series of k-tuples iteratively
  until a k-tuple is the same as the last one in two consecutive
  iterations.

![image-20240405094013650](./../.gitbook/assets/image-20240405094013650.png)

X is a fixed point of function F if `X = F(X)`

We say the iterative algorithm reaches a fixed point.

# Partial Order

## poset

![image-20240405094243100](./../.gitbook/assets/image-20240405094243100.png)

Some examples of poset:

* (S, ≤) , S is a set of integers
* (S, substring) , S is a set of English words
* (S, subset) , S is the power set of set {a,b,c}

## upper & lower bounds

![image-20240405094641615](./../.gitbook/assets/image-20240405094641615.png)

![image-20240405094725002](./../.gitbook/assets/image-20240405094725002.png)

![image-20240405094807219](./../.gitbook/assets/image-20240405094807219.png)

![image-20240405094956644](./../.gitbook/assets/image-20240405094956644.png)

# Lattice

![image-20240405095221142](./../.gitbook/assets/image-20240405095221142.png)

Some examples of lattice:

* (S, ≤) , S is a set of integers
  * ⊔ means “max”
  * ⊓ means “min”
* (S, subset) , S is the power set of set {a,b,c}
  * ⊔ means ∪
  * ⊓ means ∩

## Semilattice

![image-20240405095649604](./../.gitbook/assets/image-20240405095649604.png)

## Complete Lattice

![image-20240405095713765](./../.gitbook/assets/image-20240405095713765.png)

Some examples of complete lattice:

* (S, ≤) , S is a set of integers
  * × not a complete lattice
  * it has no ⊔S（+∞）
* (S, subset) , S is the power set of set {a,b,c}
  * √

Note: the definition of bounds implies that the bounds are not necessarily in the subsets (but they must be in the lattice)

![image-20240405100122578](./../.gitbook/assets/image-20240405100122578.png)

A complete lattice is not necessarily a finite lattice!

## Product Lattice

![image-20240405101104252](./../.gitbook/assets/image-20240405101104252.png)

## DFA Framework via Lattice

![image-20240405101304929](./../.gitbook/assets/image-20240405101304929.png)

![image-20240405103624941](./../.gitbook/assets/image-20240405103624941.png)

![image-20240405103653446](./../.gitbook/assets/image-20240405103653446.png)

![image-20240405103708296](./../.gitbook/assets/image-20240405103708296.png)

![image-20240405103733012](./../.gitbook/assets/image-20240405103733012.png)

Data flow analysis can be seen as iteratively applying transfer functions and meet/join operations on the values of a lattice

## Monotonicity

![image-20240405104548572](./../.gitbook/assets/image-20240405104548572.png)

## Fixed Point Theorem

![image-20240405104608361](./../.gitbook/assets/image-20240405104608361.png)

Proof：

![image-20240405111438552](./../.gitbook/assets/image-20240405111438552.png)

![image-20240405111741958](./../.gitbook/assets/image-20240405111741958.png)

## Relate to Iterative Algorithm

![image-20240405120423891](./../.gitbook/assets/image-20240405120423891.png)

two conditions of fixed point theorem:

* L is finite
* f is monotonic

If a product lattice Lk is a product of complete(and finite) lattices, i.e., (L, L, …, L), then Lk is also complete (and finite) =》 L is a finite and complete lattice

In each iteration, it is equivalent to think that we apply function F which consists of

* transfer function fi: L → L for every node
* join/meet function ⊔/⊓: L×L×L... → L 

for control-flow confluence

How to prove function F is monotonic?

first, transfer function is obviously monotonic(Gen/Kill function is monotonic, IN/OUT never shrinks)

next, prove that ⊔/⊓ is monotonic

![image-20240405120943340](./../.gitbook/assets/image-20240405120943340.png)

When will the algorithm reach the fixed point?

![image-20240405121202416](./../.gitbook/assets/image-20240405121202416.png)

To sum up, we can draw these conclusions:

1. the iterative algorithm is guaranteed to terminate（reach the fixed point）
2. There may be more than one solution, but our solution is the best one（least/greatest fixed point）
3. Worst case of iterations: lattice height × nodes num in CFG

## May & Must analysis

![image-20240405125725741](./../.gitbook/assets/image-20240405125725741.png)

## MOP

How precise is our solution?

MOP: Meet Over All Paths

![image-20240405143022516](./../.gitbook/assets/image-20240405143022516.png)

![image-20240405143044398](./../.gitbook/assets/image-20240405143044398.png)

![image-20240405143115949](./../.gitbook/assets/image-20240405143115949.png)

Bit-vector or Gen/Kill problems(set union/intersection for join/meet) are distributive.

But some analyses are not distributive

# Constant Propagation

Given a variable x at program point p, determine whether x is guaranteed to hold a constant value at p.

The OUT of each node in CFG, includes a set of pairs (x, v) where x is a variable and v is the value held by x after that node

![image-20240405143505512](./../.gitbook/assets/image-20240405143505512.png)

![image-20240405143531481](./../.gitbook/assets/image-20240405143531481.png)

![image-20240405143627014](./../.gitbook/assets/image-20240405143627014.png)

# Worklist algorithm

an optimization of Iterative Algorithm.

As iteration goes on in the iterative algorithm, we need to apply F to every node even if one node’s OUT changes. 

![image-20240405144258241](./../.gitbook/assets/image-20240405144258241.png)

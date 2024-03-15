# 0x03 Data Flow Analysis

## Overview of DFA

DFA：Data Flow Analysis

How application-specific data flows through the nodes and edges of CFG（source code->IR->CFG）

> most static analyzer tends to may analysis
>
> * may analysis
>   * outputs information that may be true (over-approximation)
> * must analysis
>   * outputs information that must be true (under-approximation)

different data-flow analysis applications have

different data abstraction and

different flow safe-approximation strategies

different transfer functions and control-flow handlings

## Preliminaries of DFA

### Input and Output States

* Each execution of an IR statement transforms an input state to a new output state
* The input/output state is associated with the program point before/after the statement
* ![image-20240312212229562](./../.gitbook/assets/image-20240312212229562.png)

> In each data-flow analysis application, we associate with every program point a data-flow value that represents an abstraction of the set of all possible program states that can be observed for that point.

### Transfer Function

![image-20240312213047748](./../.gitbook/assets/image-20240312213047748.png)

### Control Flow

![image-20240312213409543](./../.gitbook/assets/image-20240312213409543.png)

## Reaching Definitions analysis

> A definition d at program point p reaches a point q if there is path from p to q such that d is not “killed” along that path

* A definition of a variable v is a statement that assigns a value to v
* how to be “killed”: new definition of v

Reaching definitions can be used to detect possible undefined variables.

> e.g., introduce a dummy definition for each variable v at the entry of CFG, and if the dummy definition of v reaches a point p where v is used, then v may be used before definition (as undefined reaches v)

So reaching definitions analysis is may analysis.

(不放过动态运行时所有可能的路径)

















## Live Variables Analysis











## Available Expressions Analysis


























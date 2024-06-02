# 0x01 What Is CodeQL

CodeQL是一个支持多种语言及框架的代码分析工具，由`Semmle`公司开发，现已被`Github`收购。其可以从代码中提取信息构成数据库，我们通过编写查询语句来获取信息，分析可能存在的漏洞。codeql处理的对象是AST数据库。

## Install CodeQL

CodeQL包括`引擎`和`库`两部分。核心的解析引擎不开源，用于解析数据库执行查询等操作的SDK库是开源的。

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

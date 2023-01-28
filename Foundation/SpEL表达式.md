# 0x01 What Is SpEL

SpEL：Spring Expression Language 一种表达式语言，支持在运行时查询和操作对象图。
类似于EL表达式，感觉就是简洁化的Java代码。

SpEL 的诞生是为了给 Spring 社区提供一种能够与Spring 生态系统所有产品无缝对接，能提供一站式支持的表达式语言。

SpEL基本语法：
SpEL使用 `#{...}`作为定界符，大括号内被视为SpEL表达式，里面可以使用运算符，变量，调用方法等。使用 `T()` 运算符会调用类作用域的方法和常量，如`#{T(java.lang.Math)}`返回一个java.lang.Math类对象

SpEL常用在三个地方。

1. Value注解
   （注：这个类要通过依赖注入才能使Value注解生效，直接new对象是不行的）

   ```java
   package com.example.demo1.bean;
   
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.context.annotation.PropertySource;
   import org.springframework.stereotype.Component;
   
   @Component
   @PropertySource({"classpath:/configure.properties"})
   public class User {
       @Value("${spring.user.name}")
       public String userName; // 值来自application.properties
       @Value("${home.dorm}")
       public String address; // 值来自configure.properties(放在resources文件夹下)
       @Value("#{T(java.lang.Math).random()}")
       public double age;
       @Value("#{systemProperties['os.name']}")
       public String sys; // 注入操作系统属性
   }
   ```

   ```xml
   // configure.properties
   home.dorm=Room402,Unit4,Building3,No.34.LousyLoad
   // application.properti
   spring.user.name=Taco
   ```

   输出如下：
   `User{userName='Taco', address='Room402,Unit4,Building3,No.34.LousyLoad', age=0.5913714334107036, sys='Windows 10'}`

2. XML
   ```xml
   <bean id="Book" class="com.example.bean">
   	<property name="author" value="#{表达式}">
   </bean>
   ```

3. Expression接口
   ```java
   @Test
   public void spelTest() {
       ExpressionParser parser = new SpelExpressionParser();
       Expression expression = parser.parseExpression("('Hello '+'SpEL').concat(#end)");
       EvaluationContext context = new StandardEvaluationContext();
       context.setVariable("end", "!");
       System.out.println(expression.getValue(context));
   }
   ```

   输出`Hello SpEL!`

# 0x02 Way To Attack

实际情况下，一般都是基于上面第三种SpEL的使用场景出现的漏洞。
下面简单分析一下SpEL在求表达式值的过程

> 1.创建解析器 new SpelExpressionParser()
> 2.解析表达式 parseExpression(your_expression)
> 3.构造上下文 new StandardEvaluationContext() 默认为这个
> 4.求值 expression.getValue(context)

漏洞利用前提

> 1.服务器接收用户输入的表达式
> 2.表达式解析之后调用了getValue
> 3.使用StandardEvaluationContext作为上下文对象

```java
@RestController
public class SpELController {
    @GetMapping("spel")
    public String spel(@RequestParam(name="cmd")String cmd) {
        System.out.println("Hello SpEL!!!");
        ExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression(cmd);
        Object obj = expression.getValue();
        return obj.toString();
    }
}
```

这段代码中，可注入的点在请求参数cmd
访问`http://localhost:8080/spel?cmd=T(java.lang.Runtime).getRuntime().exec(%27calc%27)`
成功弹出计算器

# 0x03 CVE To Study

* CVE-2022-22980 Spring Data MongoDB SpEL表达式注入
* CVE-2022-22963 SpringCloud Function SpEL表达式注入
* CVE-2022-22947 Spring Cloud Gateway 远程代码执行

# 0x04 Patch

`SimpleEvaluationContext`、`StandardEvaluationContext` 是 SpEL提供的两个 `EvaluationContext`
`SimpleEvaluationContext` 旨在仅支持 SpEL 语言语法的一个子集。它不包括 Java 类型引用，构造函数和 bean 引用

用`SimpleEvaluationContext`替换默认的`StandardEvaluationContext`，就能有效防止恶意SpEL表达式的执行。


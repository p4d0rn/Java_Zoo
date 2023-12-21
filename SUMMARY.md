# Table of contents

* [About This Book](README.md)

## 🍖Prerequisites

* [反射](Foundation/reflection.md)
* [命令执行](./Foundation/exec.md)
* [高版本JDK反射绕过](./Foundation/reflection2.md)
* [Unsafe](./Foundation/unsafe.md)
* [Java Security Manager](./Foundation/securityManager.md)
* [serialVersionUID](./Foundation/serialVersionUID.md)
* [代理模式](./Foundation/proxy.md)
* [双亲委派模型](./Foundation/Parents_Delegate.md)
* [SPI](./Foundation/SPI.md)
* [动态加载字节码](./Foundation/ClassLoader.md)
* [BCEL](/Foundation/BCEL.md)
* [URLDNS](Foundation/URLDNS.md)
* [RPC Intro](Foundation/RPC.md)
* [RMI](Foundation/RMI.md)
* [JEP 290](Foundation/jep.md)
* [JNDI](Foundation/JNDI.md)
* [JMX](Foundation/JMX.md)
* [JDWP]()
* [Tomcat架构](Foundation/tomcat.md)
* [SpEL表达式](Foundation/SpEL.md)
* [JNI](./Foundation/JNI.md)

## 👻Serial Journey

* Commons Collection 🥏

  * [CC1-TransformedMap](./Deserial/CC1_TransformedMap.md)
  * [CC1-LazyMap](./Deserial/CC1_LazyMap.md)
  * [CC6](./Deserial/CC6.md)
  * [CC3](./Deserial/CC3.md)
  * [CC2](./Deserial/CC2.md)
* FastJson 🪁

  * [FastJson-Basic Usage](./Deserial/FastJsonBasic.md)

  * [FastJson-TemplatesImpl](./Deserial/FastJson_TemplatesImpl.md)

  * [FastJson-JdbcRowSetImpl](./Deserial/FastJson_JdbcRowSetImpl.md)

  * [FastJson-BasicDataSource](/Foundation/BCEL.md)

  * [FastJson-ByPass](./Deserial/FastJson_ByPass.md)

  * [FastJson与原生反序列化(一)](https://paper.seebug.org/2055/)

  * [FastJson与原生反序列化(二)](https://y4tacker.github.io/2023/04/26/year/2023/4/FastJson与原生反序列化-二/)

  * [Jackson的原生反序列化利用](./Deserial/jackson.md)
* [SnakeYaml](./Deserial/SnakeYaml.md)
* [C3P0](./Deserial/C3P0.md)
* [Log4j](./Deserial/log4j2.md)
* [AspectJWeaver](./Deserial/AspectJWeaver.md)
* [Rome](./Deserial/Rome.md)
* [Spring](./Deserial/spring.md)
* [Hessian](./Deserial/Hessian.md)
* [Hessian_Only_JDK](./Deserial/hessian_only_jdk.md)
* [Kryo](./Deserial/Kryo.md)
* [Dubbo](./Deserial/dubbo.md)

## ✂️JDBC Attack

* [MySQL JDBC Attack](./JDBC/mysql.md)
* [H2 JDBC Attack](./JDBC/h2.md)

## 🌵RASP

* [JavaAgent](./Foundation/JavaAgent.md)
* [JVM](./RASP/jvm.md)
* [ByteCode](./RASP/bytecode.md)
* ASM 🪡
  * [ASM Intro](./RASP/asm0.md)
  * [Class Generation](./RASP/asm1.md)
  * [Class Transformation](./RASP/asm2.md)
* [Rasp防御命令执行](./RASP/rasp1.md)
* [OpenRASP]()

## 🐎Memory Shell

* Servlet API

  * [Tomcat-Listener](./MemShell/listener.md)

  * [Tomcat-Filter](./MemShell/filter.md)

  * [Tomcat-Servlet](./MemShell/servlet.md)
* Tomcat-Middlewares

  * [Tomcat-Valve](./MemShell/valve.md)
  * [Tomcat-Executor](./MemShell/executor.md)
  * [Tomcat-Upgrade](./MemShell/upgrade.md)
  * [Tomcat-Poller]()
* [Agent MemShell](./MemShell/agent.md)
* [WebSocket](./MemShell/websocket.md)
* [Tomcat JSP](./MemShell/jsp.md)
* [IDEA本地调试Tomcat](./MemShell/de_tomcat.md)


## 🛡️Shiro

* [Shiro Intro](./Shiro/shiro.md)

* [Request URI ByPass](./Shiro/CVE-2010-3863.md)

* [Context Path ByPass](./Shiro/CVE-2016-6802.md)

* [Remember Me反序列化 CC-Shiro](./Shiro/CC-Shiro.md)
* [CB1与无CC依赖的反序列化链](./Shiro/CB1.md)

## 🍺Others

* [Deserialization Twice](./Others/deserTwice.md)

* [A New Blazer 4 getter RCE](./Others/newGetter.md)

* [Apache Commons Jxpath](./Others/jxpath.md)

* [El Attack](./Others/elAttack.md)

* Echo Tech
  * [SpringBoot Under Tomcat](./Echo/sbTomcat.md)

## 🎨Templates

* [FreeMarker](./Templates/freemarker.md)
* [Thymeleaf](./Templates/thymeleaf.md)
* [Enjoy](./Templates/enjoy.md)

## 🎏MessageQueue

* [ActiveMQ CNVD-2023-69477](./MessageQueue/activemq.md)
* [AMQP CVE-2023-34050](./MessageQueue/ampq.md)
* [Spring-Kafka CVE-2023-34040](./MessageQueue/kafka.md)
* [RocketMQ CVE-2023-33246](./MessageQueue/rocketmq.md)


## 🚩CTF

* [长城杯-b4bycoffee (ROME反序列化)](./CTF/b4bycoffee.md)
* [MTCTF2022(CB+Shiro绕过)](./CTF/MTCTF2022.md)
* [CISCN 2023 西南赛区半决赛 (Hessian原生JDK+Kryo反序列化)](./CTF/seacloud.md)
* [CISCN 2023 初赛 (高版本Commons Collections下其他依赖的利用)](./CTF/deserbug.md)
* [CISCN 2021 总决赛 ezj4va (AspectJWeaver写字节码文件到classpath)](./CTF/ezj4va.md)
* [D^3CTF2023 (新的getter+高版本JNDI不出网+Hessian异常toString)](./CTF/d3java.md)
* [WMCTF2023（CC链花式玩法+盲读文件）](./CTF/WMCTF2023.md)

## 🔍Code Inspector

* CodeQL 🐳
  * [CodeQL Quick Start](./Utils/CodeQL_Basic.md)
  * [CodeQL 4 Java](./Utils/CodeQL4Java.md)
* ByteCodeDL
* Tabby 🦀
* Theory
  * [Static Analysis](./Theory/Static_Analysis.md)

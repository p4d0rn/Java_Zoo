# UnixPrintServiceLookup

JDK中`PrintServiceLookup`接口用于提供打印服务的注册查找功能

在linux的JDK中它的实现类叫做`UnixPrintServiceLookup`或`PrintServiceLookupProvider`（高版本 jdk中）

```java
UnixPrintServiceLookup#init
👇
UnixPrintServiceLookup#getDefaultPrinterNameBSD
👇
UnixPrintServiceLookup#execCmd
```



> 🤬万恶的sun包
>
> J2SE中的类大致可以划分为如下的包：
>
> `java.*`、`javax.*`、`org.*`、`sun.*`
>
> 除了sun包，其他各个包都是Java平台的标准实现，sun包并不包含在Java平台的标准中，它在不同操作系统中的实现也各不相同。

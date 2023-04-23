# 0x01 What IS JNI

JNI：Java Native Interface

Java是基于C语言实现的，很多底层API都是通过调用JNI来实现的。

JNI让Java能够调用编译好的动态链接库里的方法（存在跨平台问题👊）

像我们熟悉的`Class.forName()`最底层就是C去实现的，它被`native`修饰

![image-20230322164250276](../.gitbook/assets/image-20230322164250276.png)

# 0x02 Best Pratice

* 定义native修饰方法

```java
public class JNITest {
    private native void say();
    static {
        System.loadLibrary("JniHi");
        // System.load(String fileName); 加载绝对路径
    }

    public static void main(String[] args) {
        new JNITest().say();
    }
}
```

* `javah`生成`.h`头文件

`javac -h . JNITest.java`（JDK ≥ 10）

老版本：`javah -jni JNITest`  

得到`JNITest.h`头文件和`JNITest.class`文件

* C++实现头文件

VS新建dll项目，调试属性=》C/C++预编译头 => 不使用预编译头

![image-20230322172805579](../.gitbook/assets/image-20230322172805579.png)

生成后得到x64的dll

```c++
// dllmain.cpp : 定义 DLL 应用程序的入口点。
#include <iostream>
#include "jni.h"
#include "JNITest.h"
#include <stdio.h>
#include<stdlib.h>

JNIEXPORT void JNICALL
Java_JNITest_say(JNIEnv* env, jobject obj)
{
	system("calc");
	return;
}
```

javah生成的头文件中的函数命名方式是有非常强制性的约束

`(JNIEnv *, jclass, jstring)`表示分别是`JNI环境变量对象`、`java调用的类对象`、`参数入参类型`

无法包含`jni.h`的看这里：[JNI报错："无法打开源文件jni.h" "JNIEXPORT此声明没有存储类或类型说明符"_"无法打开源文件 “jni.h"](https://blog.csdn.net/michael_f2008/article/details/88525000)

* 加载dll

将生成的`JniHi.dll`放在class同目录下，执行`java JNITest`

成功弹出计算器
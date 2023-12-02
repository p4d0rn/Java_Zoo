Java在反序列化时，会检查序列化字节流中的SerialVersionUID是否和本地类的SerialVersionUID一致，不一致会抛出`InvalidClassException`异常

SerialVersionUID有两种生成方法

* 类中指明：`private static final long serialVersionUID = 1L;`
* `ObjectOutputStream#writeObject`自动计算类的信息自动生成

调用`ObjectOutputStream#writeObject(o)`后

`ObjectOutputStream#writeClassDescriptor` -> `ObjectStreamClass#writeNonProxy` -> `ObjectStreamClass#getSerialVersionUID`

```java
void writeNonProxy(ObjectOutputStream out) throws IOException {
    out.writeUTF(name);
    out.writeLong(getSerialVersionUID()); //...
}

/**
     * Return the serialVersionUID for this class.  The serialVersionUID
     * defines a set of classes all with the same name that have evolved from a
     * common root class and agree to be serialized and deserialized using a
     * common format. NonSerializable classes have a serialVersionUID of 0L.
*/
public long getSerialVersionUID() {
    if (suid == null) {
        suid = AccessController.doPrivileged(
            new PrivilegedAction<Long>() {
                public Long run() {
                    return computeDefaultSUID(cl);
                }
            }
        );
    }
    return suid.longValue();
}
```

下面是serialVersionUID的计算方式

```java
private static long computeDefaultSUID(Class<?> cl) {
    // 没有实现Serializable，serialVersionUID为0L
    if (!Serializable.class.isAssignableFrom(cl) || Proxy.isProxyClass(cl))
    {
        return 0L;
    }

    try {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
		// 类名
        dout.writeUTF(cl.getName());
		
        int classMods = cl.getModifiers() &
            (Modifier.PUBLIC | Modifier.FINAL |
             Modifier.INTERFACE | Modifier.ABSTRACT);

        Method[] methods = cl.getDeclaredMethods();
        if ((classMods & Modifier.INTERFACE) != 0) {
            classMods = (methods.length > 0) ?
                (classMods | Modifier.ABSTRACT) :
            (classMods & ~Modifier.ABSTRACT);
        }
        // 类的访问修饰符
        dout.writeInt(classMods);

        if (!cl.isArray()) {
            Class<?>[] interfaces = cl.getInterfaces();
            String[] ifaceNames = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                ifaceNames[i] = interfaces[i].getName();
            }
            Arrays.sort(ifaceNames);
            for (int i = 0; i < ifaceNames.length; i++) {
                // 类的接口名
                dout.writeUTF(ifaceNames[i]);
            }
        }

        Field[] fields = cl.getDeclaredFields();
        MemberSignature[] fieldSigs = new MemberSignature[fields.length];
        for (int i = 0; i < fields.length; i++) {
            fieldSigs[i] = new MemberSignature(fields[i]);
        }
        Arrays.sort(fieldSigs, new Comparator<MemberSignature>() {
            public int compare(MemberSignature ms1, MemberSignature ms2) {
                return ms1.name.compareTo(ms2.name);
            }
        });
        for (int i = 0; i < fieldSigs.length; i++) {
            MemberSignature sig = fieldSigs[i];
            int mods = sig.member.getModifiers() &
                (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
                 Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE |
                 Modifier.TRANSIENT);
            if (((mods & Modifier.PRIVATE) == 0) ||
                ((mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0))
            {
                // 非私有或私有非静态、非transient的字段名、访问修饰符、签名
                dout.writeUTF(sig.name);
                dout.writeInt(mods);
                dout.writeUTF(sig.signature);
            }
        }
		
        // 是否有静态字段
        if (hasStaticInitializer(cl)) {
            dout.writeUTF("<clinit>");
            dout.writeInt(Modifier.STATIC);
            dout.writeUTF("()V");
        }

        Constructor<?>[] cons = cl.getDeclaredConstructors();
        MemberSignature[] consSigs = new MemberSignature[cons.length];
        for (int i = 0; i < cons.length; i++) {
            consSigs[i] = new MemberSignature(cons[i]);
        }
        Arrays.sort(consSigs, new Comparator<MemberSignature>() {
            public int compare(MemberSignature ms1, MemberSignature ms2) {
                return ms1.signature.compareTo(ms2.signature);
            }
        });
        for (int i = 0; i < consSigs.length; i++) {
            MemberSignature sig = consSigs[i];
            int mods = sig.member.getModifiers() &
                (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
                 Modifier.STATIC | Modifier.FINAL |
                 Modifier.SYNCHRONIZED | Modifier.NATIVE |
                 Modifier.ABSTRACT | Modifier.STRICT);
            if ((mods & Modifier.PRIVATE) == 0) {
                // 非私有构造器
                dout.writeUTF("<init>");
                dout.writeInt(mods);
                dout.writeUTF(sig.signature.replace('/', '.'));
            }
        }

        MemberSignature[] methSigs = new MemberSignature[methods.length];
        for (int i = 0; i < methods.length; i++) {
            methSigs[i] = new MemberSignature(methods[i]);
        }
        Arrays.sort(methSigs, new Comparator<MemberSignature>() {
            public int compare(MemberSignature ms1, MemberSignature ms2) {
                int comp = ms1.name.compareTo(ms2.name);
                if (comp == 0) {
                    comp = ms1.signature.compareTo(ms2.signature);
                }
                return comp;
            }
        });
        for (int i = 0; i < methSigs.length; i++) {
            MemberSignature sig = methSigs[i];
            int mods = sig.member.getModifiers() &
                (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
                 Modifier.STATIC | Modifier.FINAL |
                 Modifier.SYNCHRONIZED | Modifier.NATIVE |
                 Modifier.ABSTRACT | Modifier.STRICT);
            if ((mods & Modifier.PRIVATE) == 0) {
                // 非私有方法
                dout.writeUTF(sig.name);
                dout.writeInt(mods);
                dout.writeUTF(sig.signature.replace('/', '.'));
            }
        }

        dout.flush();

        MessageDigest md = MessageDigest.getInstance("SHA");
        byte[] hashBytes = md.digest(bout.toByteArray());
        long hash = 0;
        for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
            hash = (hash << 8) | (hashBytes[i] & 0xFF);
        }
        return hash;
    } catch (IOException ex) {//...}
}
```

计算的内容如下：

* 类名、类的访问修饰符、类的接口名
* 非私有或私有非静态、非transient的字段名、访问修饰符、签名
* 非私有构造器
* 非私有方法

对这些数据进行SHA1哈希，再进行移位和与或运算

反射调用`computeDefaultSUID`来计算一个类的`SerialVersionUID`

```java
Class<?> clazz = Class.forName("java.io.ObjectStreamClass");
Method method = clazz.getDeclaredMethod("computeDefaultSUID", Class.class);
method.setAccessible(true);
Object suid = method.invoke(null, Dog.class);
System.out.println(suid);
```

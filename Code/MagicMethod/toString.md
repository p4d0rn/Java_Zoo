`toString`的触发

# XString

`com.sun.org.apache.xpath.internal.objects.XString`

`HashMap#readObject -> HashMap#putVal -> HotSwappableTargetSource#equals -> XString#equals`

```java
public boolean equals(Object obj2) {
    if (null == obj2)
        return false;
    else if (obj2 instanceof XNodeSet)
        return obj2.equals(this);
    else if(obj2 instanceof XNumber)
        return obj2.equals(this);
    else
        return str().equals(obj2.toString()); 👈
}
```

# BadAttributeValueExpException

`javax.management.BadAttributeValueExpException`

```java
private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField gf = ois.readFields();
    Object valObj = gf.get("val", null);

    if (valObj == null) {
        val = null;
    } else if (valObj instanceof String) {
        val= valObj;
    } else if (System.getSecurityManager() == null
               || valObj instanceof Long
               || valObj instanceof Integer
               || valObj instanceof Float
               || valObj instanceof Double
               || valObj instanceof Byte
               || valObj instanceof Short
               || valObj instanceof Boolean) {
        val = valObj.toString(); 👈
    } else {//...}
}
```








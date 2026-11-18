package rift;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class Rooms {
    private Rooms(){}
    public static final String ENDPOINT="";
    public static final class Snapshot {
        public Model state;
        public int rev;
        public long now;
        public long offset;
    }
    public static Snapshot request(String endpoint,String action,String code,int revision,Model state)throws IOException{
        JsonObject body=new JsonObject();
        body.addProperty("action",action);body.addProperty("code",code);body.addProperty("rev",revision);
        long started=System.currentTimeMillis();body.addProperty("clientNow",started);
        if(state!=null)body.add("state",Model.JSON.toJsonTree(state));
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        try{
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(6500);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
            c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();
            InputStream input=status==200?c.getInputStream():c.getErrorStream();
            if(input==null)throw new IOException("服务器返回 "+status);
            try(Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
                JsonObject reply=JsonParser.parseReader(reader).getAsJsonObject();
                if(status!=200)throw new IOException(reply.has("error")?reply.get("error").getAsString():"服务器返回 "+status);
                Snapshot result=Model.JSON.fromJson(reply,Snapshot.class);
                if(result.state==null||!result.state.valid()||result.now<=0||result.rev<1)throw new IOException("服务器对局数据无效");
                result.offset=result.now-(started+(System.currentTimeMillis()-started)/2);
                return result;
            }
        }catch(JsonParseException|IllegalStateException e){throw new IOException("服务器响应无效",e);}
        finally{c.disconnect();}
    }
}


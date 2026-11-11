package dev.codexboard.diagnostic;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.*;

/** Local authenticated CONNECT tunnel. TLS remains end-to-end. */
final class LocalConnectProxy implements Closeable {
    private final ServerSocket server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(8);
    private final String secret;
    private final String expectedAuth;
    private final boolean allowOtherHosts;
    LocalConnectProxy() throws IOException { this(false); }
    LocalConnectProxy(boolean allowOtherHosts) throws IOException {
        this.allowOtherHosts = allowOtherHosts;
        byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
        secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        expectedAuth = "Basic " + Base64.getEncoder().encodeToString(("codex:" + secret).getBytes(StandardCharsets.US_ASCII));
        server = new ServerSocket(); server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
        Thread listener = new Thread(() -> { while (!server.isClosed()) { try { Socket client=server.accept(); handlers.execute(() -> handle(client)); } catch(Exception e) { if(server.isClosed()) break; } } }, "codex-proxy");
        listener.setDaemon(true); listener.start();
    }
    String url() { return "http://codex:" + secret + "@127.0.0.1:" + server.getLocalPort(); }
    private void handle(Socket client) {
        try (Socket c=client) {
            c.setSoTimeout(15000);
            InputStream in=c.getInputStream(); OutputStream out=c.getOutputStream();
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            int state=0, ch;
            while(buf.size()<16384 && (ch=in.read())!=-1) { buf.write(ch); state=(state==0&&ch==13)?1:(state==1&&ch==10)?2:(state==2&&ch==13)?3:(state==3&&ch==10)?4:0; if(state==4)break; }
            if(state!=4) return;
            String[] lines=buf.toString("ISO-8859-1").split("\\r\\n");
            String[] request=lines[0].split(" "); String auth="";
            for(String line:lines) if(line.toLowerCase(Locale.ROOT).startsWith("proxy-authorization:")) auth=line.substring(line.indexOf(':')+1).trim();
            if(!java.security.MessageDigest.isEqual(auth.getBytes(StandardCharsets.US_ASCII), expectedAuth.getBytes(StandardCharsets.US_ASCII))) { out.write("HTTP/1.1 407 Proxy Authentication Required\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); return; }
            if(request.length<2||!"CONNECT".equals(request[0])) return;
            String[] target=request[1].split(":",-1); if(target.length!=2||!"443".equals(target[1]))return;
            String host=target[0].toLowerCase(Locale.ROOT);
            if(!allowOtherHosts&&!(host.equals("openai.com")||host.endsWith(".openai.com")||host.equals("chatgpt.com")||host.endsWith(".chatgpt.com"))) return;
            try(Socket upstream=new Socket()) {
                upstream.connect(new InetSocketAddress(host,443),15000); c.setSoTimeout(0);
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
                Thread reverse=new Thread(() -> copy(upstream,c),"codex-proxy-return"); reverse.setDaemon(true); reverse.start();
                copy(c,upstream);
                try { reverse.join(1000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        } catch(IOException ignored) { }
    }
    private static void copy(Socket from, Socket to) {
        try { InputStream in=from.getInputStream(); OutputStream out=to.getOutputStream(); byte[] b=new byte[16384]; int n; while((n=in.read(b))!=-1) {out.write(b,0,n); out.flush();} to.shutdownOutput(); } catch(IOException ignored) { }
    }
    @Override public void close() throws IOException { server.close(); handlers.shutdownNow(); }
}

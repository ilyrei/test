package dev.codexboard.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Experimental app-server client. No API key is used. */
public final class ChatActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Integer,String> pending = new ConcurrentHashMap<>();
    private final List<String> modelIds = new ArrayList<>();
    private Process process;
    private LocalConnectProxy proxy;
    private BufferedWriter writer;
    private TextView status, transcript;
    private EditText input;
    private Spinner models;
    private Button login, send;
    private int nextId = 1;
    private volatile boolean ready, loggedIn, busy;
    private volatile String threadId;
    private String pendingText;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1);
        int pad=(int)(12*getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        TextView heading=new TextView(this); heading.setText("Codex 로그인·대화 시험 v0.5"); heading.setTextSize(22); root.addView(heading);
        status=new TextView(this); status.setText("앱 서버 시작 중…"); status.setTextSize(16); root.addView(status);
        LinearLayout row=new LinearLayout(this);
        login=new Button(this); login.setText("ChatGPT 로그인"); row.addView(login);
        models=new Spinner(this); row.addView(models,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row);
        transcript=new TextView(this); transcript.setTextSize(18); transcript.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this); scroll.addView(transcript); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        input=new EditText(this); input.setHint("질문을 입력하세요"); input.setMinLines(2); root.addView(input);
        send=new Button(this); send.setText("전송"); root.addView(send); setContentView(root);
        login.setEnabled(false); send.setEnabled(false);
        login.setOnClickListener(v -> worker.execute(() -> request("account/login/start", obj("type","chatgptDeviceCode"), "login")));
        send.setOnClickListener(v -> { String text=input.getText().toString().trim(); if(text.isEmpty()||busy)return; input.setText(""); append("\n나: "+text+"\nCodex: "); busy=true; refresh(); worker.execute(() -> startTurn(text)); });
        worker.execute(this::startServer);
    }
    private void startServer() {
        try {
            File home=new File(getFilesDir(),"home"), codex=new File(home,".codex"), tmp=new File(getCacheDir(),"tmp"), workspace=new File(getFilesDir(),"workspace");
            for(File dir:new File[]{home,codex,tmp,workspace}) if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("작업 폴더 생성 실패");
            ProcessBuilder b=new ProcessBuilder(new File(getApplicationInfo().nativeLibraryDir,"libcodex.so").getAbsolutePath()).directory(workspace);
            b.environment().put("HOME",home.getAbsolutePath()); b.environment().put("CODEX_HOME",codex.getAbsolutePath()); b.environment().put("TMPDIR",tmp.getAbsolutePath());
            File certFile = writeSystemCertificates();
            proxy = new LocalConnectProxy();
            b.environment().put("HTTPS_PROXY",proxy.url()); b.environment().put("https_proxy",proxy.url());
            b.environment().put("SSL_CERT_FILE",certFile.getAbsolutePath()); b.environment().put("CODEX_CA_CERTIFICATE",certFile.getAbsolutePath());
            process=b.start(); writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),"UTF-8"));
            Thread stderr=new Thread(() -> { try { byte[] bytes=new byte[4096]; while(process.getErrorStream().read(bytes)!=-1) {} } catch(IOException ignored){} },"codex-stderr"); stderr.setDaemon(true); stderr.start();
            Thread reader=new Thread(() -> { try { BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8")); String line; while((line=in.readLine())!=null) handle(new JSONObject(line)); } catch(Exception e) { updateStatus("서버 연결 종료: "+e.getClass().getSimpleName()); } finally { ready=false; refresh(); } },"codex-rpc"); reader.setDaemon(true); reader.start();
            JSONObject client=obj("name","codex_board"); client.put("version","0.5");
            request("initialize",new JSONObject().put("clientInfo",client),"initialize");
        } catch(Exception e) { updateStatus("서버 시작 실패: "+e.getClass().getSimpleName()+": "+e.getMessage()); }
    }
    private File writeSystemCertificates() throws Exception {
        java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidCAStore"); store.load(null);
        File file=new File(getFilesDir(),"system-ca-bundle.pem"); int count=0;
        try(Writer out=new OutputStreamWriter(new FileOutputStream(file),"US-ASCII")) {
            java.util.Enumeration<String> aliases=store.aliases();
            while(aliases.hasMoreElements()) { String alias=aliases.nextElement(); if(!alias.startsWith("system:"))continue;
                java.security.cert.Certificate cert=store.getCertificate(alias); if(cert==null)continue;
                out.write("-----BEGIN CERTIFICATE-----\n"); out.write(java.util.Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(cert.getEncoded())); out.write("\n-----END CERTIFICATE-----\n"); count++;
            }
        }
        if(count==0) throw new IOException("시스템 인증서를 찾지 못했습니다");
        return file;
    }
    private synchronized void write(JSONObject msg) throws Exception { if(writer==null) throw new IOException("서버가 실행되지 않음"); writer.write(msg.toString()); writer.newLine(); writer.flush(); }
    private synchronized void request(String method, JSONObject params, String kind) {
        try { int id=nextId++; pending.put(id,kind); write(new JSONObject().put("id",id).put("method",method).put("params",params)); }
        catch(Exception e) { busy=false; updateStatus("요청 실패: "+e.getClass().getSimpleName()+": "+e.getMessage()); refresh(); }
    }
    private void handle(JSONObject msg) throws Exception {
        String method=msg.optString("method","");
        if(!method.isEmpty() && msg.has("id")) { write(new JSONObject().put("id",msg.get("id")).put("error",new JSONObject().put("code",-32601).put("message","This diagnostic client does not support tool approvals"))); updateStatus("명령 실행은 이 시험판에서 지원하지 않아요."); return; }
        if(msg.has("id")) {
            String kind=pending.remove(msg.optInt("id")); if(kind==null)return;
            if(msg.has("error")) { busy=false; updateStatus("요청 실패 ("+kind+"): "+msg.optJSONObject("error").optString("message")); refresh(); return; }
            JSONObject result=msg.optJSONObject("result"); if(result==null)result=new JSONObject();
            switch(kind) {
                case "initialize":
                    write(new JSONObject().put("method","initialized").put("params",new JSONObject())); ready=true; refresh();
                    request("account/read",obj("refreshToken",false),"account"); request("model/list",obj("limit",20),"models"); break;
                case "account":
                    loggedIn=result.optJSONObject("account")!=null; updateStatus(loggedIn?"로그인됨":"로그인이 필요해요."); refresh(); break;
                case "login":
                    final String url=result.optString("verificationUrl"), code=result.optString("userCode");
                    if(!url.startsWith("https://auth.openai.com/")||code.isEmpty()) { updateStatus("기기 코드 로그인 응답을 확인할 수 없어요."); break; }
                    main.post(() -> new AlertDialog.Builder(this).setTitle("ChatGPT 로그인").setMessage("코드: "+code+"\n\n브라우저에서 로그인하고 이 코드를 입력하세요.").setPositiveButton("브라우저 열기",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)))).setNegativeButton("닫기",null).show()); break;
                case "models":
                    JSONArray data=result.optJSONArray("data"); final List<String> names=new ArrayList<>(), ids=new ArrayList<>();
                    if(data!=null)for(int i=0;i<data.length();i++){JSONObject model=data.optJSONObject(i); if(model!=null){String id=model.optString("model",model.optString("id")); if(!id.isEmpty()){ids.add(id); names.add(model.optString("displayName",id));}}}
                    main.post(() -> { modelIds.clear(); modelIds.addAll(ids); if(names.isEmpty()) names.add("기본 모델"); models.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names)); }); break;
                case "thread":
                    JSONObject thread=result.optJSONObject("thread"); threadId=thread==null?null:thread.optString("id",null);
                    if(threadId==null){busy=false;updateStatus("대화를 시작할 수 없어요.");refresh();} else sendTurn(pendingText); break;
                case "turn": break;
            }
            return;
        }
        JSONObject params=msg.optJSONObject("params"); if(params==null)params=new JSONObject();
        if(method.equals("account/login/completed")) { if(params.optBoolean("success")){loggedIn=true;updateStatus("로그인 성공");request("model/list",obj("limit",20),"models");} else updateStatus("로그인 실패: "+params.optString("error")); refresh(); }
        else if(method.equals("item/agentMessage/delta")) append(params.optString("delta"));
        else if(method.equals("turn/completed")) { JSONObject turn=params.optJSONObject("turn"); if(turn!=null&&!"completed".equals(turn.optString("status"))) append("\n[작업 상태: "+turn.optString("status")+" "+turn.optString("error")+"]"); busy=false; updateStatus("대화 완료"); refresh(); }
        else if(method.equals("error")) updateStatus("서버 오류: "+params.optString("error"));
    }
    private void startTurn(String text) {
        try { if(threadId==null) { pendingText=text; JSONObject p=new JSONObject().put("cwd",new File(getFilesDir(),"workspace").getAbsolutePath()).put("approvalPolicy","on-request").put("sandbox","read-only"); main.post(() -> { int i=models.getSelectedItemPosition(); if(i>=0&&i<modelIds.size())try{p.put("model",modelIds.get(i));}catch(Exception ignored){} worker.execute(() -> request("thread/start",p,"thread")); }); } else sendTurn(text); }
        catch(Exception e){ busy=false;updateStatus("대화 시작 실패: "+e.getMessage());refresh(); }
    }
    private void sendTurn(String text) { try { request("turn/start",new JSONObject().put("threadId",threadId).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",text))),"turn"); } catch(Exception e){busy=false;updateStatus("전송 실패: "+e.getMessage());refresh();} }
    private static JSONObject obj(String key,Object value) { try{return new JSONObject().put(key,value);}catch(Exception e){return new JSONObject();} }
    private void updateStatus(String s){main.post(()->status.setText(s));}
    private void append(String s){main.post(()->{ transcript.append(s); if(transcript.length()>120000)transcript.setText(transcript.getText().subSequence(transcript.length()-100000,transcript.length())); });}
    private void refresh(){main.post(()->{login.setEnabled(ready&&!loggedIn);send.setEnabled(ready&&loggedIn&&!busy);});}
    @Override public void onDestroy(){ if(process!=null)process.destroy(); if(proxy!=null)try{proxy.close();}catch(IOException ignored){} worker.shutdownNow(); super.onDestroy(); }
}

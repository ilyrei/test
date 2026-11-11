package dev.codexboard.diagnostic;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView output;
    private Button run;
    private final StringBuilder report = new StringBuilder();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);
        TextView title = new TextView(this);
        title.setText("Codex 칠판 호환성 진단 v0.3"); title.setTextSize(24);
        root.addView(title);
        TextView explanation = new TextView(this);
        explanation.setText("공식 Codex 실행 파일과 이 기기의 기본 환경을 확인합니다. 로그인이나 질문 전송은 하지 않습니다.");
        explanation.setTextSize(16); root.addView(explanation);
        LinearLayout buttons = new LinearLayout(this);
        run = new Button(this); run.setText("진단 시작");
        Button copy = new Button(this); copy.setText("결과 복사");
        buttons.addView(run, new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(copy, new LinearLayout.LayoutParams(0,-2,1)); root.addView(buttons);
        output = new TextView(this); output.setTextSize(16); output.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(output); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        run.setOnClickListener(v -> { run.setEnabled(false); report.setLength(0); output.setText(""); worker.execute(() -> { diagnose(); main.post(() -> run.setEnabled(true)); }); });
        copy.setOnClickListener(v -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Codex 진단",output.getText())); copy.setText("복사됨"); });
        log("진단 시작을 누르세요.");
    }
    private void log(String s) { main.post(() -> { report.append(s).append('\n'); output.setText(report.toString()); }); }
    private void diagnose() {
        log("진단 앱 버전: 0.3");
        log("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        log("CPU ABI: " + Arrays.toString(Build.SUPPORTED_ABIS));
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        log("시스템 가용 메모리: " + mi.availMem / 1048576 + " MiB");
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libcodex.so");
        log("Codex 실행 파일: " + (binary.isFile() ? "설치됨 (" + binary.length()/1048576 + " MiB)" : "찾지 못함"));
        try {
            Process p = prepareProcess(binary, "--version").redirectErrorStream(true).start();
            FutureTask<String> read = new FutureTask<>(() -> readLimited(p.getInputStream()));
            new Thread(read,"version-reader").start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); log("버전 실행: 15초 시간 초과"); }
            else log("버전 실행: 종료 코드 " + p.exitValue() + "\n" + read.get(2,TimeUnit.SECONDS));
        } catch (Exception e) { log("버전 실행 실패: " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
        try {
            Process p = prepareProcess(binary).start();
            FutureTask<String> read = new FutureTask<>(() -> { BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8")); return b.readLine(); });
            Thread t = new Thread(read,"rpc-reader"); t.setDaemon(true); t.start();
            FutureTask<String> errors = new FutureTask<>(() -> readLimited(p.getErrorStream()));
            Thread errorThread = new Thread(errors,"stderr-reader"); errorThread.setDaemon(true); errorThread.start();
            try {
                JSONObject info = new JSONObject().put("name","codex-board-diagnostic").put("version","0.1.0");
                JSONObject msg = new JSONObject().put("id",1).put("method","initialize").put("params",new JSONObject().put("clientInfo",info));
                p.getOutputStream().write((msg.toString()+"\n").getBytes("UTF-8")); p.getOutputStream().flush();
                String line = read.get(15,TimeUnit.SECONDS);
                if (line == null) {
                    boolean exited = p.waitFor(2, TimeUnit.SECONDS);
                    log("App-server 초기화 실패: 응답 없이 종료됨" + (exited ? " (종료 코드 " + p.exitValue() + ")" : ""));
                }
                else { JSONObject response = new JSONObject(line); log("App-server 초기화: " + (response.has("result") ? "성공" : "실패: " + response.optString("error"))); }
            } finally {
                p.destroyForcibly();
                p.waitFor(3, TimeUnit.SECONDS);
                try { String detail = errors.get(3, TimeUnit.SECONDS); log(detail.isEmpty() ? "App-server 오류 출력: 없음" : "App-server 오류 상세:\n" + detail); }
                catch (Exception e) { log("App-server 오류 출력 수집 실패: " + e.getClass().getSimpleName()); }
            }
        } catch (Exception e) { log("App-server 초기화 실패: " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
        try {
            InetAddress[] hosts = InetAddress.getAllByName("auth.openai.com");
            log("Android DNS: 성공 (" + hosts.length + "개 주소)");
        } catch (Exception e) { log("Android DNS 실패: " + e.getClass().getSimpleName()); }
        try {
            HttpsURLConnectionCompat.check(); log("Android HTTPS: 연결 성공");
        } catch (Exception e) { log("Android HTTPS 실패: " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
        log("진단 완료. 결과를 복사해 전달해주세요. 이 결과만으로 로그인·파일 수정 성공을 보장할 수 없습니다.");
    }
    private ProcessBuilder prepareProcess(File binary, String... args) throws IOException {
        File homeDir = new File(getFilesDir(), "home");
        File codexDir = new File(homeDir, ".codex");
        File tmpDir = new File(getCacheDir(), "tmp");
        if ((!homeDir.isDirectory() && !homeDir.mkdirs()) || (!codexDir.isDirectory() && !codexDir.mkdirs()) || (!tmpDir.isDirectory() && !tmpDir.mkdirs())) throw new IOException("앱 전용 폴더 생성 실패");
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(binary.getAbsolutePath());
        java.util.Collections.addAll(command, args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(getFilesDir());
        builder.environment().put("HOME", homeDir.getAbsolutePath());
        builder.environment().put("CODEX_HOME", codexDir.getAbsolutePath());
        builder.environment().put("TMPDIR", tmpDir.getAbsolutePath());
        return builder;
    }
    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
        while ((n=input.read(b))!=-1) { if (out.size()<16384) out.write(b,0,Math.min(n,16384-out.size())); }
        return out.toString("UTF-8").trim();
    }
    private static final class HttpsURLConnectionCompat {
        static void check() throws Exception {
            HttpURLConnection c=(HttpURLConnection)new URL("https://auth.openai.com/").openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestMethod("HEAD");
            try { c.getResponseCode(); } finally { c.disconnect(); }
        }
    }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}

package dev.codexboard.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Color;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.view.inputmethod.EditorInfo;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/** Native PTY and original Codex CLI, not a chat-protocol emulation. */
public final class TerminalActivity extends Activity implements TerminalSessionClient, TerminalViewClient {
    private TerminalSession session;
    private TerminalView terminal;
    private LocalConnectProxy proxy;
    private TextView status;
    private EditText input;
    private LinearLayout entry;
    private boolean ctrl;
    private volatile boolean transferring;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,20,24));
        status=new TextView(this); status.setText("Codex CLI v0.11 — 시작 전"); status.setTextSize(16); root.addView(status);
        HorizontalScrollView top=new HorizontalScrollView(this); LinearLayout buttons=new LinearLayout(this); top.addView(buttons); root.addView(top);
        button(buttons,"로그인·기존 대화",()->{if(session!=null&&session.isRunning()){status.setText("먼저 실행 중인 Codex를 중지하세요.");return;}startActivity(new Intent(this,ChatActivity.class));});
        button(buttons,"진단",()->{if(session!=null&&session.isRunning()){status.setText("먼저 실행 중인 Codex를 중지하세요.");return;}startActivity(new Intent(this,MainActivity.class));});
        button(buttons,"보호 모드 시작",()->start(false));
        button(buttons,"제한 없는 모드",()->new AlertDialog.Builder(this).setTitle("접근 제한 없이 실행")
            .setMessage("이 모드에서는 Codex가 앱의 로그인 정보와 접근 가능한 파일을 읽거나 수정할 수 있고, 확인 없이 명령과 네트워크 작업을 실행할 수 있습니다. 작업 폴더만으로 접근이 제한되지는 않습니다.")
            .setPositiveButton("이해하고 시작",(d,w)->start(true)).setNegativeButton("취소",null).show());
        button(buttons,"가져오기",()->chooseFolder(101));
        button(buttons,"내보내기",()->chooseFolder(102));
        button(buttons,"키보드",this::showKeyboard);
        button(buttons,"입력창",this::toggleInput);
        button(buttons,"Ctrl",()->{ctrl=!ctrl;status.setText(ctrl?"Ctrl: 다음 키에 적용":"Ctrl 해제");});
        button(buttons,"Esc",()->write("\u001b")); button(buttons,"Tab",()->write("\t"));
        button(buttons,"⌫",()->write("\u007f")); button(buttons,"Enter",()->write("\r")); button(buttons,"Ctrl+C",()->write("\u0003"));
        button(buttons,"↑",()->write("\u001b[A")); button(buttons,"↓",()->write("\u001b[B"));
        button(buttons,"←",()->write("\u001b[D")); button(buttons,"→",()->write("\u001b[C"));
        button(buttons,"중지",()->{if(session!=null)session.finishIfRunning();});
        terminal=new TerminalView(this,null); terminal.setFocusable(true); terminal.setFocusableInTouchMode(true); terminal.setTerminalViewClient(this);
        terminal.setTextSize((int)(16*getResources().getDisplayMetrics().scaledDensity));
        root.addView(terminal,new LinearLayout.LayoutParams(-1,0,1));
        entry=new LinearLayout(this); entry.setVisibility(View.GONE);
        input=new EditText(this); input.setSingleLine(true); input.setTextSize(18); input.setHint("질문 또는 명령 입력"); input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        entry.addView(input,new LinearLayout.LayoutParams(0,-2,1)); Button submit=new Button(this); submit.setText("전송"); entry.addView(submit); root.addView(entry);
        submit.setOnClickListener(v->sendInput()); input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEND){sendInput();return true;}return false;});
        setContentView(root);
    }
    private void button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setFocusable(false);b.setOnClickListener(v->action.run());parent.addView(b);}
    private void write(String value){if(session!=null)session.write(value);}
    private void showKeyboard(){terminal.requestFocus();terminal.post(()->{InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);imm.restartInput(terminal);imm.showSoftInput(terminal,InputMethodManager.SHOW_IMPLICIT);});}
    private void toggleInput(){if(entry.getVisibility()==View.VISIBLE){entry.setVisibility(View.GONE);showKeyboard();}else{entry.setVisibility(View.VISIBLE);input.requestFocus();input.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT));}}
    private void sendInput(){if(session==null||!session.isRunning()){status.setText("먼저 Codex를 시작하세요.");return;}String value=input.getText().toString();write(value+"\r");input.setText("");}
    private void chooseFolder(int code){
        if(transferring||(session!=null&&session.isRunning())){status.setText("먼저 실행 중인 Codex와 파일 작업을 중지하세요.");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{startActivityForResult(intent,code);}catch(Exception e){status.setText("폴더 선택기를 열 수 없습니다: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data); if(result!=RESULT_OK||data==null||data.getData()==null||(request!=101&&request!=102))return;
        Uri tree=data.getData();transferring=true;status.setText("파일 작업 중…");
        Thread t=new Thread(()->{try{File workspace=new File(getFilesDir(),"workspace");String message=request==101?WorkspaceTransfer.importTree(this,tree,workspace):WorkspaceTransfer.exportTree(this,tree,workspace);runOnUiThread(()->status.setText(message));}catch(Exception e){runOnUiThread(()->status.setText("파일 작업 실패: "+e.getMessage()));}finally{transferring=false;}},"workspace-transfer");t.start();
    }
    private void start(boolean unrestricted) {
        if(transferring){status.setText("파일 작업이 끝날 때까지 기다려 주세요.");return;}
        if(session!=null&&session.isRunning()){status.setText("이미 실행 중입니다. 먼저 중지하세요.");return;}
        try {
            File home=new File(getFilesDir(),"home"), codex=new File(home,".codex"), tmp=new File(getCacheDir(),"tmp"), workspace=new File(getFilesDir(),"workspace");
            for(File dir:new File[]{home,codex,tmp,workspace})if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("작업 폴더 생성 실패");
            File cert=ChatActivity.writeSystemCertificates(this);
            File tools=RuntimeTools.prepare(this);
            if(proxy!=null)proxy.close(); proxy=new LocalConnectProxy(true);
            String binary=new File(getApplicationInfo().nativeLibraryDir,"libcodex.so").getAbsolutePath();
            ArrayList<String> env=new ArrayList<>();
            for(Map.Entry<String,String> e:System.getenv().entrySet())env.add(e.getKey()+"="+e.getValue());
            env.removeIf(s->s.startsWith("HOME=")||s.startsWith("CODEX_HOME=")||s.startsWith("TMPDIR=")||s.startsWith("TERM=")||s.startsWith("SHELL=")||s.startsWith("PATH=")||s.startsWith("HTTPS_PROXY=")||s.startsWith("https_proxy=")||s.startsWith("SSL_CERT_FILE=")||s.startsWith("CODEX_CA_CERTIFICATE="));
            env.add("HOME="+home); env.add("CODEX_HOME="+codex); env.add("TMPDIR="+tmp); env.add("TERM=xterm-256color"); env.add("SHELL="+new File(tools,"bash")); env.add("PATH="+RuntimeTools.path(tools,System.getenv("PATH")));
            env.add("HTTPS_PROXY="+proxy.url()); env.add("https_proxy="+proxy.url()); env.add("SSL_CERT_FILE="+cert); env.add("CODEX_CA_CERTIFICATE="+cert);
            String[] args=unrestricted?new String[]{"codex","--no-daemon","-c","allow_login_shell=false","--dangerously-bypass-approvals-and-sandbox"}:new String[]{"codex","--no-daemon","-c","allow_login_shell=false","--sandbox","workspace-write","--ask-for-approval","never"};
            session=new TerminalSession(binary,workspace.getAbsolutePath(),args,env.toArray(new String[0]),2000,this);
            terminal.attachSession(session); terminal.requestFocus();
            status.setText(unrestricted?"Codex 실행 중 — 접근 제한 없음":"Codex 실행 중 — 보호 모드 (기기 호환성 시험 필요)");
        } catch(Exception e){status.setText("실행 실패: "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    @Override public void onTextChanged(TerminalSession s){terminal.onScreenUpdated();}
    @Override public void onTitleChanged(TerminalSession s){}
    @Override public void onSessionFinished(TerminalSession s){status.setText("Codex 종료됨");}
    @Override public void onCopyTextToClipboard(TerminalSession s,String text){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Codex",text));}
    @Override public void onPasteTextFromClipboard(TerminalSession s){ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(s!=null&&c.hasPrimaryClip())s.write(c.getPrimaryClip().getItemAt(0).coerceToText(this).toString());}
    @Override public void onBell(TerminalSession s){}
    @Override public void onColorsChanged(TerminalSession s){terminal.invalidate();}
    @Override public void onTerminalCursorStateChange(boolean state){}
    @Override public void setTerminalShellPid(TerminalSession s,int pid){}
    @Override public Integer getTerminalCursorStyle(){return 0;}
    @Override public float onScale(float scale){return scale;}
    @Override public void onSingleTapUp(MotionEvent e){showKeyboard();}
    @Override public boolean shouldBackButtonBeMappedToEscape(){return false;}
    @Override public boolean shouldEnforceCharBasedInput(){return true;}
    @Override public boolean shouldUseCtrlSpaceWorkaround(){return false;}
    @Override public boolean isTerminalViewSelected(){return terminal!=null&&terminal.hasFocus();}
    @Override public void copyModeChanged(boolean b){}
    @Override public boolean onKeyDown(int keyCode,KeyEvent e,TerminalSession s){return false;}
    @Override public boolean onKeyUp(int keyCode,KeyEvent e){return false;}
    @Override public boolean onLongPress(MotionEvent e){return false;}
    @Override public boolean readControlKey(){boolean value=ctrl;ctrl=false;return value;}
    @Override public boolean readAltKey(){return false;}
    @Override public boolean readShiftKey(){return false;}
    @Override public boolean readFnKey(){return false;}
    @Override public boolean onCodePoint(int codePoint,boolean ctrlDown,TerminalSession s){return false;}
    @Override public void onEmulatorSet(){}
    @Override public void logError(String tag,String message){Log.e(tag,message);}
    @Override public void logWarn(String tag,String message){Log.w(tag,message);}
    @Override public void logInfo(String tag,String message){}
    @Override public void logDebug(String tag,String message){}
    @Override public void logVerbose(String tag,String message){}
    @Override public void logStackTraceWithMessage(String tag,String message,Exception e){Log.e(tag,message,e);}
    @Override public void logStackTrace(String tag,Exception e){Log.e(tag,"terminal error",e);}
    @Override protected void onDestroy(){if(session!=null)session.finishIfRunning();if(proxy!=null)try{proxy.close();}catch(IOException ignored){}super.onDestroy();}
}

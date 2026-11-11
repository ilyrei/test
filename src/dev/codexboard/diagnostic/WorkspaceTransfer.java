package dev.codexboard.diagnostic;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Streaming SAF import/export; never overwrites an existing imported file. */
final class WorkspaceTransfer {
    static String importTree(Context context, Uri tree, File workspace) throws IOException {
        if(!workspace.isDirectory()&&!workspace.mkdirs())throw new IOException("작업 폴더 생성 실패");
        int[] count={0,0}; importDir(context,tree,DocumentsContract.getTreeDocumentId(tree),workspace,workspace.getCanonicalPath(),count,0);
        return "가져옴: "+count[0]+"개, 기존 파일 건너뜀: "+count[1]+"개";
    }
    private static void importDir(Context c, Uri tree, String id, File target, String root, int[] count, int depth) throws IOException {
        if(depth>64)throw new IOException("폴더 깊이 제한 초과");
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor cursor=c.getContentResolver().query(children,columns,null,null,null)) {
            if(cursor==null)throw new IOException("폴더를 읽을 수 없습니다");
            while(cursor.moveToNext()) {
                String childId=cursor.getString(0), name=cursor.getString(1), mime=cursor.getString(2);
                if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\"))throw new IOException("지원되지 않는 파일 이름");
                File out=new File(target,name); String canonical=out.getCanonicalPath(); if(!canonical.startsWith(root+File.separator))throw new IOException("잘못된 파일 경로");
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if(out.exists()&&!out.isDirectory()) {count[1]++;continue;}
                    if(!out.isDirectory()&&!out.mkdirs())throw new IOException("폴더 생성 실패: "+name);
                    importDir(c,tree,childId,out,root,count,depth+1);
                } else {
                    if(out.exists()){count[1]++;continue;}
                    Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,childId);
                    File temp=File.createTempFile(".codex-import-",".tmp",target);
                    try(InputStream in=c.getContentResolver().openInputStream(doc);OutputStream output=new FileOutputStream(temp)) {
                        if(in==null)throw new IOException("파일 열기 실패: "+name); copy(in,output);
                    } catch(IOException e){temp.delete();throw e;}
                    if(out.exists()||!temp.renameTo(out)){temp.delete();throw new IOException("파일 저장 실패: "+name);} count[0]++;
                }
            }
        }
    }
    static String exportTree(Context c, Uri tree, File workspace) throws IOException {
        if(!workspace.isDirectory())throw new IOException("내보낼 작업 폴더가 없습니다");
        Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        String name="Codex-결과-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date());
        Uri destination=DocumentsContract.createDocument(c.getContentResolver(),root,DocumentsContract.Document.MIME_TYPE_DIR,name);
        if(destination==null)throw new IOException("결과 폴더 생성 실패");
        int[] count={0}; exportDir(c,destination,workspace,count,0); return "새 결과 폴더에 내보냄: "+count[0]+"개 ("+name+")";
    }
    private static void exportDir(Context c,Uri dest,File source,int[] count,int depth)throws IOException{
        if(depth>64)throw new IOException("폴더 깊이 제한 초과");
        File[] children=source.listFiles(); if(children==null)throw new IOException("작업 폴더를 읽을 수 없습니다");
        for(File f:children){ if(Files.isSymbolicLink(f.toPath()))continue;
            if(f.isDirectory()){Uri sub=DocumentsContract.createDocument(c.getContentResolver(),dest,DocumentsContract.Document.MIME_TYPE_DIR,f.getName());if(sub==null)throw new IOException("폴더 내보내기 실패");exportDir(c,sub,f,count,depth+1);}
            else if(f.isFile()){Uri doc=DocumentsContract.createDocument(c.getContentResolver(),dest,"application/octet-stream",f.getName());if(doc==null)throw new IOException("파일 생성 실패: "+f.getName());try(InputStream in=new FileInputStream(f);OutputStream out=c.getContentResolver().openOutputStream(doc,"w")){if(out==null)throw new IOException("파일 열기 실패: "+f.getName());copy(in,out);}count[0]++;}
        }
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
}

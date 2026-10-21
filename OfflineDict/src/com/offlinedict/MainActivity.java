package com.offlinedict;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** مترجم قاموسي بدون إنترنت: إنجليزي <-> عربي. واجهة مبنية بالكود لتخفيف الموارد. */
public class MainActivity extends Activity {

    private static final String DB_NAME = "dict.db";

    private SQLiteDatabase db;
    private int dir = 0; // 0: EN->AR , 1: AR->EN
    private Button dirBtn;
    private EditText input;
    private TextView result;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        File f = getDatabasePath(DB_NAME);
        if (f.exists() && f.length() > 0) {
            openDb();
        } else {
            result.setText("جاري تجهيز القاموس لأول مرة...");
            new CopyTask().execute();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        dirBtn = new Button(this);
        updateDirText();
        dirBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dir = 1 - dir;
                updateDirText();
                search();
            }
        });
        root.addView(dirBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        input = new EditText(this);
        input.setHint("اكتب كلمة أو جملة...");
        input.setTextSize(20);
        input.setGravity(Gravity.TOP);
        input.setMinLines(2);
        input.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) { search(); }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        result = new TextView(this);
        result.setTextSize(20);
        result.setPadding(6, 12, 6, 12);
        sv.addView(result);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void updateDirText() {
        dirBtn.setText(dir == 0 ? "[EN → AR]  اضغط للتبديل"
                                : "[AR → EN]  اضغط للتبديل");
    }

    private void openDb() {
        try {
            db = SQLiteDatabase.openDatabase(getDatabasePath(DB_NAME).getPath(),
                    null, SQLiteDatabase.OPEN_READONLY);
            result.setText("");
        } catch (Exception e) {
            result.setText("تعذر فتح القاموس: " + e.getMessage());
        }
    }

    /** تطبيع النص: أحرف صغيرة + إزالة التشكيل وتوحيد الألف والياء والهاء. */
    static String norm(String s) {
        s = s.toLowerCase(Locale.ENGLISH);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x064B && c <= 0x0652) || c == 0x0640) continue;
            if (c == 0x0623 || c == 0x0625 || c == 0x0622) c = 0x0627;
            else if (c == 0x0629) c = 0x0647;
            else if (c == 0x0649) c = 0x064A;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private String keyCol() { return dir == 0 ? "en_k" : "ar_k"; }
    private String valCol() { return dir == 0 ? "ar" : "en"; }

    private String lookupExact(String key) {
        Cursor c = db.rawQuery("SELECT " + valCol() + " FROM d WHERE " + keyCol()
                + "=? LIMIT 1", new String[]{key});
        try {
            if (c.moveToFirst()) return c.getString(0);
        } finally {
            c.close();
        }
        return null;
    }

    private void search() {
        if (db == null) return;
        String text = input.getText().toString().trim();
        if (text.length() == 0) { result.setText(""); return; }

        String[] raw = text.split("\\s+");
        String[] tok = new String[raw.length];
        int n = 0;
        for (int i = 0; i < raw.length; i++) {
            String t = raw[i].replaceAll("^[\\p{Punct}،؟؛]+|[\\p{Punct}،؟؛]+$", "");
            if (t.length() > 0) tok[n++] = t;
        }
        if (n == 0) { result.setText(""); return; }

        StringBuilder out = new StringBuilder();

        if (n > 1) {
            // ترجمة جملة: أطول عبارة متطابقة أولًا (حتى 4 كلمات)
            int i = 0;
            while (i < n) {
                boolean found = false;
                for (int len = Math.min(4, n - i); len >= 1; len--) {
                    StringBuilder ph = new StringBuilder();
                    for (int j = 0; j < len; j++) {
                        if (j > 0) ph.append(' ');
                        ph.append(tok[i + j]);
                    }
                    String r = lookupExact(norm(ph.toString()));
                    if (r != null) {
                        int p = r.indexOf('؛');
                        if (p < 0) p = r.indexOf(';');
                        out.append(p > 0 ? r.substring(0, p) : r).append(' ');
                        i += len;
                        found = true;
                        break;
                    }
                }
                if (!found) { out.append('[').append(tok[i]).append("] "); i++; }
            }
            out.append("\n\n(ترجمة تقريبية؛ الكلمات بين [ ] غير موجودة في القاموس)");
        } else {
            // كلمة واحدة: تطابق تام أولًا ثم اقتراحات بالبداية
            String key = norm(tok[0]);
            Cursor c = db.rawQuery("SELECT en, ar FROM d WHERE " + keyCol()
                    + " LIKE ? ORDER BY (" + keyCol() + "=?) DESC, length(" + keyCol()
                    + ") LIMIT 30", new String[]{key + "%", key});
            try {
                while (c.moveToNext()) {
                    String a = c.getString(0), b = c.getString(1);
                    if (dir == 0) out.append(a).append("  →  ").append(b).append("\n\n");
                    else out.append(b).append("  →  ").append(a).append("\n\n");
                }
            } finally {
                c.close();
            }
            if (out.length() == 0) out.append("لا توجد نتيجة");
        }
        result.setText(out.toString());
    }

    private class CopyTask extends AsyncTask<Void, Void, Boolean> {
        protected Boolean doInBackground(Void... p) {
            try {
                File f = getDatabasePath(DB_NAME);
                f.getParentFile().mkdirs();
                InputStream in = getAssets().open(DB_NAME);
                OutputStream o = new FileOutputStream(f);
                byte[] buf = new byte[32768];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                o.flush(); o.close(); in.close();
                return true;
            } catch (Exception e) {
                getDatabasePath(DB_NAME).delete();
                return false;
            }
        }
        protected void onPostExecute(Boolean ok) {
            if (ok) openDb(); else result.setText("فشل تجهيز القاموس");
        }
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }
}

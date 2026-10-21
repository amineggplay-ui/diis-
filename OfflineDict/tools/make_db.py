#!/usr/bin/env python3
"""يبني assets/dict.db من كل ملفات data/*.tsv  (سطر = english<TAB>arabic)."""
import glob, os, re, sqlite3

def norm(s):
    s = s.lower()
    s = re.sub('[\u064B-\u0652\u0640]', '', s)
    s = re.sub('[أإآ]', 'ا', s).replace('ة', 'ه').replace('ى', 'ي')
    return s.strip()

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.makedirs(os.path.join(root, 'assets'), exist_ok=True)
out = os.path.join(root, 'assets', 'dict.db')
if os.path.exists(out): os.remove(out)
db = sqlite3.connect(out)
db.execute('CREATE TABLE d (en TEXT, ar TEXT, en_k TEXT, ar_k TEXT)')
rows = []
for f in sorted(glob.glob(os.path.join(root, 'data', '*.tsv'))):
    for line in open(f, encoding='utf-8'):
        line = line.strip()
        if not line or line.startswith('#') or '\t' not in line: continue
        en, ar = line.split('\t', 1)
        rows.append((en, ar, norm(en), norm(ar)))
        # فهرسة كل مرادف عربي ليعمل البحث العكسي
        for alt in re.split('[؛;]', ar):
            k = norm(alt)
            if k and k != norm(ar):
                rows.append((en, ar, norm(en), k))
db.executemany('INSERT INTO d VALUES (?,?,?,?)', rows)
db.execute('CREATE INDEX i_en ON d(en_k)')
db.execute('CREATE INDEX i_ar ON d(ar_k)')
db.commit(); db.execute('VACUUM'); db.close()
print('entries:', len(rows), ' size KB:', os.path.getsize(out)//1024)

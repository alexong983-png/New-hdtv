import re, sys, glob, os, zipfile

# stage 1: hook HomeActivity.onCreate in decoded smali
def hook(decoded):
    files = glob.glob(os.path.join(decoded, "smali*", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.smali"))
    if not files:
        sys.exit("HomeActivity.smali not found")
    f = files[0]
    s = open(f, encoding="utf-8").read()
    m = re.search(r"\.method[^\n]* onCreate\(Landroid/os/Bundle;\)V.*?invoke-super \{p0, p1\}, [^\n]*->onCreate\(Landroid/os/Bundle;\)V\n", s, re.S)
    if not m:
        sys.exit("onCreate super call not found")
    call = "    invoke-static {p0}, Lcom/patch/gate/PasswordGate;->show(Landroid/app/Activity;)V\n"
    s = s[:m.end()] + call + s[m.end():]
    open(f, "w", encoding="utf-8").write(s)
    print("hooked", f)

# stage 2: put new dex files into a copy of the original apk (keeps resources untouched)
def repack(orig, rebuilt, gate_dex, out):
    zr = zipfile.ZipFile(rebuilt)
    new_dex = {n: zr.read(n) for n in zr.namelist() if re.fullmatch(r"classes\d*\.dex", n)}
    n = len(new_dex) + 1
    new_dex["classes%d.dex" % n] = open(gate_dex, "rb").read()
    zo = zipfile.ZipFile(orig)
    with zipfile.ZipFile(out, "w") as zw:
        for info in zo.infolist():
            name = info.filename
            if name.startswith("META-INF/") and re.search(r"\.(SF|RSA|DSA|EC)$|MANIFEST\.MF$", name):
                continue
            if name in new_dex:
                continue
            zw.writestr(info, zo.read(name), compress_type=info.compress_type)
        for name, data in new_dex.items():
            zw.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
    print("repacked ->", out, sorted(new_dex))

if sys.argv[1] == "hook":
    hook(sys.argv[2])
else:
    repack(*sys.argv[2:6])

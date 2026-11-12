#!/usr/bin/env python3
"""يبني APK لتطبيق MovieFlix بدون Gradle: aapt2 + ecj + d8 + zipalign + apksigner."""
import os, re, shutil, subprocess, sys, pathlib

W = pathlib.Path("/scratch/work")
SDK = W / "sdk"
JH = pathlib.Path("/scratch/.local/lib/python3.12/site-packages/jdk4py/java-runtime")
BT = SDK / "android-14"
PLATFORM = SDK / "android-34" / "android.jar"
PROJ = W / "android"
LIBS = W / "aar" / "jars"
LIB_RES = W / "aar" / "res"
LIB_PKGS = "androidx.core:androidx.media3.exoplayer:androidx.annotation.experimental"
STATIC = W / "movieflix-app" / "static"
BUILD = PROJ / "build"
JAVA = str(JH / "bin" / "java")
ECJ = str(SDK / "ecj.jar")


def run(cmd, cwd=None, label=""):
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if p.returncode != 0:
        print(f"❌ {label or cmd[0]}\nSTDOUT:\n{p.stdout[-3000:]}\nSTDERR:\n{p.stderr[-3000:]}")
        sys.exit(1)
    return p


def prepare_assets():
    assets = PROJ / "assets"
    if assets.exists():
        shutil.rmtree(assets)
    assets.mkdir(parents=True)
    for f in ("styles.css", "i18n.js", "app.js"):
        shutil.copy(STATIC / f, assets / f)

    html = (STATIC / "index.html").read_text(encoding="utf-8")
    html = html.replace('href="/static/styles.css"', 'href="styles.css"')
    html = html.replace('src="/static/i18n.js"', 'src="i18n.js"')
    html = html.replace('src="/static/app.js"', 'src="app.js"')
    (assets / "index.html").write_text(html, encoding="utf-8")

    js = (STATIC / "app.js").read_text(encoding="utf-8")

    # 1) كشف الجسر الأصلي
    js = js.replace(
        "  const $ = (sel, root = document) => root.querySelector(sel);",
        "  const NATIVE = (typeof window.AndroidBridge !== 'undefined') ? window.AndroidBridge : null;\n\n"
        "  const $ = (sel, root = document) => root.querySelector(sel);",
    )

    # 2) البوسترات تُحمَّل مباشرة داخل التطبيق (لا خادم وسيط)
    js = js.replace(
        """  const posterUrl = (url) => {
    if (!url) return '';
    if (/^https?:\\/\\//i.test(url)) return `/api/img?u=${encodeURIComponent(url)}`;""",
        """  const posterUrl = (url) => {
    if (!url) return '';
    if (NATIVE) return url;
    if (/^https?:\\/\\//i.test(url)) return `/api/img?u=${encodeURIComponent(url)}`;""",
    )

    # 3) طبقة API: جسر أصلي داخل التطبيق، أو HTTP في المتصفح
    js = js.replace(
        """  async function api(path, { method = 'GET', body } = {}) {
    busy(true);""",
        """  async function api(path, { method = 'GET', body } = {}) {
    if (NATIVE) {
      const [rawPath, qs] = path.split('?');
      const action = rawPath.replace('/api/', '');
      const params = Object.assign({}, Object.fromEntries(new URLSearchParams(qs || '')), body || {});
      busy(true);
      try {
        const res = NATIVE.call(action, JSON.stringify(params));
        const data = JSON.parse(res || '{}');
        if (data.ok === false) { const e = new Error(data.error || 'error'); e.data = data; throw e; }
        return data;
      } finally {
        busy(false);
      }
    }
    busy(true);""",
    )

    # 4) زر الرجوع في أندرويد
    js = js.replace(
        "  document.addEventListener('DOMContentLoaded', boot);",
        """  window.__back = () => {
    if ($('#overlay').classList.contains('open')) { closeSheet(); return true; }
    if ($('#sidebar').classList.contains('open')) { closeDrawer(); return true; }
    if (state.view !== 'home') { setView('home'); return true; }
    return false;
  };

  document.addEventListener('DOMContentLoaded', boot);""",
    )

    (assets / "app.js").write_text(js, encoding="utf-8")

    for name, needle in [
        ("index", 'href="styles.css"'),
        ("app.js", "const NATIVE"),
        ("app.js", "window.__back"),
        ("app.js", "if (NATIVE) return url;"),
    ]:
        target = assets / (name if name != "index" else "index.html")
        if needle not in target.read_text(encoding="utf-8"):
            print(f"⚠️ لم يُطبَّق التعديل: {name} :: {needle}")
    print("✓ assets جاهزة")


def build():
    if BUILD.exists():
        shutil.rmtree(BUILD)
    for d in ("res", "classes", "dex", "gen"):
        (BUILD / d).mkdir(parents=True)

    # 1) الموارد (التطبيق + مكتبات Media3/androidx)
    run([str(BT / "aapt2"), "compile", "--dir", "res", "-o", str(BUILD / "res.zip")],
        cwd=PROJ, label="aapt2 compile")
    lib_zips = []
    if LIB_RES.is_dir():
        for d in sorted(LIB_RES.iterdir()):
            if not d.is_dir():
                continue
            z = BUILD / f"res_{d.name}.zip"
            run([str(BT / "aapt2"), "compile", "--dir", str(d), "-o", str(z)],
                label=f"aapt2 compile {d.name}")
            lib_zips.append(z)
        print(f"✓ موارد المكتبات: {len(lib_zips)}")

    # 2) ربط الموارد + manifest + assets
    link_cmd = [str(BT / "aapt2"), "link",
                "-o", str(BUILD / "base.apk"),
                "-I", str(PLATFORM),
                "--manifest", "AndroidManifest.xml",
                "-R", str(BUILD / "res.zip")]
    for z in lib_zips:
        link_cmd += ["-R", str(z)]
    link_cmd += ["--extra-packages", LIB_PKGS]
    run(link_cmd + [
         "--java", str(BUILD / "gen"),
         "--java", str(BUILD / "gen"),
         "--min-sdk-version", "23",
         "--target-sdk-version", "34",
         "--auto-add-overlay"],
        cwd=PROJ, label="aapt2 link")
    print("✓ الموارد")

    # 3) ترجمة Java
    srcs = sorted(str(p) for p in (PROJ / "src").rglob("*.java"))
    srcs += sorted(str(p) for p in (BUILD / "gen").rglob("*.java"))
    lib_cp = ":".join(str(j) for j in sorted(LIBS.glob("*.jar"))) if LIBS.is_dir() else ""
    cp = str(PLATFORM) + ((":" + lib_cp) if lib_cp else "")
    run([JAVA, "-jar", ECJ, "-nowarn", "-proc:none", "-source", "8", "-target", "8",
         "-bootclasspath", str(PLATFORM), "-classpath", cp,
         "-d", str(BUILD / "classes")] + srcs, label="ecj")
    print("✓ Java")

    # 4) dex
    classes = sorted(str(p) for p in (BUILD / "classes").rglob("*.class"))
    lib_jars = sorted(str(j) for j in LIBS.glob("*.jar")) if LIBS.is_dir() else []
    run([JAVA, "-Xmx900m", "-cp", str(BT / "lib" / "d8.jar"), "com.android.tools.r8.D8",
         "--min-api", "23", "--lib", str(PLATFORM),
         "--output", str(BUILD / "dex")] + classes + lib_jars, label="d8")
    print("✓ dex")

    # 5) إضافة classes.dex إلى الحزمة
    dex_files = sorted(p.name for p in (BUILD / "dex").glob("*.dex"))
    print("✓ dex:", ", ".join(f"{n}" for n in dex_files))
    run(["zip", "-q", "-9", str(BUILD / "base.apk")] + dex_files,
        cwd=BUILD / "dex", label="zip dex")

    # 6) zipalign
    run([str(BT / "zipalign"), "-f", "-p", "4",
         str(BUILD / "base.apk"), str(BUILD / "aligned.apk")], label="zipalign")

    # 7) التوقيع
    ks = PROJ / "movieflix-release.jks"
    if not ks.exists():
        run([str(JH / "bin" / "keytool"), "-genkeypair", "-keystore", str(ks),
             "-alias", "movieflix", "-storepass", "movieflixstudio", "-keypass", "movieflixstudio",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
             "-dname", "CN=MovieFlix, OU=Mobile, O=HiTV, L=Cairo, C=EG"], label="keytool")

    out = BUILD / "HiTV-Studio.apk"
    run([JAVA, "-jar", str(BT / "lib" / "apksigner.jar"), "sign",
         "--ks", str(ks), "--ks-pass", "pass:movieflixstudio", "--key-pass", "pass:movieflixstudio",
         "--min-sdk-version", "23",
         "--out", str(out), str(BUILD / "aligned.apk")], label="apksigner")

    v = run([JAVA, "-jar", str(BT / "lib" / "apksigner.jar"), "verify", "--print-certs", str(out)],
            label="verify")
    print(v.stdout.strip()[:600])
    print(f"\n✅ APK: {out}  ({out.stat().st_size/1024/1024:.2f} MB)")
    return out


if __name__ == "__main__":
    build()

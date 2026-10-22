#!/usr/bin/env python3
"""Parse all real Java source units without resolving unavailable Android SDK types."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
java_files = sorted((ROOT / "android/app/src/main/java").rglob("*.java"))
checker = r'''
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
class SyntaxCheck {
  public static void main(String[] paths) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) throw new IllegalStateException("JDK compiler module required");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
      JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
          List.of("-proc:none", "-source", "17", "-target", "17"), null,
          files.getJavaFileObjectsFromStrings(List.of(paths)));
      task.parse();
      boolean error = false;
      for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
        if (d.getKind() == Diagnostic.Kind.ERROR) { System.err.println(d); error = true; }
      }
      if (error) throw new IllegalStateException("Java syntax error");
      System.out.println("PASS: parsed " + paths.length + " Java files (syntax only; no Android type checking)");
    }
  }
}
'''
with tempfile.TemporaryDirectory(prefix="hamrah-parse-") as directory:
    path = Path(directory) / "SyntaxCheck.java"
    path.write_text(checker)
    subprocess.run(["java", str(path), *map(str, java_files)], check=True)

main = ROOT / "android/app/src/main"
for xml in main.rglob("*.xml"):
    ET.parse(xml)
manifest = ET.parse(main / "AndroidManifest.xml").getroot()
attr = "{http://schemas.android.com/apk/res/android}name"
package = "ir.hamrah.agent"
for element in manifest.findall("./application/*"):
    name = element.attrib.get(attr, "")
    if name.startswith("."):
        source = main / "java" / (package + name).replace(".", "/")
        assert source.with_suffix(".java").exists(), f"Missing component {name}"
print("PASS: Android XML parses and declared app components exist")

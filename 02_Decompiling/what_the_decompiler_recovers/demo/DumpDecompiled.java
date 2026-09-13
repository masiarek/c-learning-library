// Ghidra headless post-script: decompile the named functions and write the C
// to a file, one function after another.
//
//   analyzeHeadless <project dir> <project name> -import <binary> \
//       -scriptPath <this folder> \
//       -postScript DumpDecompiled.java <out.txt> [function name ...] \
//       -deleteProject
//
// With no function names, every function that is not an import or a thunk.
// A Mach-O symbol carries a leading underscore that an ELF symbol does not, so
// names are compared with that underscore removed.
//@category Learning

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;

public class DumpDecompiled extends GhidraScript {

    static String plain(String name) {
        return name.startsWith("_") ? name.substring(1) : name;
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        Path out = Path.of(args[0]);
        List<String> wanted = Arrays.asList(args).subList(1, args.length);

        DecompInterface ifc = new DecompInterface();
        ifc.openProgram(currentProgram);
        StringBuilder sb = new StringBuilder();
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (f.isThunk() || f.isExternal()) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.contains(plain(f.getName()))) {
                continue;
            }
            DecompileResults res = ifc.decompileFunction(f, 30, monitor);
            sb.append("// ").append(f.getName()).append('\n');
            sb.append(res.getDecompiledFunction().getC().strip()).append("\n\n");
        }
        ifc.dispose();
        Files.writeString(out, sb.toString());
        println("wrote " + out);
    }
}

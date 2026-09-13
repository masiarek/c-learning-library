// Ghidra headless post-script: after auto-analysis, count the functions Ghidra
// found in the program's own code, and check which of the named ones it found.
// The names come as name=address pairs learned from the build that still had a
// symbol table, because the one analyzed here has none. Appends a line to a file.
//
//   -postScript FunctionsFound.java <out.txt> <label> <name=address,name=address,...>
//
// Only functions inside __text (Mach-O) or .text (ELF) are counted, so the
// stubs a dynamic linker needs are not in the number. (Not ListFunctions: Ghidra
// ships a script of that name, and a headless run may pick up either.)
//@category Learning

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.mem.MemoryBlock;

public class FunctionsFound extends GhidraScript {

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        Path out = Path.of(args[0]);
        String label = args[1];

        MemoryBlock text = null;
        for (MemoryBlock block : currentProgram.getMemory().getBlocks()) {
            if (block.isExecute() && (block.getName().equals("__text") || block.getName().equals(".text"))) {
                text = block;
                break;
            }
        }
        if (text == null) {
            throw new IllegalStateException("no __text or .text block");
        }

        int count = 0;
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (!f.isThunk() && !f.isExternal() && text.contains(f.getEntryPoint())) {
                count++;
            }
        }

        List<String> missing = new ArrayList<>();
        int named = 0;
        for (String pair : args[2].split(",")) {
            String[] nameAndAddress = pair.split("=");
            Address at = currentProgram.getAddressFactory().getAddress(nameAndAddress[1]);
            named++;
            if (currentProgram.getFunctionManager().getFunctionAt(at) == null) {
                missing.add(nameAndAddress[0]);
            }
        }
        String verdict = missing.isEmpty() ? "all " + named + " named functions found"
                : "missing: " + String.join(" ", missing);
        String line = String.format("%-44s %2d in %-6s %s%n", label, count, text.getName(), verdict);
        Files.writeString(out, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        println(line.strip());
    }
}

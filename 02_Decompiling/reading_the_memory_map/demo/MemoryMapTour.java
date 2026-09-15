// Ghidra headless post-script: print a program's memory map the way the Memory
// Map window lists it, then change it the ways the window's checkboxes and
// buttons do, and print what each change did.
//
//   analyzeHeadless <project dir> <project name> -import <binary> \
//       -scriptPath <this folder> \
//       -postScript MemoryMapTour.java <out.txt> \
//       -deleteProject
//
// It expects layout.c's names: a function over_limit that reads a global limit,
// and a global scratch. Mach-O and 32-bit Windows symbols carry a leading
// underscore that an ELF symbol does not, so names are compared without it.
//@category Learning

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.OverlayAddressSpace;
import ghidra.program.model.listing.Function;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.mem.MemoryBlockSourceInfo;
import ghidra.program.model.symbol.Symbol;

public class MemoryMapTour extends GhidraScript {

    private final StringBuilder out = new StringBuilder();

    static String plain(String name) {
        return name.startsWith("_") ? name.substring(1) : name;
    }

    static String mark(boolean on) {
        return on ? "x" : "-";
    }

    private void line(String s) {
        out.append(s).append('\n');
    }

    private void heading(String s) {
        if (out.length() > 0) {
            out.append('\n');
        }
        line("== " + s);
    }

    /** One row per block, in the Memory Map window's column order. */
    private void table() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Name", "Start", "End", "Length", "R", "W", "X", "Volatile",
            "Artificial", "Overlayed", "Type", "Init", "Byte Source" });
        for (MemoryBlock b : currentProgram.getMemory().getBlocks()) {
            String overlayed = "";
            if (b.getStart().getAddressSpace() instanceof OverlayAddressSpace space) {
                overlayed = space.getOverlayedSpace().getName();
            }
            List<String> sources = new ArrayList<>();
            for (MemoryBlockSourceInfo info : b.getSourceInfos()) {
                sources.add(info.getDescription());
            }
            rows.add(new String[] { b.getName(), b.getStart().toString(), b.getEnd().toString(),
                "0x" + Long.toHexString(b.getSize()), mark(b.isRead()), mark(b.isWrite()),
                mark(b.isExecute()), mark(b.isVolatile()), mark(b.isArtificial()), overlayed,
                b.getType().toString(), mark(b.isInitialized()), String.join(" + ", sources) });
        }
        int[] width = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                width[i] = Math.max(width[i], row[i].length());
            }
        }
        for (String[] row : rows) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                sb.append(String.format("%-" + width[i] + "s  ", row[i]));
            }
            line(sb.toString().stripTrailing());
        }
    }

    private Address global(String name) {
        for (Symbol s : currentProgram.getSymbolTable().getAllSymbols(false)) {
            if (plain(s.getName()).equals(name) && s.isGlobal()) {
                return s.getAddress();
            }
        }
        throw new IllegalStateException("no symbol named " + name);
    }

    /**
     * The function called name. Where analysis left only a label -- a function
     * nothing calls, because the one call was inlined -- make the function there,
     * as pressing F on the label in the Listing does, and say so.
     */
    private Function function(String name) {
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (plain(f.getName()).equals(name)) {
                return f;
            }
        }
        Address at = global(name);
        disassemble(at);
        Function f = createFunction(at, null);
        if (f == null) {
            throw new IllegalStateException("could not make a function at " + at);
        }
        line("(analysis left only a label at " + at + "; the script made the function "
            + f.getName() + " there)");
        return f;
    }

    /**
     * The decompiler's options as the Code Browser would set them for this program --
     * a DecompInterface given none leaves Respect read-only flags off.
     */
    private DecompileOptions options() {
        DecompileOptions options = new DecompileOptions();
        options.grabFromProgram(currentProgram);
        return options;
    }

    /** A fresh decompiler each time, so nothing it read before a change is reused. */
    private String decompile(Function f) {
        DecompInterface ifc = new DecompInterface();
        ifc.setOptions(options());
        ifc.openProgram(currentProgram);
        String c = ifc.decompileFunction(f, 30, monitor).getDecompiledFunction().getC();
        ifc.dispose();
        return c.strip();
    }

    private String byteAt(Address a) {
        try {
            return String.format("%02x", currentProgram.getMemory().getByte(a) & 0xff);
        }
        catch (MemoryAccessException e) {
            return "??";
        }
    }

    @Override
    public void run() throws Exception {
        Path file = Path.of(getScriptArgs()[0]);
        Memory memory = currentProgram.getMemory();

        heading("The rows, image base " + currentProgram.getImageBase());
        table();

        Address scratch = global("scratch");
        MemoryBlock zeros = memory.getBlock(scratch);
        heading("Where scratch landed");
        line("scratch is at " + scratch + " in " + zeros.getName() + ", initialized: "
            + mark(zeros.isInitialized()) + ", first byte: " + byteAt(scratch));

        // The W and Volatile checkboxes, on the block that holds limit.
        Address limit = global("limit");
        MemoryBlock data = memory.getBlock(limit);
        heading("over_limit, decompiled with " + data.getName() + " as loaded (limit at " + limit
            + ", Respect read-only flags: " + (options().isRespectReadOnly() ? "on" : "off") + ")");
        Function overLimit = function("over_limit");
        line(decompile(overLimit));

        data.setWrite(false);
        heading("over_limit, after unchecking W on " + data.getName());
        line(decompile(overLimit));

        data.setVolatile(true);
        heading("over_limit, W still unchecked, Volatile checked");
        line(decompile(overLimit));
        data.setVolatile(false);
        data.setWrite(true);

        // Add, with Overlay checked: a second set of 0x100 bytes at the addresses
        // over_limit starts at. Read-only, like OV1 in the help's picture.
        Address at = overLimit.getEntryPoint();
        MemoryBlock ov = memory.createInitializedBlock("OV1", at, 0x100, (byte) 0, monitor, true);
        ov.setPermissions(true, false, false);
        heading("After Add: OV1, an overlay on the 0x100 bytes from over_limit at " + at);
        table();

        Address base = currentProgram.getImageBase().add(0x10000000L);
        currentProgram.setImageBase(base, true);
        heading("After Set Image Base " + base);
        table();
        line("over_limit is now at " + overLimit.getEntryPoint() + "; OV1 still starts at "
            + ov.getStart());

        Files.writeString(file, out.toString());
        println("wrote " + file);
    }
}

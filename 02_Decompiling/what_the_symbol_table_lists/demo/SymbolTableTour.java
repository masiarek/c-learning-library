// Ghidra headless post-script: print a program's Symbol Table the way the
// Symbol Table window builds it, run the queries its Symbol Table Filter dialog
// builds -- through Ghidra's own filter class, NewSymbolFilter -- and then
// rename, delete, re-create and pin symbols to see what each does to a row.
//
//   -postScript SymbolTableTour.java <out.txt>
//
// NewSymbolFilter's setters are package-private, because the dialog is their
// only caller, so this script reaches them by reflection. Every accept or
// reject below is Ghidra's decision, not a copy of it; the rows are gathered
// the way SymbolTableModel gathers them, each column is computed the way
// AbstractSymbolTableModel computes it, and a delete issues the commands the
// window's Delete action issues.
//@category Learning

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import ghidra.app.cmd.function.DeleteFunctionCmd;
import ghidra.app.cmd.label.DeleteLabelCmd;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.plugin.core.symtable.NewSymbolFilter;
import ghidra.app.script.GhidraScript;
import ghidra.framework.cmd.CompoundCmd;
import ghidra.program.model.address.*;
import ghidra.program.model.data.StringDataInstance;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.pcode.HighFunctionDBUtil;
import ghidra.program.model.symbol.*;

public class SymbolTableTour extends GhidraScript {

    // The dialog's four groups of checkboxes, by the method that names them.
    private static final String[][] GROUPS = {
        { "getSourceFilterNames", "Symbol Source" },
        { "getLabelTypeFilterNames", "Label Symbols" },
        { "getNonLabelTypeFilterNames", "Non-label Symbols" },
        { "getAdvancedFilterNames", "Advanced Filters" },
    };

    private final StringBuilder out = new StringBuilder();
    private final Set<String> known = new HashSet<>();
    private String[] sources;
    private String[] labels;

    private void line(String fmt, Object... args) {
        out.append(String.format(fmt, args).stripTrailing()).append('\n');
    }

    private static String[] arr(String... names) {
        return names;
    }

    private static Object invoke(NewSymbolFilter f, String method, Class<?>[] types,
            Object... args) throws Exception {
        Method m = NewSymbolFilter.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(f, args);
    }

    private static String[] group(NewSymbolFilter f, String getter) throws Exception {
        return (String[]) invoke(f, getter, new Class<?>[0]);
    }

    private static boolean flag(NewSymbolFilter f, String method, String box) throws Exception {
        return (Boolean) invoke(f, method, new Class<?>[] { String.class }, box);
    }

    private static void set(NewSymbolFilter f, String box, boolean on) throws Exception {
        invoke(f, "setFilter", new Class<?>[] { String.class, boolean.class }, box, on);
    }

    /** A filter with exactly these boxes checked, as if the dialog had been clicked. */
    private NewSymbolFilter checked(String[] src, String[] types, String[] advanced)
            throws Exception {
        NewSymbolFilter f = new NewSymbolFilter();
        for (String[] g : GROUPS) {
            for (String box : group(f, g[0])) {
                set(f, box, false);
            }
        }
        for (String[] part : List.of(src, types, advanced)) {
            for (String box : part) {
                if (!known.contains(box)) {
                    throw new IllegalArgumentException("no such checkbox: " + box);
                }
                set(f, box, true);
            }
        }
        return f;
    }

    /** The rows the window shows under a filter, gathered as SymbolTableModel does. */
    private List<Symbol> rows(NewSymbolFilter f) {
        Program p = currentProgram;
        SymbolTable st = p.getSymbolTable();
        List<Symbol> rows = new ArrayList<>();
        for (Symbol s : st.getDefinedSymbols()) {
            if (f.accepts(s, p)) {
                rows.add(s);
            }
        }
        // Default labels are not stored: one exists wherever a reference lands and
        // no stored symbol does. The window finds them by visiting every address a
        // reference lands on, and only when the filter could accept one.
        if (f.acceptsDefaultLabelSymbols()) {
            AddressIterator it = p.getReferenceManager()
                .getReferenceDestinationIterator(p.getAddressFactory().getAddressSet(), true);
            while (it.hasNext()) {
                Symbol s = st.getPrimarySymbol(it.next());
                if (s != null && s.isDynamic() && f.accepts(s, p)) {
                    rows.add(s);
                }
            }
        }
        rows.sort(Comparator.comparing((Symbol s) -> s.getAddress()).thenComparing(s -> s.getName()));
        return rows;
    }

    /** The Offcut Ref Count column: addresses inside this symbol's code unit that a reference lands on. */
    private int offcut(Symbol s) {
        Address a = s.getAddress();
        if (!a.isMemoryAddress()) {
            return 0;
        }
        CodeUnit cu = currentProgram.getListing().getCodeUnitContaining(a);
        if (cu == null) {
            return 0;
        }
        AddressSet set = new AddressSet(cu.getMinAddress(), cu.getMaxAddress());
        set.deleteRange(a, a);
        int n = 0;
        AddressIterator it =
            currentProgram.getReferenceManager().getReferenceDestinationIterator(set, true);
        while (it.hasNext()) {
            it.next();
            n++;
        }
        return n;
    }

    private static String location(Symbol s) {
        if (s.getObject() instanceof Variable v) {
            return v.getVariableStorage().toString();
        }
        return s.getAddress().toString();
    }

    private void header() {
        line("  %-22s %-16s %-18s %-28s %-12s %4s %6s", "Name", "Location", "Type", "Namespace",
            "Source", "Refs", "Offcut");
    }

    private void row(Symbol s) {
        String ns = s.isDynamic() ? "" : s.getParentNamespace().getName(true);
        SymbolType type = s.getSymbolType();
        boolean labelLike = type == SymbolType.LABEL || type == SymbolType.FUNCTION;
        String notes = (labelLike && !s.isPrimary() ? "  non-primary" : "") +
            (s.isPinned() ? "  pinned" : "");
        line("  %-22s %-16s %-18s %-28s %-12s %4d %6d%s", s.getName(), location(s),
            SymbolUtilities.getSymbolTypeDisplayName(s), ns, s.getSource().getDisplayString(),
            s.getReferenceCount(), offcut(s), notes);
    }

    private String summary(String[] src, String[] types, String[] advanced) {
        String s = Arrays.equals(src, sources) ? "every source" : String.join(", ", src);
        String t = Arrays.equals(types, labels) ? "the three label types" : String.join(", ", types);
        String a = advanced.length == 0 ? "no advanced filter" : String.join(", ", advanced);
        return s + " | " + t + " | " + a;
    }

    private void query(String title, String[] src, String[] types, String[] advanced)
            throws Exception {
        List<Symbol> r = rows(checked(src, types, advanced));
        line("");
        line("== %s: %d row%s ==", title, r.size(), r.size() == 1 ? "" : "s");
        line("   checked: %s", summary(src, types, advanced));
        if (!r.isEmpty()) {
            header();
            r.forEach(this::row);
        }
    }

    private void dialog(NewSymbolFilter f) throws Exception {
        for (String[] g : GROUPS) {
            line("  %s", g[1]);
            for (String box : group(f, g[0])) {
                line("    [%s] %s%s", flag(f, "isActive", box) ? "x" : " ", box,
                    flag(f, "isEnabled", box) ? "" : "   (grayed out)");
            }
        }
    }

    private void at(String title, Address a) {
        line("");
        line("== %s ==", title);
        header();
        SymbolTable st = currentProgram.getSymbolTable();
        Symbol[] here = st.getSymbols(a);
        if (here.length == 0) {
            Symbol dynamic = st.getPrimarySymbol(a);
            if (dynamic != null) {
                row(dynamic);
            }
            else {
                line("  (no symbol at %s)", a);
            }
        }
        for (Symbol s : here) {
            row(s);
        }
    }

    /** Every reference to a symbol: where it comes from, and what is there. */
    private void refs(Symbol s) {
        Reference[] refs = s.getReferences(null);
        Arrays.sort(refs, Comparator.comparing((Reference r) -> r.getFromAddress()));
        line("");
        line("== References to %s: %d ==", s.getName(), refs.length);
        for (Reference r : refs) {
            Address from = r.getFromAddress();
            MemoryBlock b = currentProgram.getMemory().getBlock(from);
            CodeUnit cu = currentProgram.getListing().getCodeUnitContaining(from);
            line("  from %-12s %-20s %-10s %s", from, r.getReferenceType(),
                b == null ? "" : b.getName(), cu == null ? "" : cu.toString());
        }
    }

    /** The window's Delete action, command for command -- AbstractSymbolTableModel.delete. */
    private String deleteAsTheWindowDoes(Symbol symbol) {
        if (symbol.isDynamic()) {
            return "skipped: the action passes over a dynamic symbol";
        }
        CompoundCmd<Program> cmd = new CompoundCmd<>("Delete symbol(s)");
        String label = symbol.getName();
        Address address = symbol.getAddress();
        if (symbol.getSymbolType() == SymbolType.FUNCTION) {
            Function function = (Function) symbol.getObject();
            cmd.add(new DeleteFunctionCmd(address, function.isThunk()));
            if (symbol.getSource() != SourceType.DEFAULT) {
                cmd.add(new DeleteLabelCmd(address, label, symbol.getParentNamespace()));
            }
        }
        else {
            cmd.add(new DeleteLabelCmd(address, label, symbol.getParentNamespace()));
        }
        return cmd.applyTo(currentProgram) ? "applied" : "failed: " + cmd.getStatusMsg();
    }

    private String functionAt(Address a) {
        Function f = getFunctionAt(a);
        return f == null ? "none" : f.getName();
    }

    private static String plain(String name) {
        return name.startsWith("_") ? name.substring(1) : name;
    }

    private Symbol named(String name) {
        for (Symbol s : currentProgram.getSymbolTable().getDefinedSymbols()) {
            if (!s.isExternal() && plain(s.getName()).equals(name)) {
                return s;
            }
        }
        return null;
    }

    /** The function that uses a string -- how a stripped program still gives one away. */
    private Function userOf(String text) {
        for (Data d : currentProgram.getListing().getDefinedData(true)) {
            if (d.hasStringValue() &&
                text.equals(StringDataInstance.getStringDataInstance(d).getStringValue())) {
                for (Reference r : getReferencesTo(d.getAddress())) {
                    Function fn = getFunctionContaining(r.getFromAddress());
                    if (fn != null) {
                        return fn;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void run() throws Exception {
        Path path = Path.of(getScriptArgs()[0]);

        NewSymbolFilter opened = new NewSymbolFilter();
        for (String[] g : GROUPS) {
            known.addAll(Arrays.asList(group(opened, g[0])));
        }
        sources = group(opened, "getSourceFilterNames");
        labels = group(opened, "getLabelTypeFilterNames");

        line("== %s, %s ==", currentProgram.getName(), currentProgram.getExecutableFormat());
        line("");
        line("== The Symbol Table Filter dialog, as NewSymbolFilter opens it ==");
        dialog(opened);

        List<Symbol> shown = rows(opened);
        line("");
        line("== The table under those defaults: %d rows ==", shown.size());
        header();
        shown.forEach(this::row);

        List<Symbol> more = rows(checked(sources, labels, arr()));
        more.removeAll(shown);
        line("");
        line("== Check Default (Labels) as well: %d more rows ==", more.size());
        header();
        more.forEach(this::row);

        Symbol orphan = named("orphan");
        if (orphan == null) {
            // Stripped: find orphan by the one string only it uses, and name it.
            query("Functions nobody has named", arr("Default (Functions)"),
                arr("Function Labels"), arr());
            query("Unreferenced functions", sources, arr("Function Labels"), arr("Unreferenced"));
            Function fn = userOf("nobody calls this: %d\n");
            refs(fn.getSymbol());
            fn.getSymbol().setName("orphan", SourceType.USER_DEFINED);
            at("Rename the function that uses \"nobody calls this\" to orphan", fn.getEntryPoint());
            query("What the user has named", arr("User Defined"), labels, arr());
            Files.writeString(path, out.toString());
            return;
        }

        query("Parameters, before any is committed", sources, arr("Parameters"), arr());

        // Parameters are symbols only once they are stored. Store _twice's, as the
        // decompiler's Commit Params/Return action does -- CommitParamsAction: its
        // source is Analysis unless the signature was already the user's.
        Function twice = getFunctionAt(named("twice").getAddress());
        DecompInterface ifc = new DecompInterface();
        ifc.openProgram(currentProgram);
        DecompileResults res = ifc.decompileFunction(twice, 30, monitor);
        SourceType commitSource = twice.getSignatureSource() == SourceType.USER_DEFINED
                ? SourceType.USER_DEFINED
                : SourceType.ANALYSIS;
        HighFunctionDBUtil.commitParamsToDatabase(res.getHighFunction(), true,
            HighFunctionDBUtil.ReturnCommitOption.COMMIT, commitSource);
        ifc.dispose();
        StringBuilder stored = new StringBuilder();
        for (Parameter p : twice.getParameters()) {
            stored.append(' ').append(p.getName()).append(" in ").append(p.getVariableStorage());
        }
        line("");
        line("== Commit %s's parameters from the decompiler, as Commit Params/Return does:%s ==",
            twice.getName(), stored);

        query("Functions nobody has named", arr("Default (Functions)"), arr("Function Labels"), arr());
        query("Library functions the program calls", sources, arr("Function Labels"), arr("Externals"));
        query("External library names", sources, arr("External Library"), arr());
        query("Entry points", sources, labels, arr("Entry Points"));
        query("Unreferenced functions", sources, arr("Function Labels"), arr("Unreferenced"));
        refs(orphan);
        query("Unreferenced, with Parameters checked too", sources,
            arr("Function Labels", "Parameters"), arr("Unreferenced"));
        query("Parameters in a register", sources, arr("Parameters"), arr("Register Variables"));
        query("The help's Example 3: functions and stack parameters", sources,
            arr("Function Labels", "Parameters"), arr("Stack Variables"));
        query("Not In Memory", sources, labels, arr("Not In Memory"));
        query("Not In Memory, with Function Labels unchecked", sources,
            arr("Instruction Labels", "Data Labels"), arr("Not In Memory"));
        query("Locals", sources, labels, arr("Locals"));
        query("Offcut Labels", sources, labels, arr("Offcut Labels"));
        int neither = rows(checked(sources, labels, arr())).size();
        int both = rows(checked(sources, labels, arr("Primary Labels", "Non-Primary Labels"))).size();
        line("");
        line("== The help's Example 4: %d rows with neither Primary Labels nor Non-Primary Labels, %d with both ==",
            neither, both);

        SymbolTable st = currentProgram.getSymbolTable();
        Address orphanAt = orphan.getAddress();
        at("Rename " + orphan.getName() + " to orphan", orphanAt);
        orphan.setName("orphan", SourceType.USER_DEFINED);
        row(orphan);
        line("  Delete: %s", deleteAsTheWindowDoes(orphan));
        at("After Delete", orphanAt);
        line("  function at %s: %s", orphanAt, functionAt(orphanAt));
        refs(st.getPrimarySymbol(orphanAt));

        Symbol counter = named("counter");
        Address counterAt = counter.getAddress();
        at(counter.getName() + ", before", counterAt);
        line("  Delete: %s", deleteAsTheWindowDoes(counter));
        at("After Delete", counterAt);
        Symbol dynamic = st.getPrimarySymbol(counterAt);
        line("  Delete %s: %s", dynamic.getName(), deleteAsTheWindowDoes(dynamic));
        refs(dynamic);

        Address twiceAt = twice.getEntryPoint();
        createLabel(twiceAt, "also_twice", false, SourceType.USER_DEFINED);
        at("Add a label also_twice at " + twice.getName() + ", Primary unchecked", twiceAt);
        query("Non-Primary Labels", sources, labels, arr("Non-Primary Labels"));

        Symbol helper = named("helper");
        Address helperAt = helper.getAddress();
        removeFunction(getFunctionAt(helperAt));
        at("Clear the function at " + helper.getName(), helperAt);
        query("Subroutines", sources, arr("Instruction Labels"), arr("Subroutines"));
        createFunction(helperAt, null);
        at("Create Function at " + helper.getName() + " again", helperAt);

        Symbol greeting = named("greeting");
        Symbol tail = named("tail");
        String g = greeting.getName();
        String t = tail.getName();
        greeting.setPinned(true);
        Address base = currentProgram.getImageBase();
        line("");
        line("== Pin %s, leave %s, and move the image base from %s ==", g, t, base);
        line("  before: %s %s  %s %s", g, greeting.getAddress(), t, tail.getAddress());
        currentProgram.setImageBase(base.add(0x10000000L), true);
        line("  after:  %s %s  %s %s   image base %s", g, greeting.getAddress(), t,
            tail.getAddress(), currentProgram.getImageBase());
        line("  in memory: %s %b  %s %b", g,
            currentProgram.getMemory().contains(greeting.getAddress()), t,
            currentProgram.getMemory().contains(tail.getAddress()));

        Files.writeString(path, out.toString());
    }
}

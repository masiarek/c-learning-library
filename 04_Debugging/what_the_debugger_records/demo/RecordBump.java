// Ghidra script for the Debugger tool: launch the current program under lldb
// through Ghidra, stop at bump three times, read `counter` from the live target
// at each stop, then rewind the trace and read the same bytes from the
// recording. Writes what it saw to ~/record_bump.txt.
//
// Run it from the Debugger tool's Script Manager with the program open. It
// uses the "lldb" launcher exactly as the Debugger > Launch menu would, with
// the run command changed to stop at the entry point first.
//@category Learning

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import ghidra.app.script.GhidraScript;
import ghidra.debug.api.tracermi.TraceRmiLaunchOffer;
import ghidra.debug.api.tracermi.TraceRmiLaunchOffer.LaunchResult;
import ghidra.debug.flatapi.FlatDebuggerRmiAPI;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.model.symbol.Symbol;
import ghidra.trace.model.Trace;
import ghidra.trace.model.memory.TraceMemoryRegion;
import ghidra.trace.model.modules.TraceModule;
import ghidra.trace.model.modules.TraceStaticMapping;
import ghidra.trace.model.time.TraceSnapshot;

public class RecordBump extends GhidraScript implements FlatDebuggerRmiAPI {

    private final StringBuilder log = new StringBuilder();

    private void say(String line) {
        log.append(line).append('\n');
        println(line);
    }

    /** The static address of a global, whether the symbol is `name` or `_name`. */
    private Address staticAddress(Program prog, String name) {
        for (String candidate : List.of(name, "_" + name)) {
            List<Symbol> found = prog.getSymbolTable().getGlobalSymbols(candidate);
            if (!found.isEmpty()) {
                return found.get(0).getAddress();
            }
        }
        throw new IllegalStateException("no symbol " + name);
    }

    private static int littleEndianInt(byte[] b) {
        return (b[0] & 0xff) | (b[1] & 0xff) << 8 | (b[2] & 0xff) << 16 | (b[3] & 0xff) << 24;
    }

    @Override
    protected void run() throws Exception {
        Program prog = getCurrentProgram();
        Path out = Path.of(System.getProperty("user.home"), "record_bump.txt");

        Address bumpStatic = staticAddress(prog, "bump");
        Address counterStatic = staticAddress(prog, "counter");
        say("static program: " + prog.getName() + "  bump at " + bumpStatic + "  counter at " + counterStatic);

        TraceRmiLaunchOffer offer = getLaunchOffers(prog).stream()
            .filter(o -> "lldb".equals(o.getTitle()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no lldb launcher offered"));
        say("launcher: " + offer.getTitle() + "  parameters: " + offer.getParameters().keySet());

        Map<String, Object> args = new HashMap<>();
        args.put("OPT_START_CMD", "process launch --stop-at-entry");
        LaunchResult result = launch(offer, args, monitor);
        if (result.exception() != null) {
            throw new IllegalStateException("launch failed", result.exception());
        }
        Trace trace = result.trace();
        waitForBreak(trace, 30, TimeUnit.SECONDS);
        say("launched: trace \"" + trace.getName() + "\", stopped at entry, snapshot " + getCurrentSnap());

        // Static address -> address inside the live process, through the module mapping.
        Address bumpDynamic = translateStaticToDynamic(bumpStatic);
        Address counterDynamic = translateStaticToDynamic(counterStatic);
        say("mapped: bump -> " + bumpDynamic + "  counter -> " + counterDynamic);

        breakpointSetSoftwareExecute(staticLocation(prog, bumpStatic), "bump");
        say("breakpoint: set on bump in the static program; the trace and lldb received it");

        long[] stops = new long[3];
        for (int i = 0; i < 3; i++) {
            resume(trace);
            waitForBreak(trace, 30, TimeUnit.SECONDS);
            stops[i] = getCurrentSnap();
            int counter = littleEndianInt(readMemory(trace, stops[i], counterDynamic, 4, monitor));
            String pc = readRegister("RIP").getUnsignedValue().toString(16);
            String fromLldb = executeCapture(trace, "p counter").strip();
            say("stop " + (i + 1) + ": snapshot " + stops[i] + "  RIP 0x" + pc
                + "  counter read from the target = " + counter
                + "  lldb says: " + fromLldb);
        }

        // Rewind: the same bytes, read from the recording rather than the process.
        activateSnap(stops[0]);
        int recorded = littleEndianInt(readMemory(trace, stops[0], counterDynamic, 4, monitor));
        say("rewound to snapshot " + stops[0] + ": counter in the trace = " + recorded
            + " (the process itself is still stopped at snapshot " + stops[2] + ")");

        say("snapshots:");
        for (TraceSnapshot snap : trace.getTimeManager().getAllSnapshots()) {
            say("  " + snap.getKey() + "  " + snap.getDescription());
        }
        say("regions at snapshot " + stops[2] + ":");
        for (TraceMemoryRegion r : trace.getMemoryManager().getRegionsAtSnap(stops[2])) {
            say("  " + r.getRange(stops[2]) + "  " + r.getName(stops[2]));
        }
        say("modules:");
        for (TraceModule m : trace.getModuleManager().getAllModules()) {
            say("  " + m.getName(stops[2]));
        }
        say("static mappings:");
        for (TraceStaticMapping m : trace.getStaticMappingManager().getAllEntries()) {
            say("  " + m.getTraceAddressRange() + " -> " + m.getStaticProgramURL() + " at " + m.getStaticAddress());
        }

        activateSnap(stops[2]);
        resume(trace);
        Thread.sleep(2000);
        kill(trace);
        Files.writeString(out, log.toString());
        println("wrote " + out);
    }
}

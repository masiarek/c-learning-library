// Ghidra headless post-script: tell Ghidra what the source file knew and the
// binary does not -- that there is a struct point { int x; int y; } and that
// norm2's parameter points at one -- then decompile norm2 again.
//
//   -postScript ApplyPointType.java <out.txt>
//
// What this does by API, the Decompiler window does by hand: Data Type Manager
// > New > Structure, then right-click the parameter > Retype Variable.
//@category Learning

import java.nio.file.Files;
import java.nio.file.Path;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.data.PointerDataType;
import ghidra.program.model.data.StructureDataType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Function.FunctionUpdateType;
import ghidra.program.model.listing.ParameterImpl;
import ghidra.program.model.symbol.SourceType;

public class ApplyPointType extends GhidraScript {

    static String plain(String name) {
        return name.startsWith("_") ? name.substring(1) : name;
    }

    @Override
    public void run() throws Exception {
        Path out = Path.of(getScriptArgs()[0]);

        // The struct, as the source declared it: two ints, x at 0 and y at 4.
        StructureDataType point = new StructureDataType("point", 0);
        point.add(IntegerDataType.dataType, "x", null);
        point.add(IntegerDataType.dataType, "y", null);
        DataType saved = currentProgram.getDataTypeManager().addDataType(point, null);

        Function norm2 = null;
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (plain(f.getName()).equals("norm2")) {
                norm2 = f;
                break;
            }
        }
        if (norm2 == null) {
            throw new IllegalStateException("no function named norm2 in this program");
        }

        // One parameter, named p, of type point *, under the platform's default calling
        // convention. The return type is left alone.
        ParameterImpl p = new ParameterImpl("p", new PointerDataType(saved), currentProgram);
        norm2.updateFunction(Function.DEFAULT_CALLING_CONVENTION_STRING, null,
            FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS, true, SourceType.USER_DEFINED, p);

        DecompInterface ifc = new DecompInterface();
        ifc.openProgram(currentProgram);
        String c = ifc.decompileFunction(norm2, 30, monitor).getDecompiledFunction().getC();
        ifc.dispose();
        Files.writeString(out, "// " + norm2.getName() + ", told about struct point\n" + c.strip() + "\n");
        println("wrote " + out);
    }
}

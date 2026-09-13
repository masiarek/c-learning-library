// Ghidra headless pre-script: switch off every analyzer that finds function
// starts by searching for byte patterns, before auto-analysis runs. What is
// left is what the file itself says -- the entry point, the calls that can be
// followed from it, and on a Mac the LC_FUNCTION_STARTS table if the file has one.
//
//   analyzeHeadless ... -preScript NoPatternSearch.java -postScript FunctionsFound.java <out.txt>
//
// The four names are the analyzers' names in Edit > Options for Program >
// Analyzers; all four are the BytePatterns module's FunctionStartAnalyzer and
// its subclasses, reading the same pattern files.
//@category Learning

import ghidra.app.script.GhidraScript;

public class NoPatternSearch extends GhidraScript {

    @Override
    public void run() throws Exception {
        for (String analyzer : new String[] {
                "Function Start Search",
                "Function Start Pre Search",
                "Function Start Search After Code",
                "Function Start Search After Data" }) {
            setAnalysisOption(currentProgram, analyzer, "false");
        }
        println("pattern-based function start search switched off");
    }
}

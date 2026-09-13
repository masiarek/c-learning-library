// Ghidra headless post-script: what the Function Bit Patterns Explorer window
// shows, produced without the window and written to a text file.
//
// It gathers the bytes and instructions around every function start and return
// with the explorer's default window sizes and writes them as the XML the
// explorer's "Read XML Files" button reads. Then it takes the steps a reader
// takes in the window, in order: the First Instructions tree with the
// percentage each node shows on hover; the First Bytes table under a length
// filter; Merge Selected Rows; Mine Sequential Patterns; Export to a pattern
// file; and Evaluate Selected Patterns against the program, with the
// explorer's own names for each kind of hit.
//
//   -postScript ExploreFunctionStarts.java <out.txt> <xml directory>
//
// Names are printed as Ghidra has them: a Mach-O symbol carries a leading
// underscore that an ELF symbol does not.
//@category Learning

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ghidra.app.script.GhidraScript;
import ghidra.bitpatterns.gui.ClosedPatternRowObject;
import ghidra.bitpatterns.gui.PatternInfoRowObject;
import ghidra.bitpatterns.info.ByteSequenceLengthFilter;
import ghidra.bitpatterns.info.ByteSequenceRowObject;
import ghidra.bitpatterns.info.DataGatheringParams;
import ghidra.bitpatterns.info.FileBitPatternInfo;
import ghidra.bitpatterns.info.FunctionBitPatternInfo;
import ghidra.bitpatterns.info.InstructionSequence;
import ghidra.bitpatterns.info.InstructionSequenceTreePathFilter;
import ghidra.bitpatterns.info.PatternType;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.block.BasicBlockModel;
import ghidra.program.model.block.CodeBlock;
import ghidra.program.model.block.CodeBlockReferenceIterator;
import ghidra.program.model.listing.CodeUnit;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.symbol.RefType;
import ghidra.util.bytesearch.DittedBitSequence;
import ghidra.util.task.TaskMonitor;

public class ExploreFunctionStarts extends GhidraScript {

    // The explorer's defaults -- the values in DumpFunctionPatternInfoScript.properties too.
    static final int FIRST_BYTES = 16, FIRST_INSTRUCTIONS = 4;
    static final int PRE_BYTES = 12, PRE_INSTRUCTIONS = 3;
    static final int RETURN_BYTES = 12, RETURN_INSTRUCTIONS = 3;

    // What the dialogs ask, answered here.
    static final int POST_PREFIX = 4;        // First Bytes length filter: keep the first 4 bytes
    static final int PRE_SUFFIX = 2;         // Return Bytes length filter: keep the last 2
    static final double MIN_SUPPORT = 0.5;   // mining: a pattern must occur in half the sequences
    static final int MIN_FIXED_BITS = 16;    // mining: and fix at least this many bits
    static final int EXPORT_TOTAL_BITS = 32, EXPORT_POST_BITS = 16;   // export: as x86win_patterns.xml
    static final int MAX_HITS_LISTED = 12;   // evaluate: list this many hits, then only count
    static final int MAX_MINED_LISTED = 8;   // mining: list this many of the patterns found
    static final int PATH_DEPTH = 3;         // analyze: the tree's most-travelled path, this deep

    private final StringBuilder out = new StringBuilder();

    private void line(String text) {
        out.append(text).append('\n');
    }

    private void line(String fmt, Object... args) {
        line(String.format(fmt, args));
    }

    /** "554889e5" as "55 48 89 e5". */
    static String spaced(String hex) {
        if (hex == null) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 2 <= hex.length(); i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(hex, i, i + 2);
        }
        return sb.toString();
    }

    /** The tree's labels for a sequence, "PUSH:1 MOV:3 ...". Pre and return sequences are
     *  stored from the boundary outwards; reverse them to read in memory order. */
    static String labels(InstructionSequence seq, boolean reverse) {
        List<String> parts = new ArrayList<>();
        String[] insts = seq.getInstructions();
        Integer[] sizes = seq.getSizes();
        for (int i = 0; i < insts.length && insts[i] != null; i++) {
            parts.add(insts[i] + ":" + sizes[i]);
        }
        if (parts.isEmpty()) {
            return "(none)";
        }
        if (reverse) {
            Collections.reverse(parts);
        }
        return String.join(" ", parts);
    }

    // ---- the tree an Instruction Sequence tab shows: one node per mnemonic:length ----

    static class Node {
        int count;
        final Map<String, Node> children = new HashMap<>();
    }

    static Node tree(List<InstructionSequence> seqs) {
        Node root = new Node();
        for (InstructionSequence seq : seqs) {
            String[] insts = seq.getInstructions();
            Integer[] sizes = seq.getSizes();
            if (insts == null || insts[0] == null) {
                continue;
            }
            root.count++;
            Node node = root;
            for (int i = 0; i < insts.length && insts[i] != null; i++) {
                node = node.children.computeIfAbsent(insts[i] + ":" + sizes[i], k -> new Node());
                node.count++;
            }
        }
        return root;
    }

    /** Children by count, largest first, as the window sorts them; the percentage is the
     *  share of all sequences in the tree that pass through the node. */
    private void printTree(Node node, int depth, int total) {
        List<Map.Entry<String, Node>> kids = new ArrayList<>(node.children.entrySet());
        kids.sort((a, b) -> a.getValue().count != b.getValue().count
                ? Integer.compare(b.getValue().count, a.getValue().count)
                : a.getKey().compareTo(b.getKey()));
        for (Map.Entry<String, Node> kid : kids) {
            line("%s%-9s %2d of %d  %3.0f%%", "  ".repeat(depth + 1), kid.getKey(),
                kid.getValue().count, total, 100.0 * kid.getValue().count / total);
            printTree(kid.getValue(), depth + 1, total);
        }
    }

    // ---- Evaluate Selected Patterns: search, and name each hit as the explorer does ----

    private String classify(Address start) throws Exception {
        Listing listing = currentProgram.getListing();
        CodeUnit cu = listing.getCodeUnitContaining(start);
        if (cu instanceof Data && ((Data) cu).isDefined()) {
            return "FP_DATA";
        }
        if (listing.getFunctionAt(start) != null) {
            return "TRUE_POSITIVE";
        }
        if (listing.getInstructionContaining(start) == null) {
            return "POSSIBLE_START_UNDEFINED";
        }
        Instruction instruction = listing.getInstructionAt(start);
        if (instruction == null) {
            return "FP_MISALIGNED";
        }
        // A defined instruction: at the start of a basic block that only jumps reach, it may
        // still be a function start; inside a block, or reached by fall-through, it is not.
        BasicBlockModel model = new BasicBlockModel(currentProgram);
        boolean blockStart = model.isBlockStart(instruction);
        if (blockStart) {
            CodeBlock block = model.getCodeBlockAt(start, TaskMonitor.DUMMY);
            CodeBlockReferenceIterator sources = model.getSources(block, TaskMonitor.DUMMY);
            while (sources != null && sources.hasNext()) {
                if (!sources.next().getFlowType().equals(RefType.UNCONDITIONAL_JUMP)) {
                    blockStart = false;
                }
            }
        }
        return blockStart ? "POSSIBLE_START_CODE" : "FP_WRONG_FLOW";
    }

    /** Search every initialized block for the pattern; the function start a hit implies is
     *  {@code startOffset} bytes in (the length of the PRE part, when there is one). */
    private void evaluate(DittedBitSequence pattern, int startOffset) throws Exception {
        Memory memory = currentProgram.getMemory();
        byte[] value = pattern.getValueBytes();
        byte[] mask = pattern.getMaskBytes();
        Map<String, Integer> totals = new LinkedHashMap<>();
        int listed = 0;
        for (MemoryBlock block : memory.getBlocks()) {
            if (!block.isInitialized()) {
                continue;
            }
            Address at = block.getStart();
            while (at != null) {
                Address hit = memory.findBytes(at, block.getEnd(), value, mask, true, monitor);
                if (hit == null) {
                    break;
                }
                Address start = hit.add(startOffset);
                String kind = classify(start);
                totals.merge(kind, 1, Integer::sum);
                if (listed < MAX_HITS_LISTED) {
                    Function f = currentProgram.getFunctionManager().getFunctionAt(start);
                    line("  %s  %-10s %-24s %s", start, block.getName(), kind,
                        f == null ? "" : f.getName());
                    listed++;
                }
                at = hit.equals(block.getEnd()) ? null : hit.add(1);
            }
        }
        if (listed < totals.values().stream().mapToInt(Integer::intValue).sum()) {
            line("  ...");
        }
        line("  totals: %s", totals);
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        Path outFile = Path.of(args[0]);
        File xmlDir = new File(args[1]);

        // 1. Gather -- the same loop as Ghidra's DumpFunctionPatternInfoScript.java.
        DataGatheringParams params = new DataGatheringParams();
        params.setNumFirstBytes(FIRST_BYTES);
        params.setNumFirstInstructions(FIRST_INSTRUCTIONS);
        params.setNumPreBytes(PRE_BYTES);
        params.setNumPreInstructions(PRE_INSTRUCTIONS);
        params.setNumReturnBytes(RETURN_BYTES);
        params.setNumReturnInstructions(RETURN_INSTRUCTIONS);
        params.setContextRegisters(new ArrayList<>());

        FileBitPatternInfo file = new FileBitPatternInfo();
        file.setLanguageID(currentProgram.getLanguageID().getIdAsString());
        file.setGhidraURL(currentProgram.getDomainFile().getPathname());
        file.setNumFirstBytes(FIRST_BYTES);
        file.setNumFirstInstructions(FIRST_INSTRUCTIONS);
        file.setNumPreBytes(PRE_BYTES);
        file.setNumPreInstructions(PRE_INSTRUCTIONS);
        file.setNumReturnBytes(RETURN_BYTES);
        file.setNumReturnInstructions(RETURN_INSTRUCTIONS);

        AddressSetView initialized = currentProgram.getMemory().getLoadedAndInitializedAddressSet();
        Listing listing = currentProgram.getListing();
        List<FunctionBitPatternInfo> infos = file.getFuncBitPatternInfo();
        List<Function> functions = new ArrayList<>();
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (f.isThunk() || f.isExternal() || !initialized.contains(f.getEntryPoint()) ||
                listing.getInstructionAt(f.getEntryPoint()) == null) {
                continue;
            }
            FunctionBitPatternInfo info = new FunctionBitPatternInfo(currentProgram, f, params);
            if (info.getFirstBytes() == null) {
                continue;
            }
            infos.add(info);
            functions.add(f);
        }
        File xml = new File(xmlDir, currentProgram.getName() + "_funcInfo.xml");
        file.toXmlFile(xml);

        line("== %s: %d functions gathered  (first %d bytes / %d instructions, pre %d / %d, return %d / %d)",
            currentProgram.getName(), infos.size(), FIRST_BYTES, FIRST_INSTRUCTIONS, PRE_BYTES,
            PRE_INSTRUCTIONS, RETURN_BYTES, RETURN_INSTRUCTIONS);
        for (int i = 0; i < infos.size(); i++) {
            FunctionBitPatternInfo info = infos.get(i);
            line("%s @ %s", functions.get(i).getName(), functions.get(i).getEntryPoint());
            line("  first   %-47s %s", spaced(info.getFirstBytes()), labels(info.getFirstInst(), false));
            line("  pre     %-47s %s", spaced(info.getPreBytes()), labels(info.getPreInst(), true));
            for (int r = 0; r < info.getReturnBytes().size(); r++) {
                line("  return  %-47s %s", spaced(info.getReturnBytes().get(r)),
                    labels(info.getReturnInst().get(r), true));
            }
        }

        // 2. The three Instruction Sequence tabs.
        List<InstructionSequence> first = new ArrayList<>();
        List<InstructionSequence> pre = new ArrayList<>();
        List<InstructionSequence> ret = new ArrayList<>();
        for (FunctionBitPatternInfo info : infos) {
            if (info.getFirstInst().getInstructions()[0] != null) {
                first.add(info.getFirstInst());
            }
            if (info.getPreBytes() != null && info.getPreInst().getInstructions()[0] != null) {
                pre.add(info.getPreInst());
            }
            for (InstructionSequence seq : info.getReturnInst()) {
                if (seq.getInstructions()[0] != null) {
                    ret.add(seq);
                }
            }
        }
        line("");
        line("== First Instructions Tree: %d sequences ==", first.size());
        printTree(tree(first), 0, first.size());
        line("== Pre-Instructions Tree: %d sequences; the root is the instruction just before the start ==", pre.size());
        printTree(tree(pre), 0, pre.size());
        line("== Return Instructions Tree: %d sequences; the root is the return itself ==", ret.size());
        printTree(tree(ret), 0, ret.size());

        // 3. The First Bytes tab under a length filter, and its top row sent to the clipboard.
        List<ByteSequenceRowObject> firstRows = ByteSequenceRowObject.getFilteredRowObjects(infos,
            PatternType.FIRST, null, new ByteSequenceLengthFilter(POST_PREFIX, POST_PREFIX));
        firstRows.sort((a, b) -> Integer.compare(b.getNumOccurrences(), a.getNumOccurrences()));
        int firstPassed = firstRows.stream().mapToInt(ByteSequenceRowObject::getNumOccurrences).sum();
        line("");
        line("== First Bytes, length filter: at least %d bytes, keep the first %d: %d of %d sequences pass ==",
            POST_PREFIX, POST_PREFIX, firstPassed, infos.size());
        for (ByteSequenceRowObject row : firstRows) {
            line("  %-12s %2d of %d  %3.0f%%", spaced(row.getSequence()), row.getNumOccurrences(),
                firstPassed, row.getPercentage());
        }
        DittedBitSequence post = new DittedBitSequence(firstRows.get(0).getSequence(), true);
        line("  Send Selected to Clipboard, the top row only:  %s   (%d of %d bits fixed)",
            post.getHexString(), post.getNumFixedBits(), 8 * POST_PREFIX);

        // 4. Analyze Sequences on a tree node -- the most-travelled path, PATH_DEPTH instructions
        //    deep -- gives the bytes of just those instructions; Merge Selected Rows shows where
        //    the sequences that took that path differ.
        List<String> pathInsts = new ArrayList<>();
        List<Integer> pathLens = new ArrayList<>();
        Node node = tree(first);
        for (int depth = 0; depth < PATH_DEPTH && !node.children.isEmpty(); depth++) {
            Map.Entry<String, Node> best = null;
            for (Map.Entry<String, Node> kid : node.children.entrySet()) {
                if (best == null || kid.getValue().count > best.getValue().count ||
                    (kid.getValue().count == best.getValue().count &&
                        kid.getKey().compareTo(best.getKey()) < 0)) {
                    best = kid;
                }
            }
            String[] parts = best.getKey().split(":");
            pathInsts.add(parts[0]);
            pathLens.add(Integer.parseInt(parts[1]));
            node = best.getValue();
        }
        InstructionSequenceTreePathFilter path =
            new InstructionSequenceTreePathFilter(pathInsts, pathLens, PatternType.FIRST);
        List<ByteSequenceRowObject> pathRows =
            ByteSequenceRowObject.getRowObjectsFromInstructionSequences(infos, path, null);
        pathRows.sort((a, b) -> Integer.compare(b.getNumOccurrences(), a.getNumOccurrences()));
        List<String> pathLabels = new ArrayList<>();
        for (int i = 0; i < pathInsts.size(); i++) {
            pathLabels.add(pathInsts.get(i) + ":" + pathLens.get(i));
        }
        line("");
        line("== Analyze Sequences on the node %s: %d of %d sequences, %d bytes each ==",
            String.join(" > ", pathLabels), node.count, first.size(), path.getTotalLength());
        for (ByteSequenceRowObject row : pathRows) {
            line("  %-27s %2d   %s", spaced(row.getSequence()), row.getNumOccurrences(),
                row.getDisassembly());
        }
        DittedBitSequence mergedPath = ByteSequenceRowObject.merge(pathRows);
        line("  Merge Selected Rows, all of them:  %s   (%d of %d bits fixed)",
            mergedPath.getHexString(), mergedPath.getNumFixedBits(), 8 * path.getTotalLength());

        // 5. The same merge over the whole first-bytes window.
        List<ByteSequenceRowObject> fullRows = ByteSequenceRowObject.getFilteredRowObjects(infos,
            PatternType.FIRST, null, new ByteSequenceLengthFilter(FIRST_BYTES, FIRST_BYTES));
        int fullPassed = fullRows.stream().mapToInt(ByteSequenceRowObject::getNumOccurrences).sum();
        DittedBitSequence mergedAll = ByteSequenceRowObject.merge(fullRows);
        line("  On the whole %d-byte window, which %d of %d sequences fill:", FIRST_BYTES,
            fullPassed, infos.size());
        line("    %s   (%d of %d bits fixed)", mergedAll.getHexString(), mergedAll.getNumFixedBits(),
            8 * FIRST_BYTES);

        List<ByteSequenceRowObject> returnRows = ByteSequenceRowObject.getFilteredRowObjects(infos,
            PatternType.RETURN, null, new ByteSequenceLengthFilter(-PRE_SUFFIX, PRE_SUFFIX));
        returnRows.sort((a, b) -> Integer.compare(b.getNumOccurrences(), a.getNumOccurrences()));
        line("");
        int returnPassed = returnRows.stream().mapToInt(ByteSequenceRowObject::getNumOccurrences).sum();
        line("== Return Bytes, length filter: at least %d bytes, keep the last %d: %d sequences ==",
            PRE_SUFFIX, PRE_SUFFIX, returnPassed);
        for (ByteSequenceRowObject row : returnRows) {
            line("  %-12s %2d of %d  %3.0f%%", spaced(row.getSequence()), row.getNumOccurrences(),
                returnPassed, row.getPercentage());
        }
        DittedBitSequence preSeq = new DittedBitSequence(returnRows.get(0).getSequence(), true);
        line("  Send Selected to Clipboard, the top row only:  %s   (%d of %d bits fixed)",
            preSeq.getHexString(), preSeq.getNumFixedBits(), 8 * PRE_SUFFIX);

        // 6. Return Bytes, then Mine Sequential Patterns on the whole first-bytes window.
        line("");
        line("== Mine Sequential Patterns on the %d full %d-byte first sequences: support >= %.0f%%, >= %d fixed bits, as bits ==",
            fullPassed, FIRST_BYTES, 100 * MIN_SUPPORT, MIN_FIXED_BITS);
        List<ClosedPatternRowObject> mined = ClosedPatternRowObject.mineClosedPatterns(fullRows,
            MIN_SUPPORT, MIN_FIXED_BITS, true, PatternType.FIRST, null, null);
        mined.sort((a, b) -> a.getNumOccurrences() != b.getNumOccurrences()
                ? Integer.compare(b.getNumOccurrences(), a.getNumOccurrences())
                : Integer.compare(b.getNumFixedBits(), a.getNumFixedBits()));
        int shown = Math.min(MAX_MINED_LISTED, mined.size());
        line("  %d closed patterns; the first %d, most common first:", mined.size(), shown);
        for (ClosedPatternRowObject m : mined.subList(0, shown)) {
            line("  %-72s %3d fixed bits  %2d of %d  %3.0f%%",
                m.getPatternInfo().getDittedBitSequence().getHexString(), m.getNumFixedBits(),
                m.getNumOccurrences(), fullPassed, m.getPercentage());
        }

        // 7. Export: the two merges as a PRE and a POST pattern, in the analyzer's file format.
        List<PatternInfoRowObject> clipboard = new ArrayList<>();
        clipboard.add(new PatternInfoRowObject(PatternType.PRE, preSeq, null));
        clipboard.add(new PatternInfoRowObject(PatternType.FIRST, post, null));
        File patternFile = new File(xmlDir, currentProgram.getName() + "_patterns.xml");
        PatternInfoRowObject.exportXMLFile(clipboard, patternFile, EXPORT_POST_BITS, EXPORT_TOTAL_BITS);
        line("");
        line("== Export Selected Patterns, total bits %d, post bits %d: %s ==", EXPORT_TOTAL_BITS,
            EXPORT_POST_BITS, patternFile.getName());
        out.append(Files.readString(patternFile.toPath()));

        // 8. Evaluate: the POST pattern alone, then the PRE pattern followed immediately by it.
        line("== Evaluate: the POST pattern %s, everywhere in the program ==", post.getHexString());
        evaluate(post, 0);
        DittedBitSequence pair = preSeq.concatenate(post);
        line("== Evaluate: PRE followed immediately by POST, %s ==", pair.getHexString());
        evaluate(pair, preSeq.getSize());

        Files.writeString(outFile, out.toString());
        println("wrote " + outFile);
    }
}

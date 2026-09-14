import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.rewriter.DexRewriter;
import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;
import com.android.tools.smali.dexlib2.rewriter.TypeRewriter;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Offline extraction of original SDK bytecode and its minimal R8 outline dependencies. */
public final class BuildShumeiDex {
    private static final String SOURCE_SHA256 = "7af1a0b28d684cad807ef435242c490c6a131117faaf8d3f8f0e772fcb7f4fee";
    private static final String SDK_PREFIX = "Lcom/ishumei/smantifraud/";
    private static final String COMPAT_PREFIX = "Lcom/leapauto/security/shumei/compat/";
    private static final Map<String, Set<String>> HELPERS = helperMethods();

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !(args[0].equals("build") || args[0].equals("check"))) {
            throw new IllegalArgumentException("Usage: build|check source.dex output.dex");
        }
        Path sourcePath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        require(sha256(Files.readAllBytes(sourcePath)).equals(SOURCE_SHA256), "Unexpected reference DEX version");
        DexBackedDexFile source = DexFileFactory.loadDexFile(sourcePath.toFile(), null);
        List<ClassDef> sdkClasses = sdkClasses(source);
        List<ClassDef> selected = new ArrayList<>(sdkClasses);
        selected.addAll(extractHelpers(source));
        DexFile original = new ImmutableDexFile(source.getOpcodes(), selected);
        Map<String, String> relocation = new TreeMap<>();
        HELPERS.keySet().forEach(type -> relocation.put(type, COMPAT_PREFIX + type.substring(1)));
        DexFile relocated = rewrite(original, relocation);
        byte[] expectedBytes = canonical(relocated);
        DexBackedDexFile expected = new DexBackedDexFile(source.getOpcodes(), expectedBytes);
        verifyClosure(expected);
        verifyPreservation(original, expected, relocation);
        if (Files.exists(outputPath)) verifySdkBaseline(sdkClasses, outputPath, source.getOpcodes(), relocation);
        if (args[0].equals("build")) {
            Files.createDirectories(outputPath.toAbsolutePath().getParent());
            Path temporary = Files.createTempFile(outputPath.toAbsolutePath().getParent(), "shumei-", ".dex");
            try {
                Files.write(temporary, expectedBytes);
                Files.move(temporary, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        DexBackedDexFile actual = DexFileFactory.loadDexFile(outputPath.toFile(), source.getOpcodes());
        verifyClosure(actual);
        require(Arrays.equals(canonical(actual), expectedBytes), "Output differs from the verified extraction");
        System.out.println("sourceSha256=" + SOURCE_SHA256);
        System.out.println("sdkClasses=" + sdkClasses.size() + " helperClasses=" + HELPERS.size() + " helperMethods=10");
        System.out.println("unresolvedNonPlatformTypes=0 missingHelperMethods=0 defaultPackageHelpers=0");
        System.out.println("sdkAndHelperBytecodePreserved=true relocation=" + COMPAT_PREFIX);
        System.out.println("outputBytes=" + Files.size(outputPath) + " outputSha256=" + sha256(Files.readAllBytes(outputPath)));
    }

    private static Map<String, Set<String>> helperMethods() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("Lic3;", Set.of("a(Ljava/lang/StringBuilder;Ljava/lang/String;JLjava/lang/String;)J",
            "e(Ljava/lang/StringBuilder;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"));
        result.put("Ljc3;", Set.of("g(Ljava/lang/String;Ljava/lang/Throwable;)V"));
        result.put("Lq10;", Set.of("o(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/StringBuilder;"));
        result.put("Lue1;", Set.of("g(ILjava/lang/String;)Ljava/lang/String;"));
        result.put("Luu1;", Set.of("i(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            "n(Ljava/lang/StringBuilder;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
        result.put("Lw;", Set.of("p(Ljava/lang/String;)V", "s(Ljava/lang/String;)V"));
        result.put("Lw50;", Set.of("x(Ljava/lang/String;)V"));
        return result;
    }

    private static List<ClassDef> extractHelpers(DexFile source) {
        Map<String, ClassDef> definitions = definitions(source);
        List<ClassDef> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : HELPERS.entrySet()) {
            ClassDef original = definitions.get(entry.getKey());
            require(original != null, "Missing source helper: " + entry.getKey());
            List<Method> methods = new ArrayList<>();
            Set<String> found = new TreeSet<>();
            for (Method method : original.getMethods()) {
                if (!entry.getValue().contains(signature(method))) continue;
                require(AccessFlags.STATIC.isSet(method.getAccessFlags()), "Helper must remain static");
                require(method.getImplementation() != null, "Missing original helper implementation");
                methods.add(method);
                found.add(signature(method));
            }
            require(found.equals(entry.getValue()), "Incomplete source helper: " + entry.getKey());
            // These static outline methods need no unrelated interfaces, fields or instance methods.
            result.add(new ImmutableClassDef(original.getType(),
                AccessFlags.PUBLIC.getValue() | AccessFlags.FINAL.getValue() | AccessFlags.SYNTHETIC.getValue(),
                "Ljava/lang/Object;", List.of(), original.getSourceFile(), List.of(), List.of(), methods));
        }
        return result;
    }

    private static void verifyClosure(DexBackedDexFile dex) {
        Map<String, ClassDef> definitions = definitions(dex);
        Set<String> missing = new TreeSet<>();
        for (String type : dex.getTypeSection()) {
            String unwrapped = type.replaceFirst("^\\[+", "");
            if (unwrapped.startsWith("L") && !isPlatform(unwrapped) && !definitions.containsKey(unwrapped)) {
                missing.add(unwrapped);
            }
            require(!HELPERS.containsKey(unwrapped), "Unrelocated helper reference: " + unwrapped);
        }
        require(missing.isEmpty(), "Unresolved non-platform types: " + missing);
        for (MethodReference method : dex.getMethodSection()) {
            if (!method.getDefiningClass().startsWith(COMPAT_PREFIX)) continue;
            ClassDef owner = definitions.get(method.getDefiningClass());
            Set<String> methods = new TreeSet<>();
            owner.getMethods().forEach(candidate -> methods.add(signature(candidate)));
            require(methods.contains(signature(method)), "Missing helper method: " + signature(method));
        }
    }

    private static void verifyPreservation(DexFile original, DexFile output, Map<String, String> relocation) throws Exception {
        Map<String, String> reverse = new TreeMap<>();
        relocation.forEach((from, to) -> reverse.put(to, from));
        require(Arrays.equals(canonical(original), canonical(rewrite(output, reverse))),
            "Bytecode changed beyond helper type relocation");
    }

    private static void verifySdkBaseline(List<ClassDef> sdkClasses, Path output, Opcodes opcodes,
                                          Map<String, String> relocation) throws Exception {
        DexFile existing = DexFileFactory.loadDexFile(output.toFile(), opcodes);
        Map<String, String> reverse = new TreeMap<>();
        relocation.forEach((from, to) -> reverse.put(to, from));
        DexFile previousSdk = new ImmutableDexFile(opcodes, sdkClasses(rewrite(existing, reverse)));
        DexFile sourceSdk = new ImmutableDexFile(opcodes, sdkClasses);
        if (!Arrays.equals(canonical(withoutDebug(previousSdk)), canonical(withoutDebug(sourceSdk)))) {
            Map<String, ClassDef> previous = definitions(withoutDebug(previousSdk));
            List<String> changed = new ArrayList<>();
            for (ClassDef candidate : withoutDebug(sourceSdk).getClasses()) {
                ClassDef old = previous.remove(candidate.getType());
                if (old == null || !Arrays.equals(canonical(new ImmutableDexFile(opcodes, List.of(old))),
                    canonical(new ImmutableDexFile(opcodes, List.of(candidate))))) changed.add(candidate.getType());
            }
            System.out.println("baselineClasses=" + previousSdk.getClasses().size() + " sourceClasses=" + sourceSdk.getClasses().size());
            System.out.println("differentClassCount=" + changed.size() + " firstDifferences=" + changed.stream().limit(5).toList());
            System.out.println("extraBaselineClassCount=" + previous.size());
        }
        require(Arrays.equals(canonical(withoutDebug(previousSdk)), canonical(withoutDebug(sourceSdk))),
            "Existing SDK bytecode does not match the pinned reference; manual review required");
    }

    private static DexFile withoutDebug(DexFile dex) {
        // The earlier smali extraction synthesized debug locals; executable code must still match.
        List<ClassDef> classes = new ArrayList<>();
        for (ClassDef original : dex.getClasses()) {
            List<Method> methods = new ArrayList<>();
            for (Method method : original.getMethods()) {
                List<ImmutableMethodParameter> parameters = new ArrayList<>();
                method.getParameters().forEach(parameter -> parameters.add(
                    new ImmutableMethodParameter(parameter.getType(), parameter.getAnnotations(), null)));
                var implementation = method.getImplementation();
                var code = implementation == null ? null : new ImmutableMethodImplementation(
                    implementation.getRegisterCount(), implementation.getInstructions(), implementation.getTryBlocks(), List.of());
                methods.add(new ImmutableMethod(method.getDefiningClass(), method.getName(), parameters, method.getReturnType(),
                    method.getAccessFlags(), method.getAnnotations(), method.getHiddenApiRestrictions(), code));
            }
            classes.add(new ImmutableClassDef(original.getType(), original.getAccessFlags(), original.getSuperclass(),
                original.getInterfaces(), null, original.getAnnotations(), original.getFields(), methods));
        }
        return new ImmutableDexFile(dex.getOpcodes(), classes);
    }

    private static DexFile rewrite(DexFile dex, Map<String, String> names) {
        DexRewriter rewriter = new DexRewriter(new RewriterModule() {
            @Override public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
                return new TypeRewriter() {
                    @Override protected String rewriteUnwrappedType(String value) {
                        return names.getOrDefault(value, value);
                    }
                };
            }
        });
        return rewriter.getDexFileRewriter().rewrite(dex);
    }

    private static List<ClassDef> sdkClasses(DexFile dex) {
        List<ClassDef> classes = new ArrayList<>();
        dex.getClasses().forEach(value -> { if (value.getType().startsWith(SDK_PREFIX)) classes.add(value); });
        require(!classes.isEmpty(), "No SDK classes in source");
        return classes;
    }

    private static Map<String, ClassDef> definitions(DexFile dex) {
        Map<String, ClassDef> result = new TreeMap<>();
        dex.getClasses().forEach(value -> result.put(value.getType(), value));
        return result;
    }

    private static String signature(MethodReference method) {
        StringBuilder value = new StringBuilder(method.getName()).append('(');
        method.getParameterTypes().forEach(value::append);
        return value.append(')').append(method.getReturnType()).toString();
    }

    private static boolean isPlatform(String type) {
        return List.of("Landroid/", "Ljava/", "Ljavax/", "Ldalvik/", "Lorg/json/", "Lorg/xml/", "Lorg/w3c/")
            .stream().anyMatch(type::startsWith);
    }

    private static byte[] canonical(DexFile dex) throws Exception {
        MemoryDataStore store = new MemoryDataStore();
        try {
            DexPool.writeTo(store, dex);
            return store.getData();
        } finally {
            store.close();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

package com.foursquare.heapaudit;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

class HeapSettings {

    public static void parse(String args,
                             boolean dynamic) throws FileNotFoundException {

        // The following describes how to specify the args string.
        // 
        //   Syntax for args: [ -Xconditional ]
        //                    [ -O<file> ]
        //                    [ -S<path> |
        //                      -A<path> |
        //                      -D<path> |
        //                      -T<path> |
        //                      -I<path> |
        //                      -R<path> ]*
        //
        //   Syntax for path: <class_regex>[@<method_regex>]
        //
        //   * Use -Xconditional if most of the time zero recorders are
        //     registered to actively record heap allocations. It includes
        //     extra if-statements to short-circuit the recording logic.
        //     However, if recorders are expected to be mostly present, then
        //     including extra if-statements adds extra execution instructions.
        //
        //   * Use -O to redirect the output to the designated file.
        //
        //   * Use -S to suppress auditing a particular path and its sub calls.
        //   * Use -A to avoid auditing a particular path.
        //   * Use -D to debug instrumentation of a particular path.
        //   * Use -T to trace execution of auditing a particular path.
        //   * Use -I to dynamically inject recorders for a particular path.
        //   * Use -R to dynamically remove recorders for a particular path.
        //
        //   * Paths are specified as a one or two part regular expressions
        //     where if the second part if omitted, it is treated as a catch all
        //     wild card. The class_regex matches with the full namespace path
        //     of the class where '/' is used as the separator. The method_regex
        //     matches with the method name and method signature where the
        //     signature follows the JNI method descriptor convention. See
        //     http://java.sun.com/docs/books/jni/html/types.html
        //
        //   For instance:
        //
        //     The following avoids auditing all methods under the class
        //     com/foursquare/MyUtil
        //       -Acom/foursquare/MyUtil
        //
        //     The following injects recorders for all toString methods under
        //     the class com/foursquare/MyTest
        //       -Icom/foursquare/MyTest@toString.+
        //
        //   The -S option is more applicable to general scenarios over the -A
        //   option. The former suppresses the entire sub call tree as oppose to
        //   only skipping the designated class or method. The latter will still
        //   include the indirect allocations down the callstack.
        //
        //   The -D and -T options are normally used for HeapAudit development
        //   purposes only.
        //
        //   The -I and -R options are only applicable when HeapAudit is
        //   dynamically injected into a running process. The dynamically
        //   injected recorders capture all heap allocations that occur within
        //   the designated method, including sub-method calls.

        HeapSettings.dynamic = dynamic;

        toSuppressAuditing.clear();

        toAvoidAuditing.clear();
        
        toIncludeAuditing.clear();

        toDebugAuditing.clear();

        toTraceAuditing.clear();

        toInjectRecorder.clear();

        toRemoveRecorder.clear();

        toAvoidAuditing.addAll(Arrays.asList(new Pattern("java/lang/ThreadLocal"),
                                             new Pattern("org/objectweb/asm/.+"),
                                             new Pattern("com/foursquare/heapaudit/HeapRecorder.*"),
                                             new Pattern("com/foursquare/heapaudit/HeapUtil.*"),
                                             new Pattern("[$].*"),
                                             new Pattern("java/.+"),
                                             new Pattern("javax/.+"),
                                             new Pattern("org/jcp/.+"),
                                             new Pattern("org/xml/.+"),
                                             new Pattern("com/apple/.+"),
                                             new Pattern("apple/.+"),
                                             new Pattern("com/sun/.+"),
                                             new Pattern("sun/.+"),
                                             new Pattern("oracle/.+"),
                                             new Pattern("jrockit/.+")));

        // actually do transform these classes, despite the matches in toAvoidAuditing
        toIncludeAuditing.addAll(Arrays.asList(new Pattern("java/util/.*Map*"),
                                               new Pattern("java/util/.*Set.*"),
                                               // if we xform ArrayList, we'll stack-overflow because we use it in HeapAudit
//                                               new Pattern("java/util/.*List.*"),
                                               new Pattern("java/util/concurrent/.+")));
                
        if (args != null) {

            for (String arg: args.split("[ #]")) {

                if ((arg.length() < 2) ||
                    (arg.charAt(0) != '-')) {

                    throw new IllegalArgumentException(arg);

                }

                String value = (arg.length() > 2) ? arg.substring(2) : null;

                switch (arg.charAt(1)) {

                case 'X':

                    if (value.equals("conditional")) {

                        conditional = true;

                    }

                    break;

                case 'O':

                    output = value.length() > 0 ? new PrintStream(value) : System.out;

                    break;

                case 'S':

                    toSuppressAuditing.add(new Pattern(value));

                    break;

                case 'A':

                    toAvoidAuditing.add(new Pattern(value));

                    break;

                case 'D':

                    toDebugAuditing.add(new Pattern(value));

                    break;

                case 'I':

                    toInjectRecorder.add(new Pattern(value));

                    break;

                case 'R':

                    toRemoveRecorder.add(new Pattern(value));

                    break;

                case 'T':

                    toTraceAuditing.add(new Pattern(value));

                    break;

                default:

                    throw new IllegalArgumentException(arg);

                }

            }

        }

    }

    // The dynamic flag indicates whether the HeapAudit java agent was loaded
    // dynamically or statically.

    public static boolean dynamic = false;

    // The conditional setting determines whether to optimize for tradeoffs by
    // adding extra bytecode instructions to check and potentially skip the code
    // paths for executing the recording logic. If HeapAudit is expected to
    // always have at least one recorder present, then setting conditional to
    // false can avoid the checks.

    public static boolean conditional = false;

    public static PrintStream output = System.out;

    private final static ArrayList<Pattern> toSuppressAuditing = new ArrayList<Pattern>();

    private final static ArrayList<Pattern> toAvoidAuditing = new ArrayList<Pattern>();

    // override avoidance
    private final static ArrayList<Pattern> toIncludeAuditing = new ArrayList<Pattern>();

    private final static ArrayList<Pattern> toDebugAuditing = new ArrayList<Pattern>();

    private final static ArrayList<Pattern> toTraceAuditing = new ArrayList<Pattern>();

    private final static ArrayList<Pattern> toInjectRecorder = new ArrayList<Pattern>();

    private final static ArrayList<Pattern> toRemoveRecorder = new ArrayList<Pattern>();

    private static boolean should(ArrayList<Pattern> patterns,
                                  String classPath,
                                  String methodName) {

        for (Pattern pattern: patterns) {

            if (classPath.matches(pattern.classPattern)) {

                if ((methodName == null) ||
                    (pattern.methodPattern == null) ||
                    methodName.matches(pattern.methodPattern)) {

                    return true;

                }

            }

        }

        return false;

    }

    public static boolean shouldSuppressAuditing(String classPath,
                                                 String methodName) {

        return should(toSuppressAuditing,
                      classPath,
                      methodName);

    }

    public static boolean shouldAvoidAuditing(String classPath,
                                              String methodName) {

        return should(toAvoidAuditing,
                      classPath,
                      methodName) &&
            (! should(toIncludeAuditing,
                      classPath,
                      methodName));

    }

    public static boolean shouldDebugAuditing(String classPath,
                                            String methodName) {

        return should(toDebugAuditing,
                      classPath,
                      methodName);

    }

    public static boolean shouldTraceAuditing(String classPath,
                                              String methodName) {

        return should(toTraceAuditing,
                      classPath,
                      methodName);

    }

    public static boolean shouldInjectRecorder(String classPath,
                                               String methodName) {

        return dynamic &&
            should(toInjectRecorder,
                   classPath,
                   methodName);

    }

    public static boolean shouldRemoveRecorder(String classPath,
                                               String methodName) {

        return dynamic &&
            should(toRemoveRecorder,
                   classPath,
                   methodName);

    }

    private static class Pattern {

        public Pattern(String pattern) {

            String[] parts = pattern.split("@");

            classPattern = parts[0];

            methodPattern = (parts.length > 1) ? parts[1] : null;

        }

        public final String classPattern;

        public final String methodPattern;

    }

}


import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.Assert;

import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InteractiveStringReplace extends DirectoryServerTestCase {

  private static class ReplacementInfo {
    private final String inStr;
    private final String outStr;
    private final Pattern inPattern;
    private int numReplacements = 0;
    private boolean doPrompt = true;
    private final String type;

    public ReplacementInfo(String inStr, String outStr, String inPatternStr, boolean doPrompt, String type) {
      this.inStr = inStr;
      this.outStr = outStr;
      this.inPattern = Pattern.compile(inPatternStr);
      this.doPrompt = doPrompt;
      this.type = type;
    }


    public String toString() {
      return type + "," + inStr + ", " + outStr + ", " + numReplacements;
    }

    private void incNumReplacements() {
      numReplacements++;
    }
  }

  private LinkedHashMap<String, ReplacementInfo> replacementInfos = null;

//  private LinkedHashMap<String,String> replacements;
//  private Map<String, Pattern> replacementPattern;
//  private Map<String, Integer> replacementCountByOriginal;
  private boolean doUpdate = false;
  private boolean doPrompt = false;
  private LinkedHashSet<File> modifiedFiles = new LinkedHashSet<File>();
  private int numMods = 0;

  private static final List<String> abbreviations = Arrays.asList(new String[]
          {"aci", "ip", "ssl", "dn", "rdn", "jmx", "smtp", "http",
           "https", "ldap", "ldaps", "ldif", "jdbc", "tcp", "tls",
           "pkcs11", "sasl", "gssapi", "md5", "je", "dse", "fifo",
           "vlv", "uuid", "md5", "sha1", "sha256", "sha384", "sha512",
           "tls"});

  private void setReplacements(List<String> replacementLines) throws Exception {
    replacementInfos = new LinkedHashMap<String, ReplacementInfo>();

    for (String replacement: replacementLines) {
      String[] components = replacement.split("\\s*,\\s*");
      if (components.length >= 2) {
        String in = components[0].trim();
        String out = components[1].trim();
        if ((in.length() > 0) && (out.length() > 0)) {
          addReplacement(in, out, "(?<![a-zA-Z\\-])\\Q" + in + "\\E(?![a-zA-Z\\-])",
                  false, in.startsWith("ds-cfg") ? "schema" : "property-name");

          if (components.length >= 3) {
            String type = components[2].trim();
            String inCaml = toCamlCase(in);
            String outCaml = toCamlCase(out);

            // Setter
            String inSetter = "set" + inCaml;
            String outSetter = "set" + outCaml;
            // Replace usages, but not 
            addReplacement(inSetter, outSetter, "(?<![a-zA-Z]\\s{0,5}+)\\b" + inSetter + "\\b", true, "setter");


            // Getter
            String inGetter = "get" + inCaml;
            String outGetter = "get" + outCaml;
            if (type.equals("boolean")) {
              inGetter = "is" + inCaml;
              outGetter = "is" + outCaml;
            }
            addReplacement(inGetter, outGetter, "(?<![a-zA-Z]\\s{0,5}+)\\b" + inGetter + "\\b", true, "getter");

            String inPropGetter = "get" + inCaml + "PropertyDefinition";
            String outPropGetter = "get" + outCaml + "PropertyDefinition";
            addReplacement(inPropGetter, outPropGetter, "(?<![a-zA-Z]\\s{0,5}+)\\b" + inPropGetter + "\\b", true, "prop-getter");


            if (type.equals("enumeration")) {
              addReplacement(inCaml, outCaml, "\\b" + inCaml + "\\b", true, "enum-class");
            }
          }
        }
      }
    }
  }

  private void addReplacement(String in, String out, String inPattern, boolean doPrompt, String type) {
    replacementInfos.put(in, new ReplacementInfo(in, out, inPattern, doPrompt, type));
  }


  private String toCamlCase(String hyphenated) {
    String[] components = hyphenated.split("\\-");
    StringBuilder buffer = new StringBuilder();
    for (String component: components) {
      if (abbreviations.contains(component)) {
        buffer.append(component.toUpperCase());
      } else {
        buffer.append(component.substring(0, 1).toUpperCase() +
                component.substring(1));
      }
    }
    return buffer.toString();
  }




  private void setDoUpdate(boolean doUpdate) {
    this.doUpdate = doUpdate;
  }


  public void setDoPrompt(boolean doPrompt) {
    this.doPrompt = doPrompt;
  }


  private void replaceAllRecursively(File root) throws Exception {
    List<File> filesToProcess = new ArrayList<File>();
    recursivelyList(root, filesToProcess);

    for (File fileToProcess: filesToProcess) {
//      System.out.println("");
//      System.out.println("");
      System.out.println("Now processing: " + fileToProcess);
//      System.out.println("");
      replaceAll(fileToProcess);
    }
  }


  private String replaceAll(String str, Collection<String> candidateReplacements) throws Exception {

    if (candidateReplacements == null) {
      candidateReplacements = replacementInfos.keySet();
    }

    for (String strToReplace: candidateReplacements) {
      final ReplacementInfo replacementInfo = replacementInfos.get(strToReplace);
      final String replacement = replacementInfo.outStr;
      Pattern pattern = replacementInfo.inPattern;
      Matcher matcher = pattern.matcher(str);

      StringBuffer buffer = new StringBuffer(str.length());
      while (matcher.find()) {
        String thisReplacement = replacement;
        if (doPrompt && replacementInfo.doPrompt) {
          System.out.println();
          System.out.println("In " + str);
          System.out.print("    " + strToReplace + "  -->  " + replacement + " ");
          String response = readStdinLine();
          if (response.toLowerCase().startsWith("n")) {
            thisReplacement = matcher.group();
          } else if (response.toLowerCase().startsWith("a")) {
            replacementInfo.doPrompt = false;
          }
        }
        replacementInfo.incNumReplacements();

        matcher.appendReplacement(buffer, thisReplacement);
        numMods++;
      }
      matcher.appendTail(buffer);

      str = buffer.toString();
    }

    return str;
  }
  // todo add a check for circular dependencies!!!
  private void replaceAll(File file) throws Exception {
    ArrayList<String> linesIn = TestCaseUtils.readFileToLines(file);
    ArrayList<String> linesOut = new ArrayList<String>();

    StringBuilder buffer = new StringBuilder();
    for (String line : linesIn) {
      buffer.append(line).append(ServerConstants.EOL);
    }
    String fullFile = buffer.toString();

    List<String> candidateReplacements = findCandidateReplacements(fullFile);

    for (String line: linesIn) {
      linesOut.add(replaceAll(line, candidateReplacements));
    }

    if (!linesIn.equals(linesOut)) {
      if (doUpdate) {
        if (linesIn.size() != linesOut.size()) {
          throw new RuntimeException("There seem to be too many problems.");
        }
        TestCaseUtils.writeFile(file, linesOut);
      }
      modifiedFiles.add(file);
    }
  }

  private List<String> findCandidateReplacements(String fullFile) {
    List<String> candidateReplacements = new ArrayList<String>();

    for (String searchFor: replacementInfos.keySet()) {
      if (fullFile.indexOf(searchFor) >= 0) {
        candidateReplacements.add(searchFor);
      }
    }

    return candidateReplacements;
  }

  private void recursivelyList(File base, List<File> filesToProcess) {
    if (base.isFile()) {
      if ((base.getPath().indexOf(".svn") >= 0) || (base.getPath().indexOf("generated") >= 0)) {
        return;
      }

      String extension = base.getName().replaceAll(".*\\.", "");

      if (extension.equals("java") ||
          extension.equals("xml")  ||
          extension.equals("txt")  ||
          extension.equals("ldif") ||
          extension.equals("dat") ||
          extension.equals("py") ||
          extension.equals("properties"))
//      if (extension.equals("dat"))
      {
        filesToProcess.add(base);
      }
    } else {
      File[] files = base.listFiles();
      for (int i = 0; i < files.length; i++) {
        File file = files[i];
        recursivelyList(file, filesToProcess);
      }
    }
  }




  public static void main(String[] args) throws Exception {
    if (!args[0].equals("doUpdate") && !args[0].equals("noUpdate")) {
      throw new Exception("The first argument must be doUpdate or noUpdate.");
    }
    if (!args[1].equals("doPrompt") && !args[1].equals("noPrompt")) {
      throw new Exception("The second argument must be doUpdate or noUpdate.");
    }


    InteractiveStringReplace replacer = new InteractiveStringReplace();
    replacer.setDoUpdate(args[0].equals("doUpdate"));
    replacer.setDoPrompt(args[1].equals("doPrompt"));
    replacer.setReplacements(TestCaseUtils.readFileToLines(new File(args[2])));

    for (int i = 3; i < args.length; i++) {
      replacer.replaceAllRecursively(new File(args[i]));
    }

    System.out.println("ReplacmentInfos:");
    for (ReplacementInfo replacementInfo: replacer.replacementInfos.values()) {
      System.out.println(replacementInfo);
    }

    System.out.println("Made changes " + replacer.numMods + " across " + replacer.modifiedFiles.size() + " files.");
  }


  private static final BufferedReader stdinReader = new BufferedReader (new InputStreamReader (System.in));
  private static String readStdinLine() throws Exception {
    return stdinReader.readLine().trim();
  }







  private static Object[] toArgsArray(String orig, String expected, String... replacements) {
    return new Object[]{orig, expected, Arrays.asList(replacements)};
  }

  @DataProvider
  public Object[][] replaceAllTestCases() {
    return new Object[][] {
            toArgsArray("simple-replacement", "complex-replacement", "simple-replacement,complex-replacement"),
      toArgsArray("no-replacements", "no-replacements"),
      toArgsArray("unused-replacement", "unused-replacement", "no-match,should-not-be-there"),
      toArgsArray("empty-replacement", "empty-replacement", "no-match,"),
      toArgsArray("empty-matcher", "empty-matcher", ",replacement"),
      toArgsArray("multiple-duplicate-replacement multiple-duplicate-replacement",
                  "multiple-dupe-replacement multiple-dupe-replacement",
                  "multiple-duplicate-replacement,multiple-dupe-replacement"),
      toArgsArray(" space-boundaries ",
                  " space-boundaries "),
      toArgsArray(" space-boundaries ",
                  " space-on-ends ",
                  "space-boundaries,space-on-ends"),
      toArgsArray("\"quote-boundaries\"",
                  "\"quote-on-ends\"",
                  "quote-boundaries,quote-on-ends"),
      toArgsArray("-dash-start",
                  "-dash-start",
                  "dash-start,undash-start"),
      toArgsArray("dash-end-",
                  "dash-end-",
                  "dash-end,undash-end"),
      toArgsArray("char-start",
                  "char-start",
                  "har-start,nohar-start"),
      toArgsArray("end-char",
                  "end-char",
                  "end-cha,end-nocha"),
    };
  }

  @Test(dataProvider = "replaceAllTestCases")
  public void testReplaceAll(String orig, String expected, List<String> replacements) throws Exception {
    InteractiveStringReplace replacer = new InteractiveStringReplace();
    replacer.setReplacements(replacements);
    replacer.setDoUpdate(false);
    replacer.setDoPrompt(false);
    String actual = replacer.replaceAll(orig, null);
    Assert.assertEquals(actual, expected);
  }
}


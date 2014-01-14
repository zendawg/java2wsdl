package org.meh.java2wsdl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FilenameUtils;

/**
 * Hello world!
 *
 */
public class AnnotatorApp {

  /**
   * How classes are named to an interface.
   */
  public static final String DEFAULT_CLASS_IMPL_MARKER = "Impl";
  /**
   * Is the class marker a prefix or suffix?
   */
  public static final boolean DEFAULT_SUFFIX_CLASS_IMPL_MARKER = true;
  /**
   * User defined map of classes that contain named sub-interfaces.
   */
  private Map<String, List<String>> subInterfaces = new HashMap<String, List<String>>();
  /**
   * Directories of interfaces.
   */
  private File[] dirIfaces;
  /**
   * Directories where class implementations of interfaces are kept.
   */
  private File[] dirImpls;
  /**
   * The root directory where the sources reside (at the top package level).
   */
  private File dirBase;
  /**
   * The prefix/suffix of the class, if there is one.
   */
  private String extension;
  /**
   * Prefixes to apply to all named xml types; enables users to add prefixes to
   * created class names for ease of use.
   */
  private String[] xmlTypePrefixes;
  /**
   * Prefix or suffixed class impl name?
   */
  private boolean suffixed;

  public static void main(String[] args) {
    Options options = new Options();
    Option optionIfaces = new Option("i", "dir-interface", true,
            "Specify the directories where the interfaces are kept;"
            + " separate using semi-colon (';').");
    Option optionImpls = new Option("c", "dir-class-impl", true,
            "Specify the directories where implementations of implementations are"
            + " kept; separate using semi-colon (';'). If the same as"
            + " the interface directory, just use dir-interface and omit"
            + " this option.");
    Option optionBase = new Option("b", "base-class-dir", true,
            "Set the root directory from which the resulting paths, trimmed"
            + " from the -c and -i options, to determine package names.");
    Option optionIfaceMarker = new Option("m", "class-impl-marker", true,
            "Give the generic name of the class implementation prefix/suffix,"
            + " like Impl, Base, etc. Defaults to "
            + DEFAULT_CLASS_IMPL_MARKER);
    Option optionSuffixed = new Option("s", "is-suffixed", true,
            "Determine if the interface suffix is appended to the name of the"
            + " process class (defaults to " + DEFAULT_SUFFIX_CLASS_IMPL_MARKER
            + ") or is prepended.");
    Option optionXmlTypesPrefix = new Option("x", "xml-type-prefixes", true,
            "Specify XML type name prefix for classes; mainly used to"
            + " change generated classes to contain a common prefix for ease"
            + " of use.");
    Option optionSubInterfaces = new Option("u", "sub-interfaces", true,
            "Specify [InterfaceSuffix]:[class1,class2,...,classN] sub-interface"
            + " names for given class suffix. Separate multiple listings with ';'");
    Option optionDryRun = new Option("n", "not-really", false,
            "Print out what would be transformed, but don't actually do it.");
    Option optionHelp = new Option("h", "help", false,
            "Print this help then quit.");
    options.addOption(optionIfaces);
    options.addOption(optionBase);
    options.addOption(optionImpls);
    options.addOption(optionDryRun);
    options.addOption(optionIfaceMarker);
    options.addOption(optionXmlTypesPrefix);
    options.addOption(optionSubInterfaces);
    options.addOption(optionSuffixed);
    options.addOption(optionHelp);
    CommandLineParser parser = new PosixParser();
    try {
      String dirInterfaces = "";
      String dirImpls = "";
      String xmlTypePrefixes = "";
      String subIfs = null;
      File baseDir = null;
      String suffix = DEFAULT_CLASS_IMPL_MARKER;
      boolean suffixed = DEFAULT_SUFFIX_CLASS_IMPL_MARKER;
      boolean dryRun = false;
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("i") || cmd.hasOption("dir-interface")) {
        dirInterfaces = cmd.getOptionValue("dir-interface");
      }
      if (cmd.hasOption("help") || cmd.hasOption('h')) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("annotator-app [options], where [options] can be any of:", options);
        System.exit(0);
      }
      if (cmd.hasOption("d") || cmd.hasOption("dir-class-impl")) {
        dirImpls = cmd.getOptionValue("dir-class-impl");
      }
      if (cmd.hasOption("b") || cmd.hasOption("base-class-dir")) {
        baseDir = new File(cmd.getOptionValue("base-class-dir"));
      }
      if (cmd.hasOption("c") || cmd.hasOption("class-impl-marker")) {
        suffix = cmd.getOptionValue("class-impl-marker");
      }
      if (cmd.hasOption("x") || cmd.hasOption("xml-type-prefixes")) {
        xmlTypePrefixes = cmd.getOptionValue("xml-type-prefixes");
      }
      if (cmd.hasOption("s") || cmd.hasOption("is-suffixed")) {
        suffixed = Boolean.valueOf(cmd.getOptionValue("is-suffixed"));
      }
      if (cmd.hasOption("u") || cmd.hasOption("sub-interfaces")) {
        subIfs = cmd.getOptionValue("sub-interfaces");
      }
      if (cmd.hasOption("n") || cmd.hasOption("not-really")) {
        dryRun = true;
      }
      try {
        if ("".equals(dirInterfaces) || "".equals(dirImpls)) {
          throw new IllegalArgumentException("You must supply a set of "
                  + " interfaces and implementations.");
        }
        AnnotatorApp app = new AnnotatorApp(baseDir, dirInterfaces, dirImpls,
                suffix, xmlTypePrefixes, subIfs, suffixed);
        app.execute();
      } catch (Exception ex) {
        ex.printStackTrace();
        System.err.println(ex.getMessage());
        System.exit(1);
      }
    } catch (ParseException pex) {
      pex.printStackTrace();
      System.out.println("Incorrect options: " + pex.getMessage());
      System.exit(1);
    }
  }

  /**
   * Create a new run attempting to annotate and munge classes and interfaces
   * as necessary.
   * 
   * @param baseDir base directory at the root package level where all the
   * sources reside.
   * @param interfaceDirs Directories of interfaces.
   * @param implDirs Directories where class implementations of interfaces are
   * kept.
   * @param suffix the suffix for implementation classes; may be null if there
   * is no suffix.
   * @param xmlTypePrefixes Prefixes to apply to all named xml types;
   * to add prefixes to created class names for ease of use
   * @param suffixed suffix or prefix?
   */
  public AnnotatorApp(File baseDir, String interfaceDirs, String implDirs,
          String suffix, String xmlTypes, String subIfs,
          boolean suffixed) throws IOException {

    String[] ifs = subIfs.split(";");
    for (String intf : ifs) {
      String[] split = intf.split(":");
      String name = split[0];
      String[] names = split[1].split(",");
      List<String> nameList = new ArrayList<String>();
      for (String subclass : names) {
        nameList.add(subclass);
      }
      this.subInterfaces.put(name, nameList);
    }
    this.dirBase = baseDir;
    this.suffixed = suffixed;
    this.extension = suffix;
    String[] interfaces = interfaceDirs.split(";");
    String[] impls = implDirs.split(";");
    this.xmlTypePrefixes = xmlTypes.split(";");
    this.dirIfaces = new File[interfaces.length];
    this.dirImpls = new File[impls.length];
    for (int i = 0; i < interfaces.length; i++) {
      this.dirIfaces[i] = new File(interfaces[i]);
      if (!this.dirIfaces[i].exists()) {
        throw new FileNotFoundException("Error: directory " + interfaces[i]
                + " does not exist.");
      }
    }
    for (int i = 0; i < impls.length; i++) {
      this.dirImpls[i] = new File(impls[i]);
      if (!this.dirImpls[i].exists()) {
        throw new FileNotFoundException("Error: directory " + impls[i]
                + " does not exist.");
      }
    }
  }

  /**
   * Run it - trawl all classes and interfaces marking up appropriately.
   * 
   * @return execution status; 0 for success, any other result is a failure.
   */
  public int execute() throws IOException {
    int val = -1;
    // for each interface directory:
    for (int i = 0; i < this.dirIfaces.length; i++) {

      String packageName = this.dirIfaces[i].getAbsolutePath().substring(
              this.dirBase.getAbsolutePath().length() + 1);
      packageName = packageName.replace("/", ".");
      String packageNameImpl = this.dirImpls[i].getAbsolutePath().substring(
              this.dirBase.getAbsolutePath().length() + 1);
      packageNameImpl = packageNameImpl.replace("/", ".");
      // for each interface in that directory:
      File[] interfaces = this.dirIfaces[i].listFiles(new JavaSourceFileFilter());
      for (File fileInterface : interfaces) {
        this.annotateInterface(fileInterface, dirIfaces[i], packageName, packageNameImpl);
      }
    }

    for (int i = 0; i < this.dirImpls.length; i++) {

      // now deal with impl classes:
      File[] classes = this.dirImpls[i].listFiles(new JavaSourceFileFilter());
      for (File classFile : classes) {
        BufferedReader reader = new BufferedReader(new FileReader(classFile));
        StringBuilder builder = new StringBuilder();
        int read = -1;
        while ((read = reader.read()) != -1) {
          builder.append((char) read);
        }
        String fileName = FilenameUtils.removeExtension(classFile.getName());
        fileName = fileName.substring(0, (fileName.length() - this.extension.length()));
        String pattern = null;
        String xmlTypePrefix = null;
        if (this.xmlTypePrefixes != null && i < this.xmlTypePrefixes.length) {
          xmlTypePrefix = this.xmlTypePrefixes[i];
        }
        builder = this.annotateClass(builder, classFile, fileName, xmlTypePrefix);
        // check for existence of XmlAnySimpleType:
        boolean modified = false;
        String annotation = "org.apache.xmlbeans.impl.values.XmlAnySimpleTypeImpl";
        pattern = "org.apache.xmlbeans.XmlAnySimpleType get";
        if (builder.toString().contains(pattern)) {
          int index = builder.toString().indexOf(pattern);
          if (index > -1) {
            modified = true;
            builder = new StringBuilder(builder.toString().replace(pattern, annotation + " get"));
          }
          // OK, the first occurrence will be followed by two more occurrences we need to cater for:
//          this.writeFileData(fileInterface, builder.toString());
        }
        String replace = "org.apache.xmlbeans.impl.values.XmlAnySimpleTypeImpl target = null;";
        pattern = "org.apache.xmlbeans.XmlAnySimpleType target = null;";
        if (builder.toString().contains(pattern)) {
          int index = builder.toString().indexOf(pattern);
          if (index > -1) {
            modified = true;
            builder = new StringBuilder(builder.toString().replace(pattern, replace));
          }
          // OK, the first occurrence will be followed by two more occurrences we need to cater for:
//          this.writeFileData(fileInterface, builder.toString());
        }
        replace = "target = (org.apache.xmlbeans.impl.values.XmlAnySimpleTypeImpl)get_store()";
        pattern = "target = (org.apache.xmlbeans.XmlAnySimpleType)get_store()";
        if (builder.toString().contains(pattern)) {
          int index = builder.toString().indexOf(pattern);
          if (index > -1) {
            modified = true;
            builder = new StringBuilder(builder.toString().replace(pattern, replace));
          }
          // OK, the first occurrence will be followed by two more occurrences we need to cater for:
//          this.writeFileData(fileInterface, builder.toString());
        }
        if (modified) {
          this.writeFileData(classFile, builder.toString());
        }
      }
      val = 0;
    }
    return val;
  }

  /**
   * Add appropriate XML (and other) annotations to the class declaration.
   *
   * @param builder the non-null builder containing the data to process and
   * transform.
   * @param file the non-null file to write the resultant data to.
   * @param name the non-null name of the class to transform.
   * @param xmlTypePrefix prefix to add to the class name (helps prevent name
   * clashes for names like string, int etc.); not used if null.
   * @throws IOException
   */
  private StringBuilder annotateClass(StringBuilder builder, File file,
          String name, String xmlTypePrefix) throws IOException {
    String pattern = "public class " + name;
    // rename the xml type to be lower char for 1st char:
    String data = "\nimport javax.xml.bind.annotation.XmlType;\n\n"
            + "@XmlType(name=\""
            + Character.toLowerCase(name.charAt(0))
            + name.substring(1) + "\")\n" + pattern;
    if (xmlTypePrefix != null) {
      data = "\nimport javax.xml.bind.annotation.XmlType;\n\n"
              + "@XmlType(name=\"" + xmlTypePrefix + ""
              + Character.toLowerCase(name.charAt(0))
              + name.substring(1) + "\")\n" + pattern;
    }

    builder = new StringBuilder(builder.toString().replace(pattern, data));
    this.writeFileData(file, builder.toString());
    return builder;
  }

  /**
   * Change all instances of interface XmlAnySimpleType to it's implementation.
   * 
   * Not a clean solution.
   * 
   * @param file the file to modify.
   * @param builder contents of the file to perform replacements on.
   * @throws IOException if the file could not be read or does not exist.
   */
  private StringBuilder adaptXmlAnySimpleType(File file, StringBuilder builder)
          throws IOException {

    String annotation = "@XmlElement(type=org.apache.xmlbeans.impl.values.XmlAnySimpleTypeImpl.class)";
    String xmlPattern = "org.apache.xmlbeans.XmlAnySimpleType get";
    if (builder.toString().contains(xmlPattern)) {
      builder = new StringBuilder(builder.toString().replace(xmlPattern, annotation + "\n" + xmlPattern));
      this.writeFileData(file, builder.toString());
    }
    xmlPattern = "org.apache.xmlbeans.XmlAnySimpleType add";
    if (builder.toString().contains(xmlPattern)) {
      builder = new StringBuilder(builder.toString().replace(xmlPattern, annotation + "\n" + xmlPattern));
      this.writeFileData(file, builder.toString());
    }
    return builder;
  }
  
  /**
   * Add necessary annotations to the interface. This involves:
   * 
   * - adding necessary 
   * - creation of an XML Adapter helper class;
   * - adding annotations to all inner classes and enums;
   * - modification of XmlAnySimpleType get/add methods to use concrete
   * implementations
   * 
   * @param fileInterface the file to modify.
   * @param dirIface the directory where the file is located.
   * @param packageName the name of the package where the interface resides.
   * @param packageNameImpl the name of the package where the implementing
   * class resides.
   * @throws IOException if the file could not be read or does not exist.
   */
  private void annotateInterface(File fileInterface, File dirIface,
          String packageName, String packageNameImpl) throws IOException {
    
        BufferedReader reader = new BufferedReader(new FileReader(fileInterface));
        StringBuilder builder = new StringBuilder();
        int read = -1;
        while ((read = reader.read()) != -1) {
          builder.append((char) read);
        }
        String fileName = FilenameUtils.removeExtension(fileInterface.getName());
        // check that file is interface
        int startIfacePos = builder.indexOf("public interface " + fileName);
        if (startIfacePos < 0) {
          System.out.println("[*] ignoring non-interface "
                  + fileInterface.getName() + ", " + fileName);
          return;
        }
        String implFilename = fileName + this.extension + ".java";
        String abstractFilename = "XmlAdapter" + implFilename;
        if (!this.suffixed) {
          abstractFilename = "XmlAdapter" + this.extension + fileName;
          implFilename = this.extension + fileName + ".java";
        }
        /* Here, the XmlJavaTypeAdapter is an auto-generated class (by this
         * process) that references a marshalling class to bind the interface
         * to a concrete implementation; we also give a (hopefully unique) name
         * via the XmlType annotation: */
        String data = "\nimport javax.xml.bind.annotation.XmlType;\n"
                + "import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;\n\n"
                + "@XmlJavaTypeAdapter("
                + FilenameUtils.removeExtension(abstractFilename) + ".class)\n"
                + "@XmlType(name=\""
                + Character.toLowerCase(fileName.charAt(0))
                + fileName.substring(1) + "\")";
        // Xml Any Simple element causes problems - find out if it's in this interface:
        boolean containsXmlElement = false;
        String xmlPattern = "org.apache.xmlbeans.XmlAnySimpleType get";
        if (builder.toString().contains(xmlPattern)) {
          containsXmlElement = true;
        }
        xmlPattern = "org.apache.xmlbeans.XmlAnySimpleType add";
        if (builder.toString().contains(xmlPattern)) {
          containsXmlElement = true;
        }
        // find out if it's got enums - we need to name each enum differently:
        boolean containsEnum = false;
        if (builder.toString().contains("static final class Enum extends org.apache.xmlbeans.")) {
          containsEnum = true;
        }
        if (builder.toString().contains("@XmlJavaTypeAdapter")) {
          // ^ dumb check! TODO make more elegant
          System.out.println(fileName + " already contains relevant annotation.");
        } else {
          // need to add some imports:
          if (containsEnum) {
            data = "\nimport javax.xml.bind.annotation.XmlType;\n" + data;
          }
          if (containsXmlElement) {
            data = "\nimport javax.xml.bind.annotation.XmlElement;\n" + data;
          }
          // Now finally write the new class definition in to the file:
          String newContents = builder.toString().replace("public interface " + fileName,
                  data + "\n" + "public interface " + fileName);
          builder = new StringBuilder(newContents);
          this.writeFileData(fileInterface, newContents);
          System.out.println("Updated " + fileInterface.getName() + " with appropriate XML annotations and import statements.");
        }
        /* find all enums of StringEnumAbstractBase and add annotations - each
         * enum in the class needs a unique name; in this case, we create a
         * prefix and append an index for each enum in the class: */
        String patternEnum = "static final class Enum extends org.apache.xmlbeans.StringEnumAbstractBase";
        boolean enums = false;
        int enumCount = 0;
        // do a 'blat' replace - all enums have same name at this point (if >1 enums):
        builder = new StringBuilder(builder.toString().replace(patternEnum,
                "@XmlType(name=\""
                + FilenameUtils.removeExtension(fileInterface.getName())
                + "Enum_IDX" + "\", namespace=\"http://openeyes.org\")\n"
                + patternEnum));
        
        int enumIndex = builder.toString().lastIndexOf("Enum_IDX");
        // now perform replacements based on index:
        while (enumIndex > -1) {
          enums = true;
          builder = new StringBuilder(builder.replace(enumIndex, enumIndex + "Enum_IDX".length(), "Enum_" + (enumCount++)));
          enumIndex = builder.toString().lastIndexOf("Enum_IDX", enumIndex - 1);
        }
        if (enums) {
          this.writeFileData(fileInterface, builder.toString());
        }
        /* EVERY interface requires an abstract class to bind the interface to
         * a class for marshalling purposes - create the data based
         * on the intreface name and the impl - this is the XmlJavaTypeAdapter
         * reference in the header of the interface:
         */
        String abstractFileData = this.formatXmlAdapter(packageName,
                packageNameImpl, fileName);
        File abstractFile = new File(dirIface, abstractFilename);
        if (!abstractFile.exists()) {
          // create the new abstract file
          this.writeFileData(abstractFile, abstractFileData);
          System.out.println("[*] Creation of new file in "
                  + abstractFile.getAbsolutePath() + " succeeded");

        } else {
          System.out.println(abstractFilename + " already exists; leaving it.");
        }
        /* The interface XmlAnySimpleType for add/get methods barfs:
         * So we're going to replace all occurrences of the interface
         * with the concrete implementation:
         */
        builder = this.adaptXmlAnySimpleType(fileInterface, builder);
        // Each interface /might/ have sub-interfaces - let's check to see if there are:
        StringBuilder copyStr = new StringBuilder(builder);
        // all sub-interfaces are not newline-based - they'll be indented with spaces:
        final String str = " public interface ";
        boolean updated = false;
        int index = builder.length() - 1;
        while ((index = copyStr.toString().lastIndexOf(str, index - 1)) > -1
                && index > startIfacePos) {
          if (index > -1) {
            updated = true;
            int spaceChar = copyStr.toString().indexOf(" ", index
                    + str.length());
            String subinterface = copyStr.toString().substring(index
                    + str.length(), spaceChar);
            // hack TODO need to get this to work for multiple configurations
            String prefix = null;

            for (Iterator<String> it = this.subInterfaces.keySet().iterator(); it.hasNext();) {
              String subclass = it.next();
              /* You need knowledge of which subinterfaces contain their own
               * subinterfaces - this is performed with the -u/--sub-interfaces
               * option. If the following condition holds, then we've got
               * a situation where the interface depth looks something like
               * A->B->C (in terms of inner classes); this means that new
               * abstract binding classes must be created catering for the
               * depth of classes:
               */
              if (fileName.endsWith(subclass) && this.subInterfaces.get(subclass).contains(subinterface)) {
                prefix = fileName.substring(0, fileName.length() - "Document".length());
                String subinterfaceImpl = prefix + "Impl." + subinterface + "Impl";
                String abstractFileName = "XmlAdapter" + (fileName + "."
                        + subinterfaceImpl).replace(".", "_");
                copyStr = new StringBuilder(copyStr.toString().replace(" public interface " + subinterface,
                        "@XmlJavaTypeAdapter(" + abstractFileName + ".class)\n"
                        + " public interface " + subinterface));
                subinterface = prefix + "." + subinterface;
                System.out.println(abstractFileName + " Updating subinterface " + subinterface);
                this.writeFileData(new File(dirIface, abstractFileName + ".java"),
                        this.formatXmlAdapter(packageName,
                        packageNameImpl, fileName, "." + subinterface, "." + subinterfaceImpl));
              } else {
                // Otherwise, it's just a standard sub-interface of the main interface
                // - which requires a different kind of abstract binding class:
                String abstractFileName = "XmlAdapter" + (fileName + "."
                        + subinterface).replace(".", "_") + "Impl";
                copyStr = new StringBuilder(copyStr.toString().replace(" public interface " + subinterface,
                        "@XmlJavaTypeAdapter(" + abstractFileName + ".class)\n"
                        + " public interface " + subinterface));
                System.out.println("Updating subinterface: " + subinterface
                        + " with XmlJavaTypeAdapter " + abstractFileName);
                this.writeFileData(new File(dirIface, abstractFileName + ".java"),
                        this.formatXmlAdapter(packageName,
                        packageNameImpl, fileName, "." + subinterface, "." + subinterface + "Impl"));
              }
            }
          }
        }
        if (updated) {
          this.writeFileData(fileInterface, copyStr.toString());
        }
  }

  /**
   * Write the file data out.
   * @param f the file to write to.
   * @param data the data to write to the file.
   * @throws IOException if the file could not be read or does not exist.
   */
  private void writeFileData(File f, String data) throws IOException {
    FileWriter fw = new FileWriter(f);
    fw.write(data);
    fw.close();
  }

  /**
   * Creates a new XML Adapter for the specified named interface and package
   * details. This method is used to create XML adapters for interfaces that
   * contain an interface that contains an interface and the child class needs
   * to be mapped to a class.
   * 
   * @param packageName the name of the package where the interface resides.
   * @param packageNameImpl the name of the package where the implementation
   * resides.
   * @param name the name of the interface that contains the hierarchy of
   * classes.
   * @param subclass the child of the interface.
   * @param subclassImpl name of the subclass implementation.
   * @return data containing the class information.
   */
  private String formatXmlAdapter(String packageName, String packageNameImpl,
          String name, String subclass, String subclassImpl) {
    String data = "";
    if (subclass == null) {
      subclass = "";
    }
    if (subclassImpl == null) {
      subclassImpl = subclass;
    }
    data += "package " + packageName + ";";
    data += "\nimport javax.xml.bind.annotation.adapters.XmlAdapter;";
    data += "\nimport " + packageNameImpl + "." + name
            + this.extension + ";";

    data += "\nclass XmlAdapter" + (name + subclassImpl).replace(".", "_")
            + " extends XmlAdapter<" + name + "Impl" + subclassImpl
            + ", " + name + subclass + "> {";

    data += "\n\t@Override";
    data += "\n\tpublic " + name + subclass + " unmarshal(" + name + "Impl"
            + subclassImpl + " v) {";
    data += "\n\t\treturn v;";
    data += "\n\t}";

    data += "\n\t@Override";
    data += "\n\tpublic " + name + "Impl" + subclassImpl + " marshal(" + name + subclass + " v) {";
    data += "\n\t\treturn (" + name + "Impl" + subclassImpl + ") v;";
    data += "\n\t}";
    data += "\n}\n";
    return data;
  }

  /**
   * Creates a new XML Adapter for the specified named interface and package
   * details. This method is used to create XML adapters for the specified
   * interface.
   * 
   * @param packageName the name of the package where the interface resides.
   * @param packageNameImpl the name of the package where the implementation
   * resides.
   * @param name the name of the interface to create the adapter for.
   * @return data containing the class information.
   */
  private String formatXmlAdapter(String packageName, String packageNameImpl,
          String name) {
    String data = "";
    data += "package " + packageName + ";";

    data += "\nimport javax.xml.bind.annotation.adapters.XmlAdapter;";
    data += "\nimport " + packageNameImpl + "." + name
            + this.extension + ";";

    data += "\nclass XmlAdapter" + name.replace(".", "_") + "Impl extends XmlAdapter<" + name + "Impl, " + name + "> {";

    data += "\n\t@Override";
    data += "\n\tpublic " + name + " unmarshal(" + name + "Impl v) {";
    data += "\n\t\treturn v;";
    data += "\n\t}";

    data += "\n\t@Override";
    data += "\n\tpublic " + name + "Impl marshal(" + name + " v) {";
    data += "\n\t\treturn (" + name + "Impl) v;";
    data += "\n\t}";
    data += "\n}\n";
    return data;
  }

  /**
   * 
   */
  private class JavaSourceFileFilter implements FileFilter {

    /**
     * 
     * @param pathname
     * @return 
     */
    public boolean accept(File pathname) {
      return pathname.getName().endsWith(".java");
    }
  }
}

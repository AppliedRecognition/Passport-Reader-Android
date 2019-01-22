import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class AppliedRecDoclet {

    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    public static boolean start(RootDoc doc) {
        String[][] options = doc.options();
        String htmlSrcPath = "";
        String destPath = "";
        for (String[] option : options) {
            if (option[0].equals("-htmlsrc")) {
                htmlSrcPath = option[1];
                System.out.println("HTML source path set to "+htmlSrcPath);
            } else if (option[0].equals("-destpath")) {
                destPath = option[1];
                System.out.println("Destination path set to "+destPath);
            } else {
                System.out.println("Option "+option[0]+(option.length > 1 ? " value "+option[1] : ""));
            }
        }
        ClassDoc[] classes = doc.classes();
        PrintWriter writer;
        try {
            ArrayList<String> normalClasses = new ArrayList<String>();
            ArrayList<String> interfaces = new ArrayList<String>();
            ArrayList<String> enums = new ArrayList<String>();
            String classList = "<aside>";
            for (int i = 0; i < classes.length; ++i) {
                if (classes[i].tags("version").length == 0 || classes[i].tags("suppressdoc").length > 0) {
                    continue;
                }
                String link = "<a href=\""+classes[i].qualifiedName()+".html\">"+classes[i].name()+"</a>";
                if (classes[i].tags("deprecated").length > 0) {
                    link = "<span class=\"deprecated\">"+classes[i].name()+"</span> (deprecated)";
                }
                if (classes[i].isInterface()) {
                    interfaces.add(link);
                } else if (classes[i].isEnum()) {
                    enums.add(link);
                } else {
                    normalClasses.add(link);
                }
            }
            if (!normalClasses.isEmpty()) {
                classList += "<h2>Classes</h2><ul>";
                for (String cls : normalClasses) {
                    classList += "<li>"+cls+"</li>";
                }
                classList += "</ul>";
            }

            if (!interfaces.isEmpty()) {
                classList += "<h2>Interfaces</h2><ul>";
                for (String cls : interfaces) {
                    classList += "<li>"+cls+"</li>";
                }
                classList += "</ul>";
            }

            if (!enums.isEmpty()) {
                classList += "<h2>Enums</h2><ul>";
                for (String cls : enums) {
                    classList += "<li>"+cls+"</li>";
                }
                classList += "</ul>";
            }
            classList += "</aside>";
            if (destPath == null || destPath.isEmpty()) {
                doc.printError("Destination path not set");
                return false;
            }
            if (htmlSrcPath == null || htmlSrcPath.isEmpty()) {
                doc.printError("HTML source path is not set");
                return false;
            }
            File destDir = new File(destPath);
            if (!destDir.exists() && !destDir.mkdirs()) {
                doc.printError("Destination folder "+destDir.getPath()+" cannot be created");
                return false;
            }
            File srcDir = new File(htmlSrcPath);
            if (!srcDir.exists()) {
                doc.printError("HTML source folder "+srcDir.getPath()+" does not exist");
                return false;
            }
            writer = new PrintWriter(new File(destDir,"index.html").getPath());
            String header = readFile(new File(srcDir,"header.html").getPath());
            String footer = readFile(new File(srcDir,"footer.html").getPath());
//			String intro = readFile(new File(srcDir,"intro.html").getPath());
            writer.println(header);
            writer.println(classList);
//			writer.println(intro);
            writer.println(footer);
            writer.close();
            String style = readFile(new File(srcDir,"style.css").getPath());
            writer = new PrintWriter(new File(destDir,"style.css").getPath());
            writer.println(style);
            writer.close();
            for (int i = 0; i < classes.length; ++i) {
                String classVersion = classes[i].tags("version").length > 0 ? classes[i].tags("version")[0].text() : "";
                writer = new PrintWriter(new File(destDir,classes[i].qualifiedName()+".html").getPath(), "UTF-8");
                writer.println(header);
                writer.println(classList);
                writer.println(footer);
                printHtml(writer, classes[i].name(), "h1");

                Tag[] classInlineTags = classes[i].inlineTags();
                if (classInlineTags != null && classInlineTags.length > 0) {
                    writer.println("<section>");
                    printHtml(writer, parseInlineTags(classInlineTags), "h4");
                    writer.println("</section>");
                }

                printHtml(writer, "Package "+classes[i].containingPackage().name(), "h4");
                printHtml(writer, classes[i].modifiers()+" "+(classes[i].isEnum() ? "enum " : (classes[i].isInterface() ? "" : "class "))+" "+classes[i].name(), "code");
                printSuperClass(writer, classes[i]);
                Type[] interfaceTypes = classes[i].interfaceTypes();
                if (interfaceTypes.length > 0) {
                    printHtml(writer, "Implements", "h2");
                    writer.println("<ul class=\"ancestry\">");
                    for (Type interfaceType : interfaceTypes) {
                        printHtml(writer, interfaceType.qualifiedTypeName(), "li");
                    }
                    writer.println("</ul>");
                }

                ConstructorDoc[] constructors = classes[i].constructors();
                if (constructors.length > 0) {
                    int constructorCount = 0;
                    for (ConstructorDoc constructor : constructors) {
                        if (constructor.tags("suppressdoc").length == 0 && constructor.tags("version").length > 0) {
                            constructorCount ++;
                        }
                    }
                    if (constructorCount > 1) {
                        writer.println("<section><h2>Constructors</h2><dl>");
                    } else if (constructorCount == 1) {
                        writer.println("<section><h2>Constructor</h2><dl>");
                    }
                    if (constructorCount > 0) {
                        for (ConstructorDoc constructor : constructors) {
                            if (constructor.tags("suppressdoc").length > 0 || constructor.tags("version").length == 0) {
                                continue;
                            }
                            String deprecatedClass = "";
                            String deprecated = "";
                            if (constructor.tags("deprecated").length > 0) {
                                deprecatedClass = " class=\"deprecated\"";
                                deprecated = "<h4 class=\"deprecation\">" + parseInlineTags(constructor.tags("deprecated")[0].inlineTags()) + "</h4>";
                            }
                            String version = classVersion;
                            if (constructor.tags("version").length > 0) {
                                version = constructor.tags("version")[0].text();
                            }
                            printHtml(writer, "<h3" + deprecatedClass + "><a name=\"" + constructor.name() + constructor.flatSignature() + "\">" + constructor.name() + "</a></h3><p>Introduced in version " + version + "</p>" + deprecated, "dt");
                            writer.println("<dd>");
                            printHtml(writer, constructor.modifiers() + " " + constructor.name() + constructor.flatSignature(), "code");
                            printHtml(writer, parseInlineTags(constructor.inlineTags()), "p");
                            Parameter[] params = constructor.parameters();
                            if (params.length > 0) {
                                ParamTag[] paramTags = constructor.paramTags();
                                printHtml(writer, "Parameters", "h4");
                                writer.println("<dl>");
                                for (Parameter param : params) {
                                    printHtml(writer, param.name() + " <code>" + typeLink(param.type(), classes[i].containingPackage().name()) + "</code>", "dt");
                                    for (ParamTag tag : paramTags) {
                                        if (tag.parameterName().equals(param.name())) {
                                            printHtml(writer, tag.parameterComment(), "dd");
                                        }
                                    }
                                }
                                writer.println("</dl>");
                            }
                            Type[] thrownExceptionTypes = constructor.thrownExceptionTypes();
                            if (thrownExceptionTypes.length > 0) {
                                printHtml(writer, "Throws", "h4");
                                writer.println("<dl>");
                                ThrowsTag[] throwsTags = constructor.throwsTags();
                                for (Type exc : thrownExceptionTypes) {
                                    printHtml(writer, "<code>" + exc.qualifiedTypeName() + "</code>", "dt");
                                    for (ThrowsTag tag : throwsTags) {
                                        if (tag.exceptionType().equals(exc)) {
                                            printHtml(writer, tag.exceptionComment(), "dd");
                                        }
                                    }
                                }
                                writer.println("</dl>");
                            }
                            writer.println(seeAlso(constructor));
                            writer.println("</dd>");
                        }
                        writer.println("</dl></section>");
                    }
                }
                MethodDoc[] methods = classes[i].methods();
                if (methods.length > 0 && !classes[i].isEnum()) {
                    int methodCount = 0;
                    for (MethodDoc method : methods) {
                        if (method.tags("suppressdoc").length == 0 && method.tags("version").length > 0) {
                            methodCount ++;
                        }
                    }
                    if (methodCount > 0) {
                        writer.println("<section><h2>Methods</h2><dl>");
                        for (MethodDoc method : methods) {
                            if (method.tags("suppressdoc").length > 0 || method.tags("version").length == 0) {
                                continue;
                            }
                            String deprecatedClass = "";
                            String deprecated = "";
                            if (method.tags("deprecated").length > 0) {
                                deprecatedClass = " class=\"deprecated\"";
                                deprecated = "<h4 class=\"deprecation\">" + parseInlineTags(method.tags("deprecated")[0].inlineTags()) + "</h4>";
                            }
                            String version = classVersion;
                            if (method.tags("version").length > 0) {
                                version = method.tags("version")[0].text();
                            }
                            printHtml(writer, "<h3" + deprecatedClass + "><a name=\"" + method.name() + method.flatSignature() + "\">" + method.name() + "</a></h3><p>Introduced in version " + version + "</p>" + deprecated, "dt");
                            writer.println("<dd>");
                            printHtml(writer, method.modifiers() + " " + method.name() + method.flatSignature(), "code");
                            printHtml(writer, parseInlineTags(method.inlineTags()), "p");
                            Parameter[] params = method.parameters();
                            if (params.length > 0) {
                                ParamTag[] paramTags = method.paramTags();
                                printHtml(writer, "Parameters", "h4");
                                writer.println("<dl>");
                                for (Parameter param : params) {
                                    printHtml(writer, param.name() + " <code>" + typeLink(param.type(), classes[i].containingPackage().name()) + "</code>", "dt");
                                    for (ParamTag tag : paramTags) {
                                        if (tag.parameterName().equals(param.name())) {
                                            printHtml(writer, tag.parameterComment(), "dd");
                                        }
                                    }
                                }
                                writer.println("</dl>");
                            }
                            Type returnType = method.returnType();
                            if (returnType.typeName() != null && returnType.typeName() != "void") {
                                printHtml(writer, "Returns", "h4");
                                writer.println("<dl>");
                                printHtml(writer, "<code>" + typeLink(returnType, classes[i].containingPackage().name()) + "</code>", "dt");
                                if (method.tags("return").length > 0) {
                                    printHtml(writer, parseInlineTags(method.tags("return")[0].inlineTags()), "dd");
                                } else {

                                }
                                writer.println("</dl>");
                            }
                            Type[] thrownExceptionTypes = method.thrownExceptionTypes();
                            if (thrownExceptionTypes.length > 0) {
                                printHtml(writer, "Throws", "h4");
                                writer.println("<dl>");
                                ThrowsTag[] throwsTags = method.throwsTags();
                                for (Type exc : thrownExceptionTypes) {
                                    printHtml(writer, "<code>" + exc.qualifiedTypeName() + "</code>", "dt");
                                    for (ThrowsTag tag : throwsTags) {
                                        if (tag.exceptionType().equals(exc)) {
                                            printHtml(writer, tag.exceptionComment(), "dd");
                                        }
                                    }
                                }
                                writer.println("</dl>");
                            }
                            writer.println(seeAlso(method));
                            writer.println("</dd>");
                        }
                        writer.println("</dl></section>");
                    }
                }
                FieldDoc[] fields = null;
                if (classes[i].isEnum()) {
                    fields = classes[i].enumConstants();
                } else {
                    fields = classes[i].fields();
                }
                if (fields != null && fields.length > 0) {
                    int fieldCount = 0;
                    for (FieldDoc field : fields) {
                        if (field.tags("suppressdoc").length == 0 && field.tags("version").length > 0) {
                            fieldCount++;
                        }
                    }
                    if (fieldCount > 0) {
                        writer.println("<section><h2>Properties</h2><dl>");
                        for (FieldDoc field :fields) {
                            if (field.tags("suppressdoc").length > 0 || field.tags("version").length == 0) {
                                continue;
                            }
                            String deprecatedClass = "";
                            String deprecated = "";
                            if (field.tags("deprecated").length > 0) {
                                deprecatedClass = " class=\"deprecated\"";
                                deprecated = "<h4 class=\"deprecation\">"+parseInlineTags(field.tags("deprecated")[0].inlineTags())+"</h4>";
                            }
                            String version = classVersion;
                            if (field.tags("version").length > 0) {
                                version = field.tags("version")[0].text();
                            }
                            printHtml(writer, "<h3"+deprecatedClass+"><a name=\""+field.name()+"\">"+field.name()+"</a></h3><p>Introduced in version "+version+"</p>"+deprecated, "dt");
                            writer.println("<dd>");
                            printHtml(writer, field.modifiers()+" "+field.type().typeName()+field.type().dimension()+" "+field.name(),"code");
                            printHtml(writer, parseInlineTags(field.inlineTags()), "p");
                            writer.println(seeAlso(field));
                            writer.println("</dd>");
                        }
                        writer.println("</dl></section>");
                    }
                }
                writer.close();
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;
    }

    private static void printHtml(PrintWriter writer, String text, String tag) {
        if (text != null && !text.isEmpty()) {
            writer.println("<"+tag+">"+text+"</"+tag+">");
        }
    }

    private static String parseInlineTags(Tag[] tags) {
        String text = "";
        for (Tag tag : tags) {
            if (tag.kind().equals("@see")) {
                SeeTag seeTag = (SeeTag) tag;
                String href = "";
                if (seeTag.referencedClass() != null) {
                    href = seeTag.referencedClass().qualifiedName()+".html";
                }
                if (seeTag.referencedMember() != null) {
                    href += "#"+seeTag.referencedMemberName();
                }
                String label = seeTag.label().isEmpty() ? href : seeTag.label();
                text += "<a href=\""+href+"\">"+label+"</a>";
            } else if (tag.name().equals("code")) {
                text += "<code>"+tag.text()+"</code>";
            } else {
                text += tag.text();
            }
        }
        return text;
    }

    private static void printSuperClass(PrintWriter writer, ClassDoc cls) {
        writer.println("<ul class=\"ancestry\"><li>"+cls.qualifiedName());
        if (cls.superclass() != null) {
            printSuperClass(writer, cls.superclass());
        }
        writer.println("</li></ul>");
    }

    private static String seeAlso(Doc doc) {
        String str = "";
        if (doc.seeTags().length > 0) {
            str = "<h4>See also</h4><ul class=\"seeAlso\">";
            for (SeeTag tag : doc.seeTags()) {
                str += "<li>"+parseInlineTags(new SeeTag[]{ tag })+"</li>";
            }
            str += "</ul>";
        }
        return str;
    }

    private static String typeLink(Type type, String restrictToPackageName) {
        String params = "";
        ParameterizedType parameterizedType = type.asParameterizedType();
        if (parameterizedType != null) {
            Type[] typeArgs = parameterizedType.typeArguments();
            if (typeArgs.length > 0) {
                params = "&lt;";
                int i = 0;
                for (Type type1 : typeArgs) {
                    i++;
                    params += type1.simpleTypeName();
                    if (i < typeArgs.length) {
                        params += ", ";
                    }
                }
                params += "&gt;";
            }
        }
        String link = type.qualifiedTypeName()+params+type.dimension();
        if (type.qualifiedTypeName().startsWith(restrictToPackageName)) {
            link = "<a href=\""+type.qualifiedTypeName()+".html\">"+link+"</a>";
        }
        return link;
    }

    public static int optionLength(String option) {
        if (option.equals("-htmlsrc")) {
            return 2;
        } else if (option.equals("-destpath")) {
            return 2;
        } else if (option.equals("-d")) {
            return 2;
        } else if (option.equals("-doctitle")) {
            return 2;
        } else if (option.equals("-windowtitle")) {
            return 2;
        }
        return 0;
    }

    private static String readFile(String filename) {
        String content = null;
        File file = new File(filename);
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader !=null){
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }
}

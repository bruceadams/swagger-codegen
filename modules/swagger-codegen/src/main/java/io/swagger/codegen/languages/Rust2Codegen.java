package io.swagger.codegen.languages;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import io.swagger.codegen.*;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.*;
import io.swagger.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;

public class Rust2Codegen extends DefaultCodegen implements CodegenConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(Rust2Codegen.class);

    protected String apiVersion = "1.0.0";
    protected int serverPort = 8080;
    protected String projectName = "swagger-server";
    protected String apiPath = "rust2";
    protected String packageName;
    protected String packageVersion;
    protected String externCrateName;

    public Rust2Codegen() {
        super();

        // set the output folder here
        outputFolder = "generated-code/rust2";

        /*
         * Models.  You can write model files using the modelTemplateFiles map.
         * if you want to create one template for file, you can do so here.
         * for multiple files for model, just put another entry in the `modelTemplateFiles` with
         * a different extension
         */
        modelTemplateFiles.clear();

        /*
         * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
         * as with models, add multiple entries with different extensions for multiple files per
         * class
         */
        apiTemplateFiles.clear();

        /*
         * Template Location.  This is the location which templates will be read from.  The generator
         * will use the resource stream to attempt to read the templates.
         */
        embeddedTemplateDir = templateDir = "rust2";

        /*
         * Reserved words.  Override this with reserved words specific to your language
         */
        setReservedWordsLowerCase(
            Arrays.asList(
                // From https://doc.rust-lang.org/grammar.html#keywords
                "abstract", "alignof", "as", "become", "box", "break", "const",
                "continue", "crate", "do", "else", "enum", "extern", "false",
                "final", "fn", "for", "if", "impl", "in", "let", "loop", "macro",
                "match", "mod", "move", "mut", "offsetof", "override", "priv",
                "proc", "pub", "pure", "ref", "return", "Self", "self", "sizeof",
                "static", "struct", "super", "trait", "true", "type", "typeof",
                "unsafe", "unsized", "use", "virtual", "where", "while", "yield"
            )
        );

        defaultIncludes = new HashSet<String>(
                Arrays.asList(
                    "map",
                    "array")
                );

        languageSpecificPrimitives = new HashSet<String>(
            Arrays.asList(
                "bool",
                "char",
                "i8",
                "i16",
                "i32",
                "i64",
                "u8",
                "u16",
                "u32",
                "u64",
                "isize",
                "usize",
                "f32",
                "f64",
                "str")
            );

        instantiationTypes.clear();
        instantiationTypes.put("array", "Vec");
        instantiationTypes.put("map", "Map");

        typeMapping.clear();
        typeMapping.put("number", "f64");
        typeMapping.put("integer", "i32");
        typeMapping.put("long", "i64");
        typeMapping.put("float", "f32");
        typeMapping.put("double", "f64");
        typeMapping.put("string", "String");
        typeMapping.put("UUID", "uuid::Uuid");
        typeMapping.put("byte", "u8");
        typeMapping.put("ByteArray", "swagger::ByteArray");
        typeMapping.put("binary", "swagger::ByteArray");
        typeMapping.put("boolean", "bool");
        typeMapping.put("date", "chrono::DateTime<chrono::Utc>");
        typeMapping.put("DateTime", "chrono::DateTime<chrono::Utc>");
        typeMapping.put("password", "String");
        typeMapping.put("File", "Box<Stream<Item=Vec<u8>, Error=Error> + Send>");
        typeMapping.put("file", "Box<Stream<Item=Vec<u8>, Error=Error> + Send>");
        typeMapping.put("array", "Vec");
        typeMapping.put("map", "HashMap");

        importMapping = new HashMap<String, String>();

        cliOptions.clear();
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_NAME,
                                     "Rust crate name (convention: snake_case).")
                       .defaultValue("swagger_client"));
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_VERSION,
                                     "Rust crate version.")
                       .defaultValue("1.0.0"));

        /*
         * Additional Properties.  These values can be passed to the templates and
         * are available in models, apis, and supporting files
         */
        additionalProperties.put("apiVersion", apiVersion);
        additionalProperties.put("serverPort", serverPort);
        additionalProperties.put("apiPath", apiPath);

        /*
         * Supporting Files.  You can write single files for the generator with the
         * entire object tree available.  If the input file has a suffix of `.mustache
         * it will be processed by the template engine.  Otherwise, it will be copied
         */
        supportingFiles.add(new SupportingFile("swagger.mustache", "api", "swagger.yaml"));
        supportingFiles.add(new SupportingFile("Cargo.mustache", "", "Cargo.toml"));
        supportingFiles.add(new SupportingFile("cargo-config", ".cargo", "config"));
        supportingFiles.add(new SupportingFile("gitignore", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("lib.mustache", "src", "lib.rs"));
        supportingFiles.add(new SupportingFile("models.mustache", "src", "models.rs"));
        supportingFiles.add(new SupportingFile("server.mustache", "src", "server.rs"));
        supportingFiles.add(new SupportingFile("client.mustache", "src", "client.rs"));
        supportingFiles.add(new SupportingFile("example-server.mustache", "examples", "server.rs"));
        supportingFiles.add(new SupportingFile("example-client.mustache", "examples", "client.rs"));
        supportingFiles.add(new SupportingFile("example-server_lib.mustache", "examples/server_lib", "mod.rs"));
        supportingFiles.add(new SupportingFile("example-ca.pem", "examples", "ca.pem"));
        supportingFiles.add(new SupportingFile("example-server-chain.pem", "examples", "server-chain.pem"));
        supportingFiles.add(new SupportingFile("example-server-key.pem", "examples", "server-key.pem"));
        writeOptional(outputFolder, new SupportingFile("README.mustache", "", "README.md"));
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            setPackageName((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME));
        }
        else {
            setPackageName("swagger_client");
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)) {
            setPackageVersion((String) additionalProperties.get(CodegenConstants.PACKAGE_VERSION));
        }
        else {
            setPackageVersion("1.0.0");
        }

        additionalProperties.put(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put(CodegenConstants.PACKAGE_VERSION, packageVersion);
        additionalProperties.put("externCrateName", externCrateName);
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;

        // Also set the extern crate name, which has any '-' replace with a '_'.
        this.externCrateName = packageName.replace('-', '_');
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    @Override
    public String apiPackage() {
        return apiPath;
    }

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see io.swagger.codegen.CodegenType
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -l flag.
     *
     * @return the friendly name for the generator
     */
    @Override
    public String getName() {
        return "rust2";
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    @Override
    public String getHelp() {
        return "Generates a Rust client/server library using the swagger-codegen project.";
    }

    @Override
    public void preprocessSwagger(Swagger swagger) {
        Info info = swagger.getInfo();
        List versionComponents = new ArrayList(Arrays.asList(info.getVersion().split("[.]")));
        if (versionComponents.size() < 1) {
            versionComponents.add("1");
        }
        while (versionComponents.size() < 3) {
            versionComponents.add("0");
        }
        info.setVersion(StringUtils.join(versionComponents, "."));
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "default";
        }
        return underscore(name);
    }

    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
     * those terms here.  This logic is only called if a variable matches the reseved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        return "_" + name;  // add an underscore to the name
    }

    /**
     * Location to write api files.  You can use the apiPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String toModelName(String name) {
        // camelize the model name
        // phone_number => PhoneNumber
        String camelizedName = camelize(toModelFilename(name));

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(camelizedName)) {
            camelizedName = "Model" + camelizedName;
            LOGGER.warn(camelizedName + " (reserved word) cannot be used as model name. Renamed to " + camelizedName);
        }

        // model name starts with number
        else if (name.matches("^\\d.*")) {
            // e.g. 200Response => Model200Response (after camelize)
            camelizedName = "Model" + camelizedName;
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + camelizedName);
        }

        return camelizedName;

    }

    @Override
    public String toParamName(String name) {
        return underscore(super.toParamName(name));
    }

    @Override
    public String toVarName(String name) {
        String sanitizedName = super.sanitizeName(name);
        // for reserved word or word starting with number, append _
        if (isReservedWord(sanitizedName) || sanitizedName.matches("^\\d.*")) {
            sanitizedName = escapeReservedWord(sanitizedName);
        }

        return underscore(sanitizedName);
    }

    @Override
    public String toOperationId(String operationId) {
        // method name cannot use reserved keyword, e.g. return
        if (isReservedWord(operationId)) {
            LOGGER.warn(operationId + " (reserved word) cannot be used as method name. Renamed to " + camelize(sanitizeName("call_" + operationId)));
            operationId = "call_" + operationId;
        }

        return camelize(operationId);
    }

    @Override
    public String toModelFilename(String name) {
        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name;
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix;
        }

        name = sanitizeName(name);

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model name. Renamed to " + camelize("model_" + name));
            name = "model_" + name; // e.g. return => ModelReturn (after camelize)
        }

        return underscore(name);
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        return sanitizeName(camelize(property.name)) + "Enum";
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        String var = null;
        if (value.length() == 0) {
            var = "EMPTY";
        }

        // for symbol, e.g. $, #
        else if (getSymbolName(value) != null) {
            var = getSymbolName(value).toUpperCase();
        }

        // number
        else if ("Integer".equals(datatype) || "Long".equals(datatype) ||
                "Float".equals(datatype) || "Double".equals(datatype)) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            var = varName;
        }

        // string
        var = value.replaceAll("\\W+", "_").toUpperCase();
        if (var.matches("\\d.*")) {
            var = "_" + var;
        } else {
            var = sanitizeName(var);
        }
        return var;
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        if ("Integer".equals(datatype) || "Long".equals(datatype) ||
                "Float".equals(datatype) || "Double".equals(datatype)) {
            return value;
        } else {
            return "\"" + escapeText(value) + "\"";
        }
    }

    @Override
    public String toApiFilename(String name) {
        // replace - with _ e.g. created-at => created_at
        name = name.replaceAll("-", "_"); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.

        // e.g. PetApi.go => pet_api.go
        return underscore(name);
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, Map<String, Model> definitions, Swagger swagger) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, definitions, swagger);
        op.vendorExtensions.put("operation_id", underscore(op.operationId));
        op.vendorExtensions.put("path", op.path.replace("{", ":").replace("}", ""));
        op.vendorExtensions.put("HttpMethod", Character.toUpperCase(op.httpMethod.charAt(0)) + op.httpMethod.substring(1).toLowerCase());
        op.vendorExtensions.put("httpmethod", op.httpMethod.toLowerCase());
        for (CodegenParameter param : op.allParams) {
            String example = null;

            if (param.isString) {
                if (param.dataFormat != null && param.dataFormat.equals("byte")) {
                    param.vendorExtensions.put("formatString", "\\\"{:?}\\\"");
                    example = "swagger::ByteArray(\"" + ((param.example != null) ? param.example : "") + "\".to_string().into_bytes())";
                } else {
                    param.vendorExtensions.put("formatString", "\\\"{}\\\"");
                    example = "\"" + ((param.example != null) ? param.example : "") + "\".to_string()";
                }
            } else if (param.isPrimitiveType) {
                if ((param.isByteArray) ||
                    (param.isBinary)) {
                    // Binary primitive types don't implement `Display`.
                    param.vendorExtensions.put("formatString", "{:?}");
                    example = "swagger::ByteArray(Vec::from(\"" + ((param.example != null) ? param.example : "") + "\"))";
                } else {
                    param.vendorExtensions.put("formatString", "{}");
                    example = (param.example != null) ? param.example : "";
                }
            } else if (param.isListContainer) {
                param.vendorExtensions.put("formatString", "{:?}");
                example = (param.example != null) ? param.example : "&Vec::new()";
            } else if (param.isFile) {
                param.vendorExtensions.put("formatString", "{:?}");
                op.vendorExtensions.put("hasFile", true);
                additionalProperties.put("apiHasFile", true);
                example = "Box::new(stream::once(Ok(b\"hello\".to_vec()))) as Box<Stream<Item=_, Error=_> + Send>";
            } else {
                param.vendorExtensions.put("formatString", "{:?}");
                if (param.example != null) {
                    example = "serde_json::from_str::<" + param.dataType + ">(\"" + param.example + "\").expect(\"Failed to parse JSON example\")";
                }
            }

            if (param.required) {
                if (example != null) {
                    param.vendorExtensions.put("example", example);
                } else {
                    // If we don't have an example that we can provide, we need to disable the client example, as it won't build.
                    param.vendorExtensions.put("example", "???");
                    op.vendorExtensions.put("noClientExample", Boolean.TRUE);
                }
            } else if ((param.dataFormat != null)&&((param.dataFormat.equals("date-time")) || (param.dataFormat.equals("date")))) {
                param.vendorExtensions.put("formatString", "{:?}");
                param.vendorExtensions.put("example", "None");
            } else {
                // Not required, so override the format string and example
                param.vendorExtensions.put("formatString", "{:?}");
                if (param.isFile) {
                    // Optional file types are wrapped in a future
                    param.vendorExtensions.put("example", (example != null) ? "Box::new(future::ok(Some(" + example + "))) as Box<Future<Item=_, Error=_> + Send>" : "None");
                } else {
                    param.vendorExtensions.put("example", (example != null) ? "Some(" + example + ")" : "None");
                }
            }
        }
        for (CodegenParameter param : op.headerParams) {
            // Give header params a name in camel case. CodegenParameters don't have a nameInCamelCase property.
            param.vendorExtensions.put("typeName", toModelName(param.baseName));
        }
        for (CodegenResponse rsp : op.responses) {
            rsp.message = camelize(rsp.message.split("[^A-Za-z ]")[0].replace(" ", "_"));
            rsp.vendorExtensions.put("uppercase_operation_id", underscore(op.operationId).toUpperCase());
            rsp.vendorExtensions.put("uppercase_message", underscore(rsp.message).toUpperCase());
            for (CodegenProperty header : rsp.headers) {
                header.nameInCamelCase = toModelName(header.baseName);
            }
        }
        for (CodegenProperty header : op.responseHeaders) {
            header.nameInCamelCase = toModelName(header.baseName);
        }

        List<String> produces = new ArrayList<String>();
        if (operation.getProduces() != null) {
            if (operation.getProduces().size() > 0) {
                // use produces defined in the operation
                produces = operation.getProduces();
            }
        } else if (swagger != null && swagger.getProduces() != null && swagger.getProduces().size() > 0) {
            // use produces defined globally
            produces = swagger.getProduces();
            LOGGER.debug("No produces defined in operation. Using global produces (" + swagger.getProduces() + ") for " + op.operationId);
        }

        if (produces != null && !produces.isEmpty()) {
            List<Map<String, String>> c = new ArrayList<Map<String, String>>();
            for (String key : produces) {
                Map<String, String> mediaType = new HashMap<String, String>();

                String result = this.processMimeType(key);
                if ("application/json".equals(key)) {
                    mediaType.put("hasJson", "true");
                } else {
                    mediaType.put("mediaType", result);
                }

                c.add(mediaType);
            }
            op.produces = c;
            op.hasProduces = true;
        }

        return op;
    }

    @Override
    public boolean isDataTypeFile(final String dataType) {
        return dataType != null && dataType.equals(typeMapping.get("File").toString());
    }

    @Override
    public String getTypeDeclaration(Property p) {
        if (p instanceof ArrayProperty) {
            ArrayProperty ap = (ArrayProperty) p;
            Property inner = ap.getItems();
            String innerType = getTypeDeclaration(inner);
            StringBuilder typeDeclaration = new StringBuilder(typeMapping.get("array")).append("<");
            if (inner instanceof RefProperty) {
                typeDeclaration.append("models::");
            }
            typeDeclaration.append(innerType).append(">");
            return typeDeclaration.toString();
        } else if (p instanceof MapProperty) {
            MapProperty mp = (MapProperty) p;
            Property inner = mp.getAdditionalProperties();
            String innerType = getTypeDeclaration(inner);
            StringBuilder typeDeclaration = new StringBuilder(typeMapping.get("map")).append("<").append(typeMapping.get("string")).append(", ");
            if (inner instanceof RefProperty) {
                typeDeclaration.append("models::");
            }
            typeDeclaration.append(innerType).append(">");
            return typeDeclaration.toString();
        } else if (p instanceof RefProperty) {
            String datatype;
            try {
                RefProperty r = (RefProperty) p;
                datatype = r.get$ref();
                if (datatype.indexOf("#/definitions/") == 0) {
                    datatype = toModelName(datatype.substring("#/definitions/".length()));
                }
            } catch (Exception e) {
                LOGGER.warn("Error obtaining the datatype from RefProperty:" + p + ". Datatype default to Object");
                datatype = "Object";
                LOGGER.error(e.getMessage(), e);
            }
            return datatype;
        } else if (p instanceof FileProperty) {
            return typeMapping.get("File").toString();
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public CodegenParameter fromParameter(Parameter param, Set<String> imports) {
        CodegenParameter parameter = super.fromParameter(param, imports);
        if(param instanceof BodyParameter) {
            BodyParameter bp = (BodyParameter) param;
            Model model = bp.getSchema();
            if (model instanceof RefModel) {
                String name = ((RefModel) model).getSimpleRef();
                name = toModelName(name);
                name = "models::" + getTypeDeclaration(name);

                parameter.baseType = name;
                parameter.dataType = name;
            }
        }
        return parameter;
    }


    @Override
    public CodegenProperty fromProperty(String name, Property p) {
        CodegenProperty property = super.fromProperty(name, p);
        if (p instanceof RefProperty) {
            property.datatype = "models::" + property.datatype;
        }
        return property;
    }

    @Override
    public String toInstantiationType(Property p) {
        if (p instanceof ArrayProperty) {
            ArrayProperty ap = (ArrayProperty) p;
            Property inner = ap.getItems();
            return instantiationTypes.get("array") + "<" + getSwaggerType(inner) + ">";
        } else if (p instanceof MapProperty) {
            MapProperty mp = (MapProperty) p;
            Property inner = mp.getAdditionalProperties();
            return instantiationTypes.get("map") + "<" + typeMapping.get("string") + ", " + getSwaggerType(inner) + ">";
        } else {
            return null;
        }
    }

    @Override
    public CodegenModel fromModel(String name, Model model) {
        return fromModel(name, model, null);
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {
        CodegenModel mdl = super.fromModel(name, model, allDefinitions);
        if (model instanceof ModelImpl) {
             ModelImpl modelImpl = (ModelImpl) model;
             mdl.dataType = typeMapping.get(modelImpl.getType());
        }
        if (model instanceof ArrayModel) {
            mdl.arrayModelType = toModelName(mdl.arrayModelType);
        }
        return mdl;
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Swagger swagger = (Swagger)objs.get("swagger");
        if(swagger != null) {
            try {
                objs.put("swagger-yaml", Yaml.mapper().writeValueAsString(swagger));
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    @Override
    public String toDefaultValue(Property p) {
        if (p instanceof StringProperty) {
            StringProperty dp = (StringProperty) p;
            if (dp.getDefault() != null) {
                return "\"" + dp.getDefault() + "\".to_string()";
            }
        } else if (p instanceof BooleanProperty) {
            BooleanProperty dp = (BooleanProperty) p;
            if (dp.getDefault() != null) {
                if (dp.getDefault().toString().equalsIgnoreCase("false"))
                    return "false";
                else
                    return "true";
            }
        } else if (p instanceof DoubleProperty) {
            DoubleProperty dp = (DoubleProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof FloatProperty) {
            FloatProperty dp = (FloatProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof IntegerProperty) {
            IntegerProperty dp = (IntegerProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof LongProperty) {
            LongProperty dp = (LongProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        }

        return null;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if(!languageSpecificPrimitives.contains(property.datatype)) {
            // If we use a more qualified model name, then only camelize the actual type, not the qualifier.
            if(property.datatype.contains(":")) {
                int position = property.datatype.lastIndexOf(":");
                property.datatype = property.datatype.substring(0, position) + camelize(property.datatype.substring(position));
            } else {
                property.datatype = camelize(property.datatype, false);
            }
        }

        // Handle custom unsigned integer formats.
        if ("integer".equals(property.baseType)) {
            if ("uint32".equals(property.dataFormat)) {
                property.datatype = "u32";
            } else if ("uint64".equals(property.dataFormat)) {
                property.datatype = "u64";
            }
        }

        property.name = underscore(property.name);

        if (!property.required) {
            property.defaultValue = (property.defaultValue != null) ? "Some(" + property.defaultValue + ")" : "None";
        }
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        return super.postProcessModelsEnum(objs);
    }

    private String processMimeType(String mimeType){
        // Transform mime type into a form that the hyper mime! macro can handle.
        String result = "";

        String[] split_attributes = mimeType.split(";");
        String media = split_attributes[0];
        String[] mediaTypes = media.split("/");

         // Escape quotation marks and special characters to avoid code injection.
        if (mediaTypes.length == 2) {

            if (mediaTypes[0].equals("*")){
                result += "Star";
            } else {
                result += escapeText(escapeQuotationMark(initialCaps(mediaTypes[0])));
            }

            result += "/";

            if (mediaTypes[1].equals("*")) {
                result += "Star";
            } else {
                result += escapeText(escapeQuotationMark(initialCaps(mediaTypes[1])));
            }
        } else {
            LOGGER.error("Failed to parse media type: "
                         + mimeType
                         + ", media types should have exactly one /");
        }

        if (split_attributes.length == 2) {
            String attributes = "";
            String[] attrs = split_attributes[1].split(",");

            for (String attr : attrs) {
                String[] keyValuePair =attr.split("=");
                if (keyValuePair.length == 2) {
                    attributes += "(\""
                                + escapeText(escapeQuotationMark(keyValuePair[0].trim()))
                                + "\")=(\""
                                + escapeText(escapeQuotationMark(keyValuePair[1].trim()))
                                + "\")";
                } else {
                    LOGGER.error("Failed to parse parameter attributes: "
                                 + split_attributes[1]
                                 + ", attributes must be a comma separated list of 'key=value' pairs");
                }
            }
            result += "; " + attributes;
        }

        return result;
    }
}

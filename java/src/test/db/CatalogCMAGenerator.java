/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.db;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;

/**
 * Generates {@link CatalogCMA} from the XML metadata of the CMA application,
 * in java/res/cma, and the tables of the local SQL Server copy.
 * <p>
 * Only the tables and columns that exist in the local schema EXPLMARG_QLT are
 * generated, so that any select built from the catalog can be executed. What
 * would be a difficulty is skipped: foreign keys, possible values, views, the
 * presentation attributes, columns of large object types or unknown domains,
 * and indexes on skipped columns or repeating another index.
 * <p>
 * Run from the project root. It only reads the database.
 *
 * @author Miquel Sas
 */
public class CatalogCMAGenerator {

	/**
	 * The local schema with the CMA tables.
	 */
	private static final String SCHEMA = "EXPLMARG_QLT";
	/**
	 * The directory of the XML files.
	 */
	private static final String DIR = "java/res/cma";
	/**
	 * The file generated.
	 */
	private static final String OUTPUT = "java/src/test/db/CatalogCMA.java";
	/**
	 * The library of domains.
	 */
	private static final String DOMAINS = "Margins_Library_Domains.xml";
	/**
	 * The schema files of the modules, in the order the tables are generated.
	 */
	private static final String[] MODULES = {
			"Margins_Dictionary_DBSchema.xml",
			"Margins_Central_DBSchema.xml",
			"Margins_Local_DBSchema.xml",
			"Budget_Dictionary_DBSchema.xml" };

	/**
	 * A table of the local database: its name and its columns, by name in upper
	 * case, keeping the case of the database.
	 *
	 * @param name    The name.
	 * @param columns The column names by name in upper case, in database order.
	 */
	private record DBTable(String name, Map<String, String> columns) {
	}

	/**
	 * A column to generate.
	 *
	 * @param name        The name, with the case of the database.
	 * @param domain      The domain name.
	 * @param header      The header, or null.
	 * @param label       The label, or null.
	 * @param description The description, or null.
	 */
	private record GenColumn(String name, String domain, String header, String label, String description) {
	}

	/**
	 * A table to generate.
	 *
	 * @param name    The name, with the case of the database.
	 * @param title   The title, or null.
	 * @param module  The module of the XML file.
	 * @param columns The columns.
	 * @param pk      The columns of the primary key, empty if none.
	 * @param indexes The columns of each secondary index.
	 */
	private record GenTable(
			String name,
			String title,
			String module,
			List<GenColumn> columns,
			List<String> pk,
			List<List<String>> indexes) {
	}

	/**
	 * The domains of the library, by name.
	 */
	private final Map<String, Element> domains = new LinkedHashMap<>();
	/**
	 * The tables of the local schema, by name in upper case.
	 */
	private final Map<String, DBTable> dbTables = new LinkedHashMap<>();
	/**
	 * The tables to generate.
	 */
	private final List<GenTable> tables = new ArrayList<>();
	/**
	 * The names of the domains used, to generate only those.
	 */
	private final Set<String> usedDomains = new TreeSet<>();
	/**
	 * Counters of what was skipped, by reason.
	 */
	private final Map<String, Integer> skipped = new LinkedHashMap<>();

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 * @throws Exception If reading or writing fails.
	 */
	public static void main(String[] args) throws Exception {
		CatalogCMAGenerator generator = new CatalogCMAGenerator();
		generator.readDomains();
		generator.readDatabase();
		for (String module : MODULES) {
			generator.readModule(module);
		}
		String code = generator.write();
		Files.writeString(Path.of(OUTPUT), code, StandardCharsets.UTF_8);
		generator.summary();
		System.exit(0);
	}

	/**
	 * Parses an XML file of the directory.
	 *
	 * @param name The file name.
	 * @return The document.
	 * @throws Exception If parsing fails.
	 */
	private static Document parse(String name) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		return factory.newDocumentBuilder().parse(new File(DIR, name));
	}

	/**
	 * Returns the child elements of an element with a name.
	 *
	 * @param parent The parent, or null.
	 * @param name   The name of the children.
	 * @return The children, empty if the parent is null.
	 */
	private static List<Element> children(Element parent, String name) {
		List<Element> list = new ArrayList<>();
		if (parent == null) {
			return list;
		}
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
				list.add((Element) node);
			}
		}
		return list;
	}

	/**
	 * Returns the first child element with a name.
	 *
	 * @param parent The parent.
	 * @param name   The name.
	 * @return The child, or null.
	 */
	private static Element child(Element parent, String name) {
		List<Element> list = children(parent, name);
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * Returns an attribute, or null if missing or empty.
	 *
	 * @param element The element.
	 * @param name    The attribute name.
	 * @return The value or null.
	 */
	private static String attr(Element element, String name) {
		String value = element.getAttribute(name);
		return value.isEmpty() ? null : value;
	}

	/**
	 * Counts a skipped item.
	 *
	 * @param reason The reason.
	 */
	private void skip(String reason) {
		skipped.merge(reason, 1, Integer::sum);
	}

	/**
	 * Reads the domains of the library.
	 *
	 * @throws Exception If parsing fails.
	 */
	private void readDomains() throws Exception {
		Element root = parse(DOMAINS).getDocumentElement();
		for (Element domain : children(root, "domain")) {
			domains.put(domain.getAttribute("name"), domain);
		}
	}

	/**
	 * Reads the tables and columns of the local schema.
	 *
	 * @throws Exception If the database access fails.
	 */
	private void readDatabase() throws Exception {
		DBEngineSQLServer db = new DBEngineSQLServer();
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS ");
		sql.append("WHERE TABLE_SCHEMA = '");
		sql.append(SCHEMA);
		sql.append("' ORDER BY TABLE_NAME, ORDINAL_POSITION");
		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			while (rs.next()) {
				String table = rs.getString(1);
				String column = rs.getString(2);
				DBTable dbTable = dbTables.computeIfAbsent(upper(table), k -> new DBTable(table, new LinkedHashMap<>()));
				dbTable.columns().put(upper(column), column);
			}
		}
	}

	/**
	 * Returns a name in upper case.
	 *
	 * @param name The name.
	 * @return The name in upper case.
	 */
	private static String upper(String name) {
		return name.toUpperCase(Locale.ROOT);
	}

	/**
	 * Returns the type in the catalog of a domain, or null if it is skipped.
	 *
	 * @param domain The domain element.
	 * @return The name of the Types constant, or null.
	 */
	private static String typeOf(Element domain) {
		String type = domain.getAttribute("type");
		String result = switch (type) {
			case "VARCHAR":
				yield attr(domain, "length") == null ? null : "STRING";
			case "DECIMAL":
				yield "DECIMAL";
			case "DATE":
				yield "DATE";
			case "TIMESTAMP":
				yield "DATETIME";
			case "BIT":
				yield "BOOLEAN";
			default:
				yield null;
		};
		return result;
	}

	/**
	 * Reads the tables of a module that exist in the local schema.
	 *
	 * @param module The file of the module.
	 * @throws Exception If parsing fails.
	 */
	private void readModule(String module) throws Exception {
		Element root = parse(module).getDocumentElement();
		String moduleName = root.getAttribute("name");
		Element tablesElement = child(child(root, "module"), "tables");
		for (Element table : children(tablesElement, "table")) {
			DBTable dbTable = dbTables.get(upper(table.getAttribute("name")));
			if (dbTable == null) {
				skip("tables not in the local schema");
				continue;
			}
			readTable(table, dbTable, moduleName);
		}
	}

	/**
	 * Reads a table.
	 *
	 * @param table   The table element.
	 * @param dbTable The table of the database.
	 * @param module  The module name.
	 */
	private void readTable(Element table, DBTable dbTable, String module) {
		List<GenColumn> columns = new ArrayList<>();
		Map<String, GenColumn> byName = new LinkedHashMap<>();
		for (Element column : children(child(table, "columns"), "column")) {
			if ("false".equals(column.getAttribute("persistent"))) {
				skip("columns not persistent");
				continue;
			}
			String domainName = column.getAttribute("domain");
			Element domain = domains.get(domainName);
			if (domain == null || typeOf(domain) == null) {
				skip("columns of unknown domains or skipped types");
				continue;
			}
			String xmlName = attr(column, "name") != null ? column.getAttribute("name") : domainName;
			String name = dbTable.columns().get(upper(xmlName));
			if (name == null) {
				skip("columns not in the local table");
				continue;
			}
			if (byName.containsKey(upper(name))) {
				skip("columns repeated");
				continue;
			}
			GenColumn genColumn = new GenColumn(
					name,
					domainName,
					attr(column, "header"),
					attr(column, "label"),
					attr(column, "title"));
			columns.add(genColumn);
			byName.put(upper(name), genColumn);
			usedDomains.add(domainName);
		}
		if (columns.isEmpty()) {
			skip("tables without columns");
			return;
		}
		List<String> pk = new ArrayList<>();
		List<List<String>> indexes = new ArrayList<>();
		List<Set<String>> seen = new ArrayList<>();
		for (Element index : children(child(table, "indexes"), "index")) {
			List<String> segments = indexColumns(index, byName);
			if (segments == null) {
				skip("indexes on skipped columns");
				continue;
			}
			Set<String> set = new TreeSet<>(segments);
			if (seen.contains(set)) {
				skip("indexes repeating another one");
				continue;
			}
			boolean primary = "true".equals(index.getAttribute("primary-key"));
			if (primary && pk.isEmpty()) {
				pk.addAll(segments);
			} else {
				indexes.add(segments);
			}
			seen.add(set);
		}
		tables.add(new GenTable(dbTable.name(), attr(table, "title"), module, columns, pk, indexes));
	}

	/**
	 * Returns the columns of an index, with the names of the generated columns.
	 *
	 * @param index  The index element.
	 * @param byName The generated columns by name in upper case.
	 * @return The column names, or null if a column is skipped, repeated or too
	 *         long to be indexed, or the index has no columns.
	 */
	private List<String> indexColumns(Element index, Map<String, GenColumn> byName) {
		List<String> names = new ArrayList<>();
		for (Element segment : children(index, "segment")) {
			GenColumn column = byName.get(upper(segment.getAttribute("column")));
			if (column == null || names.contains(column.name())) {
				return null;
			}
			Element domain = domains.get(column.domain());
			String length = attr(domain, "length");
			if ("STRING".equals(typeOf(domain)) && Integer.parseInt(length) > 2000) {
				return null;
			}
			names.add(column.name());
		}
		return names.isEmpty() ? null : names;
	}

	/**
	 * Returns a Java string literal, or null.
	 *
	 * @param text The text, or null.
	 * @return The literal.
	 */
	private static String quote(String text) {
		if (text == null) {
			return "null";
		}
		StringBuilder b = new StringBuilder();
		b.append('"');
		for (char c : text.toCharArray()) {
			switch (c) {
				case '"':
					b.append("\\\"");
					break;
				case '\\':
					b.append("\\\\");
					break;
				default:
					if (c < 32 || c > 126) {
						b.append(String.format("\\u%04x", (int) c));
					} else {
						b.append(c);
					}
			}
		}
		b.append('"');
		return b.toString();
	}

	/**
	 * Appends a line made of parts, followed by a new line.
	 *
	 * @param b     The builder.
	 * @param parts The parts.
	 */
	private static void line(StringBuilder b, String... parts) {
		for (String part : parts) {
			b.append(part);
		}
		b.append("\n");
	}

	/**
	 * Returns the name of the method that defines a table, the table name kept
	 * as is after an underscore, as in table_WC_VADJ_ART.
	 *
	 * @param table The table.
	 * @return The method name.
	 */
	private static String methodName(GenTable table) {
		StringBuilder b = new StringBuilder();
		b.append("table_");
		b.append(table.name());
		return b.toString();
	}

	/**
	 * Writes the catalog class.
	 *
	 * @return The source code.
	 */
	private String write() {
		StringBuilder b = new StringBuilder();
		writeHeader(b);
		writeDomains(b);
		for (GenTable table : tables) {
			writeTable(b, table);
		}
		writeFooter(b);
		return b.toString();
	}

	/**
	 * Writes the header of the class: license, package, imports, fields,
	 * constructor and the provider methods.
	 *
	 * @param b The builder.
	 */
	private void writeHeader(StringBuilder b) {
		line(b, "/*");
		line(b, " * Copyright (c) 2026 Miquel Sas");
		line(b, " *");
		line(b, " * Licensed under the Apache License, Version 2.0 (the \"License\");");
		line(b, " * you may not use this file except in compliance with the License.");
		line(b, " * You may obtain a copy of the License at");
		line(b, " *");
		line(b, " *     http://www.apache.org/licenses/LICENSE-2.0");
		line(b, " *");
		line(b, " * Unless required by applicable law or agreed to in writing, software");
		line(b, " * distributed under the License is distributed on an \"AS IS\" BASIS,");
		line(b, " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.");
		line(b, " * See the License for the specific language governing permissions and");
		line(b, " * limitations under the License.");
		line(b, " */");
		line(b);
		line(b, "package test.db;");
		line(b);
		line(b, "import java.util.LinkedHashMap;");
		line(b, "import java.util.List;");
		line(b, "import java.util.Map;");
		line(b);
		line(b, "import msfx.lib.md.CatalogProvider;");
		line(b, "import msfx.lib.md.Column;");
		line(b, "import msfx.lib.md.Domain;");
		line(b, "import msfx.lib.md.Index;");
		line(b, "import msfx.lib.md.Table;");
		line(b, "import msfx.lib.md.Types;");
		line(b);
		line(b, "/**");
		line(b, " * Catalog provider of the CMA tables present in the local schema ", SCHEMA, ",");
		line(b, " * with the domains they use. Generated by {@link CatalogCMAGenerator} from the");
		line(b, " * XML metadata in java/res/cma and the local database: do not edit, generate");
		line(b, " * it again.");
		line(b, " * <p>");
		line(b, " * Tables: ", String.valueOf(tables.size()), ". Foreign keys, possible values and views are not");
		line(b, " * generated.");
		line(b, " *");
		line(b, " * @author Miquel Sas");
		line(b, " */");
		line(b, "public class CatalogCMA implements CatalogProvider {");
		line(b);
		line(b, "\t/**");
		line(b, "\t * The schema of the tables.");
		line(b, "\t */");
		line(b, "\tpublic static final String SCHEMA = ", quote(SCHEMA), ";");
		line(b);
		line(b, "\t/**");
		line(b, "\t * Domains, by name.");
		line(b, "\t */");
		line(b, "\tprivate final Map<String, Domain> domains = new LinkedHashMap<>();");
		line(b, "\t/**");
		line(b, "\t * Tables, by name, in the order of the modules.");
		line(b, "\t */");
		line(b, "\tprivate final Map<String, Table> tables = new LinkedHashMap<>();");
		line(b);
		line(b, "\t/**");
		line(b, "\t * Constructor. Defines the domains and the tables.");
		line(b, "\t */");
		line(b, "\tpublic CatalogCMA() {");
		line(b, "\t\tdefineDomains();");
		for (GenTable table : tables) {
			line(b, "\t\t", methodName(table), "();");
		}
		line(b, "\t}");
		line(b);
		line(b, "\t@Override");
		line(b, "\tpublic Domain getDomain(String name) {");
		line(b, "\t\treturn domains.get(name);");
		line(b, "\t}");
		line(b);
		line(b, "\t@Override");
		line(b, "\tpublic Table getTable(String schema, String name) {");
		line(b, "\t\treturn SCHEMA.equals(schema) ? tables.get(name) : null;");
		line(b, "\t}");
		line(b);
		line(b, "\t@Override");
		line(b, "\tpublic List<String> getAllDomainNames() {");
		line(b, "\t\treturn List.copyOf(domains.keySet());");
		line(b, "\t}");
		line(b);
		line(b, "\t@Override");
		line(b, "\tpublic List<String> getAllTableNames(String schema) {");
		line(b, "\t\treturn SCHEMA.equals(schema) ? List.copyOf(tables.keySet()) : List.of();");
		line(b, "\t}");
		line(b);
		line(b, "\t/**");
		line(b, "\t * Defines a domain.");
		line(b, "\t *");
		line(b, "\t * @param name        The name.");
		line(b, "\t * @param type        The type.");
		line(b, "\t * @param length      The length, -1 if not applicable.");
		line(b, "\t * @param decimals    The decimals, -1 if not applicable.");
		line(b, "\t * @param header      The header, or null.");
		line(b, "\t * @param label       The label, or null.");
		line(b, "\t * @param description The description, or null.");
		line(b, "\t */");
		line(b, "\tprivate void domain(String name, Types type, int length, int decimals, String header, String label, String description) {");
		line(b, "\t\tDomain domain = new Domain(name, type, length, decimals);");
		line(b, "\t\tif (header != null) {");
		line(b, "\t\t\tdomain.setHeader(header);");
		line(b, "\t\t}");
		line(b, "\t\tif (label != null) {");
		line(b, "\t\t\tdomain.setLabel(label);");
		line(b, "\t\t}");
		line(b, "\t\tif (description != null) {");
		line(b, "\t\t\tdomain.setDescription(description);");
		line(b, "\t\t}");
		line(b, "\t\tdomains.put(name, domain);");
		line(b, "\t}");
		line(b);
		line(b, "\t/**");
		line(b, "\t * Adds a column to a table, from a domain.");
		line(b, "\t *");
		line(b, "\t * @param table       The table.");
		line(b, "\t * @param name        The column name.");
		line(b, "\t * @param domain      The domain name.");
		line(b, "\t * @param header      The header that overrides the domain one, or null.");
		line(b, "\t * @param label       The label that overrides the domain one, or null.");
		line(b, "\t * @param description The description that overrides the domain one, or");
		line(b, "\t *                    null.");
		line(b, "\t */");
		line(b, "\tprivate void column(Table table, String name, String domain, String header, String label, String description) {");
		line(b, "\t\tColumn column = new Column(name, domains.get(domain));");
		line(b, "\t\tif (header != null) {");
		line(b, "\t\t\tcolumn.setHeader(header);");
		line(b, "\t\t}");
		line(b, "\t\tif (label != null) {");
		line(b, "\t\t\tcolumn.setLabel(label);");
		line(b, "\t\t}");
		line(b, "\t\tif (description != null) {");
		line(b, "\t\t\tcolumn.setDescription(description);");
		line(b, "\t\t}");
		line(b, "\t\ttable.columns().add(column);");
		line(b, "\t}");
		line(b);
		line(b, "\t/**");
		line(b, "\t * Sets the primary key of a table. Its columns of nullable types become not");
		line(b, "\t * nullable.");
		line(b, "\t *");
		line(b, "\t * @param table   The table.");
		line(b, "\t * @param columns The column names.");
		line(b, "\t */");
		line(b, "\tprivate static void primaryKey(Table table, String... columns) {");
		line(b, "\t\tIndex index = new Index(table);");
		line(b, "\t\tindex.addColumns(columns);");
		line(b, "\t\tfor (Column column : index.getColumns()) {");
		line(b, "\t\t\tcolumn.setNullable(false);");
		line(b, "\t\t}");
		line(b, "\t\ttable.setPrimaryKey(index);");
		line(b, "\t}");
		line(b);
		line(b, "\t/**");
		line(b, "\t * Adds a secondary index to a table.");
		line(b, "\t *");
		line(b, "\t * @param table   The table.");
		line(b, "\t * @param columns The column names.");
		line(b, "\t */");
		line(b, "\tprivate static void index(Table table, String... columns) {");
		line(b, "\t\tIndex index = new Index(table);");
		line(b, "\t\tindex.addColumns(columns);");
		line(b, "\t\ttable.addIndex(index);");
		line(b, "\t}");
	}

	/**
	 * Writes the method that defines the domains used.
	 *
	 * @param b The builder.
	 */
	private void writeDomains(StringBuilder b) {
		line(b);
		line(b, "\t/**");
		line(b, "\t * Defines the domains used by the tables.");
		line(b, "\t */");
		line(b, "\tprivate void defineDomains() {");
		for (String name : domains.keySet()) {
			if (!usedDomains.contains(name)) {
				continue;
			}
			Element domain = domains.get(name);
			String type = typeOf(domain);
			String length = "-1";
			String decimals = "-1";
			if (type.equals("STRING")) {
				length = domain.getAttribute("length");
			} else if (type.equals("DECIMAL")) {
				length = attr(domain, "length") == null ? "-1" : domain.getAttribute("length");
				decimals = attr(domain, "decimals") == null ? "0" : domain.getAttribute("decimals");
			}
			line(b, "\t\tdomain(", quote(name), ", Types.", type, ", ", length, ", ", decimals, ", ",
					quote(attr(domain, "header")), ", ", quote(attr(domain, "label")), ", ",
					quote(attr(domain, "title")), ");");
		}
		line(b, "\t}");
	}

	/**
	 * Writes the method that defines a table.
	 *
	 * @param b     The builder.
	 * @param table The table.
	 */
	private void writeTable(StringBuilder b, GenTable table) {
		line(b);
		line(b, "\t/**");
		if (table.title() == null) {
			line(b, "\t * Defines the table ", table.name(), " of ", table.module(), ".");
		} else {
			line(b, "\t * Defines the table ", table.name(), " of ", table.module(), ": ",
					table.title().replace("*/", "* /").replace("&", "&amp;").replace("<", "&lt;"), ".");
		}
		line(b, "\t */");
		line(b, "\tprivate void ", methodName(table), "() {");
		line(b, "\t\tTable table = new Table(", quote(table.name()), ");");
		if (table.title() != null) {
			line(b, "\t\ttable.setTitle(", quote(table.title()), ");");
		}
		for (GenColumn column : table.columns()) {
			line(b, "\t\tcolumn(table, ", quote(column.name()), ", ", quote(column.domain()), ", ",
					quote(column.header()), ", ", quote(column.label()), ", ", quote(column.description()), ");");
		}
		if (!table.pk().isEmpty()) {
			line(b, "\t\tprimaryKey(table, ", names(table.pk()), ");");
		}
		for (List<String> index : table.indexes()) {
			line(b, "\t\tindex(table, ", names(index), ");");
		}
		line(b, "\t\ttables.put(table.getName(), table);");
		line(b, "\t}");
	}

	/**
	 * Returns a list of names as string literals separated by commas.
	 *
	 * @param names The names.
	 * @return The literals.
	 */
	private static String names(List<String> names) {
		StringBuilder b = new StringBuilder();
		for (String name : names) {
			if (!b.isEmpty()) {
				b.append(", ");
			}
			b.append(quote(name));
		}
		return b.toString();
	}

	/**
	 * Writes the end of the class.
	 *
	 * @param b The builder.
	 */
	private void writeFooter(StringBuilder b) {
		line(b, "}");
	}

	/**
	 * Prints what was generated and skipped.
	 */
	private void summary() {
		int columns = 0;
		int pks = 0;
		int indexes = 0;
		Set<String> modules = new LinkedHashSet<>();
		for (GenTable table : tables) {
			columns += table.columns().size();
			pks += table.pk().isEmpty() ? 0 : 1;
			indexes += table.indexes().size();
			modules.add(table.module());
		}
		StringBuilder b = new StringBuilder();
		b.append("Generated ");
		b.append(OUTPUT);
		b.append("\n  tables: ");
		b.append(tables.size());
		b.append(" of modules ");
		b.append(modules);
		b.append("\n  columns: ");
		b.append(columns);
		b.append(", domains: ");
		b.append(usedDomains.size());
		b.append(", primary keys: ");
		b.append(pks);
		b.append(", secondary indexes: ");
		b.append(indexes);
		b.append("\n  local tables not in the XML: ");
		Set<String> generated = new TreeSet<>();
		for (GenTable table : tables) {
			generated.add(upper(table.name()));
		}
		int notInXml = 0;
		for (String name : dbTables.keySet()) {
			if (!generated.contains(name)) {
				notInXml++;
			}
		}
		b.append(notInXml);
		b.append("\n  skipped:");
		for (Map.Entry<String, Integer> entry : skipped.entrySet()) {
			b.append("\n    ");
			b.append(entry.getKey());
			b.append(": ");
			b.append(entry.getValue());
		}
		System.out.println(b);
	}
}

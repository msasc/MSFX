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

package msfx.lib.db.query.sa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Column;

/**
 * Writes a validated expression in the SQL of a database.
 * <ul>
 * <li>Columns are written alias.column, both quoted, with the names as
 * registered, whatever the case they were written with. Columns written
 * without alias get the alias of their table.</li>
 * <li>Literals and the values of the placeholders are written by
 * {@link DBEngine#getLiteralSQL(msfx.lib.md.Value)}.</li>
 * <li>Functions are translated by {@link DBEngine#getFunctionSQL(Function, List)}
 * and concatenations by {@link DBEngine#getConcatSQL(List)}.</li>
 * <li>IS [NOT] NULL of a column goes through {@link DBEngine#isNull} and
 * {@link DBEngine#isNotNull}, that take its type into account.</li>
 * <li>Parentheses are kept where they were written. No others are needed:
 * the tree has the precedence of SQL, and translations that use operators are
 * enclosed in parentheses by the engine.</li>
 * </ul>
 *
 * @author Miquel Sas
 */
public class Writer {

	/**
	 * The database engine.
	 */
	private final DBEngine db;
	/**
	 * The validated expression.
	 */
	private final Analysis analysis;

	/**
	 * Constructor.
	 *
	 * @param db       The database engine.
	 * @param analysis The validated expression.
	 * @throws NullPointerException If any argument is null.
	 */
	public Writer(DBEngine db, Analysis analysis) {
		this.db = Objects.requireNonNull(db, "Database engine can not be null");
		this.analysis = Objects.requireNonNull(analysis, "Analysis can not be null");
	}

	/**
	 * Writes the expression.
	 *
	 * @return The SQL.
	 */
	public String write() {
		return write(analysis.getRoot());
	}

	/**
	 * Writes a node and its children.
	 *
	 * @param node The node.
	 * @return The SQL.
	 */
	private String write(Node node) {
		List<Node> children = node.getChildren();
		String sql = switch (node.getKind()) {
			case COLUMN: {
				StringBuilder b = new StringBuilder();
				b.append(db.quote(analysis.getAlias(node)));
				b.append(".");
				b.append(db.quote(analysis.getColumn(node).getName()));
				yield b.toString();
			}
			case LITERAL:
				yield db.getLiteralSQL(node.getValue());
			case PARAMETER:
				yield db.getLiteralSQL(analysis.getValues().get(node.getIndex()));
			case STAR:
				yield "*";
			case CALL: {
				List<String> arguments = writeAll(children);
				if (node.isDistinct()) {
					StringBuilder b = new StringBuilder();
					b.append("DISTINCT ");
					b.append(arguments.get(0));
					arguments.set(0, b.toString());
				}
				yield db.getFunctionSQL(node.getFunction(), arguments);
			}
			case UNARY: {
				String operand = write(children.get(0));
				StringBuilder b = new StringBuilder();
				b.append(node.getOperator().getSymbol());
				/* A space avoids -- from a negative operand, a comment in SQL. */
				if (operand.startsWith("-") || operand.startsWith("+")) {
					b.append(" ");
				}
				b.append(operand);
				yield b.toString();
			}
			case BINARY: {
				if (node.getOperator() == Operator.CONCAT) {
					List<Node> operands = new ArrayList<>();
					collectConcat(node, operands);
					yield db.getConcatSQL(writeAll(operands));
				}
				StringBuilder b = new StringBuilder();
				b.append(write(children.get(0)));
				b.append(" ");
				b.append(node.getOperator().getSymbol());
				b.append(" ");
				b.append(write(children.get(1)));
				yield b.toString();
			}
			case AND:
				yield String.join(" AND ", writeAll(children));
			case OR:
				yield String.join(" OR ", writeAll(children));
			case NOT: {
				StringBuilder b = new StringBuilder();
				b.append("NOT ");
				b.append(write(children.get(0)));
				yield b.toString();
			}
			case IS_NULL:
				yield writeIsNull(node);
			case IN: {
				StringBuilder b = new StringBuilder();
				b.append(write(children.get(0)));
				b.append(node.isNegated() ? " NOT IN (" : " IN (");
				b.append(String.join(", ", writeAll(children.subList(1, children.size()))));
				b.append(")");
				yield b.toString();
			}
			case BETWEEN: {
				StringBuilder b = new StringBuilder();
				b.append(write(children.get(0)));
				b.append(node.isNegated() ? " NOT BETWEEN " : " BETWEEN ");
				b.append(write(children.get(1)));
				b.append(" AND ");
				b.append(write(children.get(2)));
				yield b.toString();
			}
			case LIKE: {
				StringBuilder b = new StringBuilder();
				b.append(write(children.get(0)));
				b.append(node.isNegated() ? " NOT LIKE " : " LIKE ");
				b.append(write(children.get(1)));
				yield b.toString();
			}
			case CASE: {
				StringBuilder b = new StringBuilder();
				b.append("CASE");
				int last = children.size() - 1;
				for (int i = 0; i < last; i += 2) {
					b.append(" WHEN ");
					b.append(write(children.get(i)));
					b.append(" THEN ");
					b.append(write(children.get(i + 1)));
				}
				b.append(" ELSE ");
				b.append(write(children.get(last)));
				b.append(" END");
				yield b.toString();
			}
			case GROUP: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(write(children.get(0)));
				b.append(")");
				yield b.toString();
			}
		};
		return sql;
	}

	/**
	 * Writes an IS [NOT] NULL. When the operand is a column, its type is known
	 * and the engine writes the check, that also takes empty strings as null.
	 * Other operands have no known type yet, and are written IS [NOT] NULL.
	 *
	 * @param node The IS_NULL node.
	 * @return The SQL.
	 */
	private String writeIsNull(Node node) {
		Node operand = node.getChildren().get(0);
		while (operand.getKind() == Node.Kind.GROUP) {
			operand = operand.getChildren().get(0);
		}
		String sql = write(node.getChildren().get(0));
		if (operand.getKind() == Node.Kind.COLUMN) {
			Column column = analysis.getColumn(operand);
			return node.isNegated() ? db.isNotNull(sql, column.getType()) : db.isNull(sql, column.getType());
		}
		StringBuilder b = new StringBuilder();
		b.append(sql);
		b.append(node.isNegated() ? " IS NOT NULL" : " IS NULL");
		return b.toString();
	}

	/**
	 * Collects the operands of a chain of concatenations, a || b || c, that the
	 * parser builds from the left.
	 *
	 * @param node     The node.
	 * @param operands The list to fill.
	 */
	private static void collectConcat(Node node, List<Node> operands) {
		if (node.getKind() == Node.Kind.BINARY && node.getOperator() == Operator.CONCAT) {
			collectConcat(node.getChildren().get(0), operands);
			operands.add(node.getChildren().get(1));
		} else {
			operands.add(node);
		}
	}

	/**
	 * Writes a list of nodes.
	 *
	 * @param nodes The nodes.
	 * @return The SQL of each one, in a modifiable list.
	 */
	private List<String> writeAll(List<Node> nodes) {
		List<String> list = new ArrayList<>();
		for (Node node : nodes) {
			list.add(write(node));
		}
		return list;
	}
}

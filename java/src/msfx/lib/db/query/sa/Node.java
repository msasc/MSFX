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

import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * A node of the tree of a hand-written expression, as built by the
 * {@link Parser}.
 * <p>
 * Nodes are created only by the parser, through the static method of their
 * kind, that sets the fields of that kind and leaves the rest null, false or
 * empty.
 *
 * @author Miquel Sas
 */
public class Node {

	/**
	 * Kinds of nodes.
	 */
	public enum Kind {
		/** A column, with an optional table alias. */
		COLUMN,
		/** A number or a string written in the expression. */
		LITERAL,
		/** A placeholder <code>?</code>, replaced by its value when written. */
		PARAMETER,
		/** The <code>*</code> of <code>COUNT(*)</code>. */
		STAR,
		/** A function call, the arguments being the children. */
		CALL,
		/** A sign, <code>+</code> or <code>-</code>, applied to the child. */
		UNARY,
		/** An arithmetic, concatenation or comparison operator. */
		BINARY,
		/** Logical conjunction of the children, two or more. */
		AND,
		/** Logical disjunction of the children, two or more. */
		OR,
		/** Logical negation of the child. */
		NOT,
		/** <code>IS [NOT] NULL</code> of the child. */
		IS_NULL,
		/** <code>[NOT] IN</code>: the first child, tested against the rest. */
		IN,
		/** <code>[NOT] BETWEEN</code>: the value, the low and the high limits. */
		BETWEEN,
		/** <code>[NOT] LIKE</code>: the value and the pattern. */
		LIKE,
		/**
		 * A searched <code>CASE</code>: pairs of condition and result, followed by
		 * the <code>ELSE</code> result.
		 */
		CASE,
		/** An expression or a condition between parentheses. */
		GROUP
	}

	/**
	 * Returns a column.
	 *
	 * @param position The position.
	 * @param alias    The table alias, or null if not written.
	 * @param name     The column name.
	 * @return The node.
	 */
	static Node column(int position, String alias, String name) {
		return new Node(Kind.COLUMN, position, List.of(), null, null, alias, name, null, 0, false, false);
	}

	/**
	 * Returns a literal.
	 *
	 * @param position The position.
	 * @param value    The value, a number or a string.
	 * @return The node.
	 */
	static Node literal(int position, Value value) {
		return new Node(Kind.LITERAL, position, List.of(), null, null, null, null, value, 0, false, false);
	}

	/**
	 * Returns a placeholder.
	 *
	 * @param position The position.
	 * @param index    The index of the placeholder in the expression, from zero,
	 *                 that is also the index of its value.
	 * @return The node.
	 */
	static Node parameter(int position, int index) {
		return new Node(Kind.PARAMETER, position, List.of(), null, null, null, null, null, index, false, false);
	}

	/**
	 * Returns the <code>*</code> of <code>COUNT(*)</code>.
	 *
	 * @param position The position.
	 * @return The node.
	 */
	static Node star(int position) {
		return new Node(Kind.STAR, position, List.of(), null, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns a function call.
	 *
	 * @param position  The position.
	 * @param function  The function.
	 * @param distinct  Whether the argument of an aggregate is preceded by
	 *                  DISTINCT.
	 * @param arguments The arguments.
	 * @return The node.
	 */
	static Node call(int position, Function function, boolean distinct, List<Node> arguments) {
		return new Node(Kind.CALL, position, List.copyOf(arguments), null, function, null, null, null, 0, distinct, false);
	}

	/**
	 * Returns a sign applied to an operand.
	 *
	 * @param position The position.
	 * @param operator The operator, PLUS or MINUS.
	 * @param operand  The operand.
	 * @return The node.
	 */
	static Node unary(int position, Operator operator, Node operand) {
		return new Node(Kind.UNARY, position, List.of(operand), operator, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns a binary operation.
	 *
	 * @param position The position.
	 * @param operator The operator.
	 * @param left     The left operand.
	 * @param right    The right operand.
	 * @return The node.
	 */
	static Node binary(int position, Operator operator, Node left, Node right) {
		return new Node(Kind.BINARY, position, List.of(left, right), operator, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns a logical conjunction.
	 *
	 * @param position   The position.
	 * @param conditions The conditions, two or more.
	 * @return The node.
	 */
	static Node and(int position, List<Node> conditions) {
		return new Node(Kind.AND, position, List.copyOf(conditions), null, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns a logical disjunction.
	 *
	 * @param position   The position.
	 * @param conditions The conditions, two or more.
	 * @return The node.
	 */
	static Node or(int position, List<Node> conditions) {
		return new Node(Kind.OR, position, List.copyOf(conditions), null, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns a logical negation.
	 *
	 * @param position  The position.
	 * @param condition The condition.
	 * @return The node.
	 */
	static Node not(int position, Node condition) {
		return new Node(Kind.NOT, position, List.of(condition), null, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns an <code>IS [NOT] NULL</code>.
	 *
	 * @param position The position.
	 * @param negated  Whether it is IS NOT NULL.
	 * @param operand  The operand.
	 * @return The node.
	 */
	static Node isNull(int position, boolean negated, Node operand) {
		return new Node(Kind.IS_NULL, position, List.of(operand), null, null, null, null, null, 0, false, negated);
	}

	/**
	 * Returns a <code>[NOT] IN</code>.
	 *
	 * @param position The position.
	 * @param negated  Whether it is NOT IN.
	 * @param operand  The operand.
	 * @param values   The list of values, one or more.
	 * @return The node.
	 */
	static Node in(int position, boolean negated, Node operand, List<Node> values) {
		List<Node> children = new ArrayList<>();
		children.add(operand);
		children.addAll(values);
		return new Node(Kind.IN, position, List.copyOf(children), null, null, null, null, null, 0, false, negated);
	}

	/**
	 * Returns a <code>[NOT] BETWEEN</code>.
	 *
	 * @param position The position.
	 * @param negated  Whether it is NOT BETWEEN.
	 * @param operand  The operand.
	 * @param low      The low limit.
	 * @param high     The high limit.
	 * @return The node.
	 */
	static Node between(int position, boolean negated, Node operand, Node low, Node high) {
		return new Node(Kind.BETWEEN, position, List.of(operand, low, high), null, null, null, null, null, 0, false, negated);
	}

	/**
	 * Returns a <code>[NOT] LIKE</code>.
	 *
	 * @param position The position.
	 * @param negated  Whether it is NOT LIKE.
	 * @param operand  The operand.
	 * @param pattern  The pattern.
	 * @return The node.
	 */
	static Node like(int position, boolean negated, Node operand, Node pattern) {
		return new Node(Kind.LIKE, position, List.of(operand, pattern), null, null, null, null, null, 0, false, negated);
	}

	/**
	 * Returns a searched <code>CASE</code>.
	 *
	 * @param position   The position.
	 * @param conditions The conditions of the WHEN branches.
	 * @param results    The results of the THEN branches, one per condition.
	 * @param otherwise  The result of the ELSE.
	 * @return The node.
	 */
	static Node caseOf(int position, List<Node> conditions, List<Node> results, Node otherwise) {
		List<Node> children = new ArrayList<>();
		for (int i = 0; i < conditions.size(); i++) {
			children.add(conditions.get(i));
			children.add(results.get(i));
		}
		children.add(otherwise);
		return new Node(Kind.CASE, position, List.copyOf(children), null, null, null, null, null, 0, false, false);
	}

	/**
	 * Returns an expression or a condition between parentheses.
	 *
	 * @param position The position of the opening parenthesis.
	 * @param content  The content.
	 * @return The node.
	 */
	static Node group(int position, Node content) {
		return new Node(Kind.GROUP, position, List.of(content), null, null, null, null, null, 0, false, false);
	}

	/**
	 * The kind of node.
	 */
	private final Kind kind;
	/**
	 * The position of the first token of the node in the expression, from zero.
	 */
	private final int position;
	/**
	 * The children, in the order given by the kind. Empty if none.
	 */
	private final List<Node> children;
	/**
	 * The operator, only for UNARY and BINARY.
	 */
	private final Operator operator;
	/**
	 * The function, only for CALL.
	 */
	private final Function function;
	/**
	 * The table alias of a COLUMN, null if not written.
	 */
	private final String alias;
	/**
	 * The name of a COLUMN.
	 */
	private final String name;
	/**
	 * The value of a LITERAL.
	 */
	private final Value value;
	/**
	 * The index of a PARAMETER, from zero.
	 */
	private final int index;
	/**
	 * Whether the argument of a CALL is preceded by DISTINCT.
	 */
	private final boolean distinct;
	/**
	 * Whether an IS_NULL, IN, BETWEEN or LIKE is negated.
	 */
	private final boolean negated;

	/**
	 * Constructor, used only by the static methods of each kind.
	 *
	 * @param kind     The kind.
	 * @param position The position.
	 * @param children The children.
	 * @param operator The operator.
	 * @param function The function.
	 * @param alias    The table alias.
	 * @param name     The column name.
	 * @param value    The value.
	 * @param index    The index of the parameter.
	 * @param distinct The DISTINCT flag.
	 * @param negated  The negated flag.
	 */
	private Node(
			Kind kind,
			int position,
			List<Node> children,
			Operator operator,
			Function function,
			String alias,
			String name,
			Value value,
			int index,
			boolean distinct,
			boolean negated) {
		this.kind = kind;
		this.position = position;
		this.children = children;
		this.operator = operator;
		this.function = function;
		this.alias = alias;
		this.name = name;
		this.value = value;
		this.index = index;
		this.distinct = distinct;
		this.negated = negated;
	}

	/**
	 * Returns the kind of node.
	 *
	 * @return The kind.
	 */
	public Kind getKind() {
		return kind;
	}

	/**
	 * Returns the position of the node in the expression.
	 *
	 * @return The position, from zero.
	 */
	public int getPosition() {
		return position;
	}

	/**
	 * Returns the children.
	 *
	 * @return The children, in the order given by the kind, empty if none.
	 */
	public List<Node> getChildren() {
		return children;
	}

	/**
	 * Returns the operator.
	 *
	 * @return The operator, or null if the node is not UNARY or BINARY.
	 */
	public Operator getOperator() {
		return operator;
	}

	/**
	 * Returns the function.
	 *
	 * @return The function, or null if the node is not a CALL.
	 */
	public Function getFunction() {
		return function;
	}

	/**
	 * Returns the table alias of a column.
	 *
	 * @return The alias, or null if not written or not a COLUMN.
	 */
	public String getAlias() {
		return alias;
	}

	/**
	 * Returns the name of a column.
	 *
	 * @return The name, or null if the node is not a COLUMN.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the value of a literal.
	 *
	 * @return The value, or null if the node is not a LITERAL.
	 */
	public Value getValue() {
		return value;
	}

	/**
	 * Returns the index of a parameter.
	 *
	 * @return The index, from zero, meaningful only for a PARAMETER.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * Returns whether the argument of a call is preceded by DISTINCT.
	 *
	 * @return A boolean.
	 */
	public boolean isDistinct() {
		return distinct;
	}

	/**
	 * Returns whether an IS_NULL, IN, BETWEEN or LIKE is negated.
	 *
	 * @return A boolean.
	 */
	public boolean isNegated() {
		return negated;
	}

	/**
	 * Returns whether the node is a condition, true or false, rather than a
	 * value.
	 *
	 * @return A boolean.
	 */
	public boolean isCondition() {
		boolean condition = switch (kind) {
			case COLUMN:
			case LITERAL:
			case PARAMETER:
			case STAR:
			case CALL:
			case UNARY:
			case CASE:
				yield false;
			case BINARY:
				yield operator.isComparison();
			case AND:
			case OR:
			case NOT:
			case IS_NULL:
			case IN:
			case BETWEEN:
			case LIKE:
				yield true;
			case GROUP:
				yield children.get(0).isCondition();
		};
		return condition;
	}

	/**
	 * Returns the node with every operation between parentheses, to read the
	 * precedence in tests and while debugging. A GROUP adds its own
	 * parentheses, so that a group written by hand shows as double.
	 *
	 * @return A string representation.
	 */
	@Override
	public String toString() {
		String text = switch (kind) {
			case COLUMN: {
				StringBuilder b = new StringBuilder();
				if (alias != null) {
					b.append(alias);
					b.append(".");
				}
				b.append(name);
				yield b.toString();
			}
			case LITERAL: {
				StringBuilder b = new StringBuilder();
				if (value.getType() == Types.STRING) {
					b.append("'");
					b.append(value.getString().replace("'", "''"));
					b.append("'");
				} else {
					b.append(value.getDecimal().toPlainString());
				}
				yield b.toString();
			}
			case PARAMETER: {
				StringBuilder b = new StringBuilder();
				b.append("?");
				b.append(index);
				yield b.toString();
			}
			case STAR:
				yield "*";
			case CALL: {
				StringBuilder b = new StringBuilder();
				b.append(function.name());
				b.append("(");
				if (distinct) {
					b.append("DISTINCT ");
				}
				appendList(b, children, ", ");
				b.append(")");
				yield b.toString();
			}
			case UNARY: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(operator.getSymbol());
				b.append(children.get(0));
				b.append(")");
				yield b.toString();
			}
			case BINARY: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(children.get(0));
				b.append(" ");
				b.append(operator.getSymbol());
				b.append(" ");
				b.append(children.get(1));
				b.append(")");
				yield b.toString();
			}
			case AND: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				appendList(b, children, " AND ");
				b.append(")");
				yield b.toString();
			}
			case OR: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				appendList(b, children, " OR ");
				b.append(")");
				yield b.toString();
			}
			case NOT: {
				StringBuilder b = new StringBuilder();
				b.append("(NOT ");
				b.append(children.get(0));
				b.append(")");
				yield b.toString();
			}
			case IS_NULL: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(children.get(0));
				b.append(negated ? " IS NOT NULL" : " IS NULL");
				b.append(")");
				yield b.toString();
			}
			case IN: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(children.get(0));
				b.append(negated ? " NOT IN (" : " IN (");
				appendList(b, children.subList(1, children.size()), ", ");
				b.append("))");
				yield b.toString();
			}
			case BETWEEN: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(children.get(0));
				b.append(negated ? " NOT BETWEEN " : " BETWEEN ");
				b.append(children.get(1));
				b.append(" AND ");
				b.append(children.get(2));
				b.append(")");
				yield b.toString();
			}
			case LIKE: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(children.get(0));
				b.append(negated ? " NOT LIKE " : " LIKE ");
				b.append(children.get(1));
				b.append(")");
				yield b.toString();
			}
			case CASE: {
				StringBuilder b = new StringBuilder();
				b.append("CASE");
				int last = children.size() - 1;
				for (int i = 0; i < last; i += 2) {
					b.append(" WHEN ");
					b.append(children.get(i));
					b.append(" THEN ");
					b.append(children.get(i + 1));
				}
				b.append(" ELSE ");
				b.append(children.get(last));
				b.append(" END");
				yield b.toString();
			}
			case GROUP: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(children.get(0));
				b.append(")");
				yield b.toString();
			}
		};
		return text;
	}

	/**
	 * Appends the nodes separated by the separator.
	 *
	 * @param b         The builder.
	 * @param nodes     The nodes.
	 * @param separator The separator.
	 */
	private static void appendList(StringBuilder b, List<Node> nodes, String separator) {
		for (int i = 0; i < nodes.size(); i++) {
			if (i > 0) {
				b.append(separator);
			}
			b.append(nodes.get(i));
		}
	}
}

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

import java.util.List;

import msfx.lib.md.Value;

/**
 * A node of the tree of a hand-written expression, as built by the
 * {@link Parser}.
 * <p>
 * Nodes are created only through the static method of their kind, that sets
 * the fields of that kind and leaves the rest null, false or empty.
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
	public static Node column(int position, String alias, String name) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns a literal.
	 *
	 * @param position The position.
	 * @param value    The value, a number or a string.
	 * @return The node.
	 */
	public static Node literal(int position, Value value) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns a placeholder.
	 *
	 * @param position The position.
	 * @param index    The index of the placeholder in the expression, from zero,
	 *                 that is also the index of its value.
	 * @return The node.
	 */
	public static Node parameter(int position, int index) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns the <code>*</code> of <code>COUNT(*)</code>.
	 *
	 * @param position The position.
	 * @return The node.
	 */
	public static Node star(int position) {
		throw new UnsupportedOperationException("Not implemented yet");
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
	public static Node call(int position, Function function, boolean distinct, List<Node> arguments) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns a sign applied to an operand.
	 *
	 * @param position The position.
	 * @param operator The operator, PLUS or MINUS.
	 * @param operand  The operand.
	 * @return The node.
	 */
	public static Node unary(int position, Operator operator, Node operand) {
		throw new UnsupportedOperationException("Not implemented yet");
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
	public static Node binary(int position, Operator operator, Node left, Node right) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns a logical conjunction.
	 *
	 * @param position   The position.
	 * @param conditions The conditions, two or more.
	 * @return The node.
	 */
	public static Node and(int position, List<Node> conditions) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns a logical disjunction.
	 *
	 * @param position   The position.
	 * @param conditions The conditions, two or more.
	 * @return The node.
	 */
	public static Node or(int position, List<Node> conditions) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns a logical negation.
	 *
	 * @param position  The position.
	 * @param condition The condition.
	 * @return The node.
	 */
	public static Node not(int position, Node condition) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns an <code>IS [NOT] NULL</code>.
	 *
	 * @param position The position.
	 * @param negated  Whether it is IS NOT NULL.
	 * @param operand  The operand.
	 * @return The node.
	 */
	public static Node isNull(int position, boolean negated, Node operand) {
		throw new UnsupportedOperationException("Not implemented yet");
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
	public static Node in(int position, boolean negated, Node operand, List<Node> values) {
		throw new UnsupportedOperationException("Not implemented yet");
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
	public static Node between(int position, boolean negated, Node operand, Node low, Node high) {
		throw new UnsupportedOperationException("Not implemented yet");
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
	public static Node like(int position, boolean negated, Node operand, Node pattern) {
		throw new UnsupportedOperationException("Not implemented yet");
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
	public static Node caseOf(int position, List<Node> conditions, List<Node> results, Node otherwise) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns an expression or a condition between parentheses.
	 *
	 * @param position The position of the opening parenthesis.
	 * @param content  The content.
	 * @return The node.
	 */
	public static Node group(int position, Node content) {
		throw new UnsupportedOperationException("Not implemented yet");
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
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns the node fully parenthesized, to read in tests and while debugging.
	 *
	 * @return A string representation.
	 */
	@Override
	public String toString() {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}

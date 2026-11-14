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
package msfx.lib.util.function;

/**
 * Functional interfaces that take from 0 to 6 parameters, return a result,
 * and may throw a checked exception. The type parameters follow the order of
 * the signature: the parameters, the result R, and the exception E.
 * <p>
 * The exception is a type parameter. A method that receives one of these
 * interfaces declares E in its own throws clause:
 *
 * <pre>
 * public &lt;R, E extends Exception&gt; List&lt;R&gt; map(Function.P1&lt;Record, R, E&gt; mapper) throws E
 * </pre>
 *
 * When the lambda passed throws no checked exception, E is inferred as
 * RuntimeException and the caller has nothing to catch; when it throws one,
 * E is inferred as that exception and the caller must handle it.
 *
 * @author Miquel Sas
 */
public interface Function {

	/**
	 * Function without parameters.
	 *
	 * @param <R> The type of the result.
	 * @param <E> The exception thrown.
	 */
	@FunctionalInterface
	public interface P0<R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call() throws E;
	}

	/**
	 * Function of 1 parameter.
	 *
	 * @param <T1> The type of the 1st parameter.
	 * @param <R>  The type of the result.
	 * @param <E>  The exception thrown.
	 */
	@FunctionalInterface
	public interface P1<T1, R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @param p1 The 1st parameter.
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call(T1 p1) throws E;
	}

	/**
	 * Function of 2 parameters.
	 *
	 * @param <T1> The type of the 1st parameter.
	 * @param <T2> The type of the 2nd parameter.
	 * @param <R>  The type of the result.
	 * @param <E>  The exception thrown.
	 */
	@FunctionalInterface
	public interface P2<T1, T2, R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @param p1 The 1st parameter.
		 * @param p2 The 2nd parameter.
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call(T1 p1, T2 p2) throws E;
	}

	/**
	 * Function of 3 parameters.
	 *
	 * @param <T1> The type of the 1st parameter.
	 * @param <T2> The type of the 2nd parameter.
	 * @param <T3> The type of the 3rd parameter.
	 * @param <R>  The type of the result.
	 * @param <E>  The exception thrown.
	 */
	@FunctionalInterface
	public interface P3<T1, T2, T3, R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @param p1 The 1st parameter.
		 * @param p2 The 2nd parameter.
		 * @param p3 The 3rd parameter.
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call(T1 p1, T2 p2, T3 p3) throws E;
	}

	/**
	 * Function of 4 parameters.
	 *
	 * @param <T1> The type of the 1st parameter.
	 * @param <T2> The type of the 2nd parameter.
	 * @param <T3> The type of the 3rd parameter.
	 * @param <T4> The type of the 4th parameter.
	 * @param <R>  The type of the result.
	 * @param <E>  The exception thrown.
	 */
	@FunctionalInterface
	public interface P4<T1, T2, T3, T4, R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @param p1 The 1st parameter.
		 * @param p2 The 2nd parameter.
		 * @param p3 The 3rd parameter.
		 * @param p4 The 4th parameter.
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call(T1 p1, T2 p2, T3 p3, T4 p4) throws E;
	}

	/**
	 * Function of 5 parameters.
	 *
	 * @param <T1> The type of the 1st parameter.
	 * @param <T2> The type of the 2nd parameter.
	 * @param <T3> The type of the 3rd parameter.
	 * @param <T4> The type of the 4th parameter.
	 * @param <T5> The type of the 5th parameter.
	 * @param <R>  The type of the result.
	 * @param <E>  The exception thrown.
	 */
	@FunctionalInterface
	public interface P5<T1, T2, T3, T4, T5, R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @param p1 The 1st parameter.
		 * @param p2 The 2nd parameter.
		 * @param p3 The 3rd parameter.
		 * @param p4 The 4th parameter.
		 * @param p5 The 5th parameter.
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call(T1 p1, T2 p2, T3 p3, T4 p4, T5 p5) throws E;
	}

	/**
	 * Function of 6 parameters.
	 *
	 * @param <T1> The type of the 1st parameter.
	 * @param <T2> The type of the 2nd parameter.
	 * @param <T3> The type of the 3rd parameter.
	 * @param <T4> The type of the 4th parameter.
	 * @param <T5> The type of the 5th parameter.
	 * @param <T6> The type of the 6th parameter.
	 * @param <R>  The type of the result.
	 * @param <E>  The exception thrown.
	 */
	@FunctionalInterface
	public interface P6<T1, T2, T3, T4, T5, T6, R, E extends Exception> {
		/**
		 * Calls the function.
		 *
		 * @param p1 The 1st parameter.
		 * @param p2 The 2nd parameter.
		 * @param p3 The 3rd parameter.
		 * @param p4 The 4th parameter.
		 * @param p5 The 5th parameter.
		 * @param p6 The 6th parameter.
		 * @return The result.
		 * @throws E The exception thrown.
		 */
		R call(T1 p1, T2 p2, T3 p3, T4 p4, T5 p5, T6 p6) throws E;
	}
}

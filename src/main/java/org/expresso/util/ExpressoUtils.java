package org.expresso.util;

import java.util.Collection;
import java.util.Deque;
import java.util.List;

import org.expresso.Expression;

/** Utilities used by the Expresso library */
public class ExpressoUtils {
	/**
	 * Optimally gets the last value in an iterable
	 * 
	 * @param <T> The type of the iterable
	 * @param values The iterable
	 * @return The last value in the iterable
	 */
	public static <T> T getLast(Iterable<? extends T> values) {
		if (values == null) {
			return null;
		} else if (values instanceof Collection) {
			if (((Collection<?>) values).isEmpty())
				return null;
			else if (values instanceof Deque)
				return ((Deque<? extends T>) values).getLast();
			else if (values instanceof List) {
				List<? extends T> list = (List<? extends T>) values;
				return list.get(list.size() - 1);
			}
		}
		T last = null;
		for (T val : values)
			last = val;
		return last;
	}

	/**
	 * @param expression The expression to get the end position for
	 * @return The position immediately after the expression
	 */
	public static int getEnd(Expression<?> expression) {
		return expression == null ? 0 : expression.getStream().getPosition() + expression.length();
	}

	/**
	 * @param rootPosition The point-of-reference position in the stream
	 * @param children The children
	 * @return The difference between the end position of the last child and the point of reference
	 */
	public static int getLength(int rootPosition, Iterable<? extends Expression<?>> children) {
		Expression<?> last = getLast(children);
		if (last == null)
			return 0;
		else
			return getEnd(last) - rootPosition;
	}
}

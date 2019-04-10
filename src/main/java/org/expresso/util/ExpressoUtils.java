package org.expresso.util;

import java.util.Collection;
import java.util.Deque;
import java.util.List;

import org.expresso3.Expression;

public class ExpressoUtils {
	public static <T> T getLast(Iterable<? extends T> expressions) {
		if (expressions == null) {
			return null;
		} else if (expressions instanceof Collection) {
			if (((Collection<?>) expressions).isEmpty())
				return null;
			else if (expressions instanceof Deque)
				return ((Deque<? extends T>) expressions).getLast();
			else if (expressions instanceof List) {
				List<? extends T> list = (List<? extends T>) expressions;
				return list.get(list.size() - 1);
			}
		}
		T last = null;
		for (T val : expressions)
			last = val;
		return last;
	}

	public static int getEnd(Expression<?> expression) {
		return expression == null ? 0 : expression.getStream().getPosition() + expression.length();
	}

	public static int getLength(int rootPosition, Iterable<? extends Expression<?>> children) {
		Expression<?> last = getLast(children);
		if (last == null)
			return 0;
		else
			return getEnd(last) - rootPosition;
	}
}

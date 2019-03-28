package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.expresso.Expression;
import org.expresso.ExpressionPossibility;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

public class ForbidExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final ExpressionType<S> theForbidden;

	public ForbidExpressionType(int id, ExpressionType<S> forbidden) {
		super(id);
		theForbidden = forbidden;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		ExpressionPossibility<S2> forbidden = parser.parseWith(theForbidden);
		if (forbidden == null)
			return null;
		else if (!parser.tolerateErrors() && forbidden.getErrorCount() > 0)
			return null;
		else
			return new ForbiddenPossibility<>(this, parser, forbidden);
	}

	@Override
	public String toString() {
		return "Forbidden:" + theForbidden;
	}

	private static class ForbiddenPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final ForbidExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theForbidden;

		ForbiddenPossibility(ForbidExpressionType<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> forbidden) {
			theType = type;
			theParser = parser;
			theForbidden = forbidden;
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theForbidden.length();
		}

		@Override
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			Collection<? extends ExpressionPossibility<S>> forbiddenForks = theForbidden.fork();
			if (forbiddenForks.isEmpty())
				return forbiddenForks;
			else
				return forbiddenForks.stream().map(fork -> new ForbiddenPossibility<>(theType, theParser, fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(forbiddenForks.size())));
		}

		@Override
		public int getErrorCount() {
			int fec = theForbidden.getErrorCount();
			if (fec != 0)
				return fec;
			else if (theForbidden.length() == 0)
				return 0;
			else
				return 1;
		}

		@Override
		public int getFirstErrorPosition() {
			int errPos = theForbidden.getFirstErrorPosition();
			if (errPos < 0 && theForbidden.length() > 0)
				errPos = 0;
			return errPos;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof ForbiddenPossibility))
				return false;
			ForbiddenPossibility<S> other = (ForbiddenPossibility<S>) o;
			return getType().equals(other.getType()) && theForbidden.equals(other.theForbidden);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theForbidden);
		}

		@Override
		public Expression<S> getExpression() {
			return new ForbiddenExpression<>(theParser.getStream(), theType, theForbidden.getExpression());
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(metadata);
			theForbidden.print(str, indent + 1, "");
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}

	private static class ForbiddenExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		public ForbiddenExpression(S stream, ForbidExpressionType<? super S> type, Expression<S> forbidden) {
			super(stream, type, Arrays.asList(forbidden));
		}

		@Override
		public Expression<S> getFirstError() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return super.getFirstError();
			else if (length() == 0)
				return null;
			else
				return this;
		}

		@Override
		public int getErrorCount() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return ec;
			else if (length() == 0)
				return 0;
			else
				return 1;
		}

		@Override
		public String getLocalErrorMessage() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return null;
			else if (length() == 0)
				return null;
			else
				return "Forbidden content present";
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
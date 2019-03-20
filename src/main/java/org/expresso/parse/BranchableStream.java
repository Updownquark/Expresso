package org.expresso.parse;

import java.io.IOException;
import java.util.function.BiFunction;

import org.qommons.TriFunction;

/**
 * Provides stream iteration over some kind of data. Instances of this type are immutable--an instance always points to the same position in
 * the stream. To point to a different position, a new instance is created.
 *
 * @param <D> The type of individual data values that this stream provides
 * @param <C> The type of data chunk that this stream handles
 */
public abstract class BranchableStream<D, C> implements Cloneable {
	/** A chunk of streamed data */
	protected class Chunk {
		private final int theChunkIndex;

		private C theData;

		private int theLength;

		private Chunk theNextChunk;

		private boolean isLast;

		Chunk(int chunkIndex) {
			theChunkIndex = chunkIndex;
			theData = createChunk(theChunkSize);
		}

		void getMore() throws IOException {
			if (theLength == theChunkSize)
				throw new IllegalStateException("No more data to get for this chunk");
			int nextLen = getNextData(theData, theLength);
			while (nextLen == 0) {
				// Wait for more data
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
			if (nextLen < 0) {
				isLast = true;
				return;
			}
			if (nextLen < theChunkSize - theLength)
				isLast = true;
			theLength += nextLen;
			if (theLength == theChunkSize)
				theNextChunk = new Chunk(theChunkIndex + 1);
		}

		/** @return The amount of data populated into this chunk */
		public int length() {
			return theLength;
		}

		/** @return The chunk that is next in the sequence after this */
		public Chunk getNext() {
			return theNextChunk;
		}

		/** @return This chunk's data */
		public C getData() {
			return theData;
		}
	}

	private final BranchableStream<D, C> theRoot;

	private final int theChunkSize;

	private Chunk theChunk;

	/** This stream's position within the current chunk */
	private int thePosition;

	/** @param chunkSize The chunk size for this stream's cache */
	protected BranchableStream(int chunkSize) {
		if (chunkSize <= 0)
			throw new IllegalArgumentException("Positive chunk size expected, not " + chunkSize);
		theRoot = this;
		theChunkSize = chunkSize;
		theChunk = new Chunk(0);
		thePosition = 0;
	}

	/** @return The current position in this stream */
	public int getPosition() {
		return theChunk.theChunkIndex * theChunkSize + thePosition;
	}

	@Override
	public BranchableStream<D, C> clone() {
		BranchableStream<D, C> ret;
		try {
			ret = (BranchableStream<D, C>) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
		return ret;
	}

	/** @return The amount of data that is available to this stream without further queries to the data source */
	public int getDiscoveredLength() {
		Chunk chunk = theChunk;
		int length = 0;
		while (chunk != null) {
			length += chunk.length();
			chunk = chunk.theNextChunk;
		}
		length -= thePosition;
		return length;
	}

	/**
	 * <p>
	 * Discovers at least a certain portion of this stream. If present in the data source, the stream will discovered its content up to the
	 * given number of spaces past the current position and <code>length</code>will be returned. If the stream is exhausted before that
	 * number, then the stream will be fully discovered and the remaining length of the stream will be returned.
	 * </p>
	 * <p>
	 * This method does not affect the position of this stream
	 * </p>
	 *
	 * @param length The number of places from this position to discover up to
	 * @return The number of places that have been discovered after this operation, or <code>length</code>, whichever is smaller.
	 * @throws IOException If the data cannot be retrieved
	 */
	public int discoverTo(int length) throws IOException {
		if (length < 0)
			throw new IndexOutOfBoundsException("" + length);
		if (isFullyDiscovered()) {
			int realLength = getDiscoveredLength();
			if (realLength > length)
				realLength = length;
			return realLength;
		}
		Chunk chunk = theChunk;
		int realLength = -thePosition;
		while (length >= realLength + chunk.length()) {
			if (chunk.length() == theChunkSize) {
				realLength += chunk.length();
				chunk = chunk.getNext();
			} else if (chunk.isLast)
				break;
			else
				chunk.getMore();
		}
		return realLength + chunk.length() - thePosition;
	}

	/**
	 * @param index The index of the value to get
	 * @return The data value in this stream at the given offset from the stream's position
	 * @throws IOException If the data cannot be retrieved
	 */
	public D get(int index) throws IOException {
		Object[] ret = new Object[1];
		doOn(index, (chunk, idx) -> ret[0] = get(chunk.getData(), idx), null);
		return (D) ret[0];
	}

	/**
	 * Creates a new stream instance pointing to a position some number of data points beyond this stream's position.
	 *
	 * @param spaces The number of value spaces to advance the position by
	 * @return The advanced stream
	 * @throws IOException If the data cannot be retrieved
	 */
	public BranchableStream<D, C> advance(int spaces) throws IOException {
		if (spaces == 0)
			return this;
		BranchableStream<D, C> advanced = clone();
		advanced._advance(spaces);
		return advanced;
	}

	private void _advance(int spaces) throws IOException {
		if (isFullyDiscovered() && spaces == getDiscoveredLength()) {
			// doOn will throw an out of bounds exception here, but this is acceptable--makes this stream zero-length
			while (theChunk.getNext() != null) {
				int newEnd = thePosition + spaces;
				if (newEnd > theChunk.length())
					newEnd = theChunk.length();
				theChunk = theChunk.getNext();
				advancedPast(theChunk.getData(), thePosition, newEnd);
			}
			thePosition = theChunk.length();
		} else {
			doOn(spaces, (chunk, idx) -> {
				theChunk = chunk;
				thePosition = idx;
				return null;
			}, (chunk, start, end) -> {
				advancedPast(chunk, start, end);
				return null;
			});
		}
	}

	/**
	 * Called when this stream is advanced. May be overridden by subclasses to allow them to keep track of metadata associated with their
	 * position (e.g. line and column number for text files)
	 * 
	 * @param chunk The data chunk whose data is being advanced past
	 * @param start The starting position of the data being passed
	 * @param end The end position +1 of the data being passed
	 */
	protected void advancedPast(C chunk, int start, int end) {}

	/**
	 * Performs some operation on a data point
	 *
	 * @param <T> The type of value returned by the operation
	 * @param index The index of the data point to operate on
	 * @param op The operation to perform. The function takes the chunk containing the data point and the index within the chunk's data
	 *        corresponding to the given index from this stream position.
	 * @param skip The operation to perform on data before the given index. May be null.
	 * @return The value returned by the operation
	 * @throws IOException If the data cannot be retrieved
	 */
	protected <T> T doOn(int index, BiFunction<Chunk, Integer, T> op, TriFunction<C, Integer, Integer, Void> skip) throws IOException {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		Chunk chunk = theChunk;
		int length = -thePosition;
		boolean firstChunk = true;
		while (chunk != null && index >= length + chunk.length()) {
			if (chunk.length() == theChunkSize) {
				length += chunk.length();
				if (skip != null) {
					int start = firstChunk ? thePosition : 0;
					skip.apply(chunk.getData(), start, chunk.length());
				}
				chunk = chunk.getNext();
				firstChunk = false;
			} else if (chunk.isLast)
				throw new IndexOutOfBoundsException(index + " of " + (length + chunk.length()));
			else
				chunk.getMore();
		}
		if (chunk == null)
			throw new IndexOutOfBoundsException(index + " of " + length);
		int targetPos = index - length;
		if (skip != null) {
			int start = firstChunk ? thePosition : 0;
			if (start != targetPos)
				skip.apply(chunk.getData(), start, targetPos);
		}
		return op.apply(chunk, index - length);
	}

	/** @return Whether this stream has discovered all its available content or not */
	public boolean isFullyDiscovered() {
		Chunk c = theChunk;
		while (!c.isLast && c.theNextChunk != null)
			c = c.theNextChunk;
		return c.isLast;
	}

	/**
	 * @param spaces The number of data points to check
	 * @return Whether this stream contains at least the given number of data points past this position
	 */
	public boolean hasMoreData(int spaces) throws IOException {
		return discoverTo(spaces) >= spaces;
	}

	/**
	 * Creates an empty chunk of data for this cache
	 *
	 * @param length The capacity of the chunk to create
	 * @return The new data chunk
	 */
	protected abstract C createChunk(int length);

	/**
	 * Fills the chunk structure with data for the stream's cache.
	 * <ul>
	 * <li>If there is enough data available to satisfy the request, the chunk will be filled with data to its capacity</li>
	 * <li>If there is some data available, but not enough to satisfy the request, the available data will be put in the chunk. If it can be
	 * determined that the data source has been exhausted, the {@link #isFullyDiscovered() discovered} flag will be set to true.</li>
	 * <li>If no data is available, this request will block until data is available (which will be filled in the chunk) and/or it is
	 * determined that the data source has been exhausted, in which case the {@link #isFullyDiscovered() discovered} flag will be set to true.
	 * </li>
	 * </ul>
	 *
	 * @param chunk The chunk to fill with data
	 * @param start The start position of the chunk to fill
	 *
	 * @return A collection of data with the given length.
	 * @throws IOException If the data cannot be retrieved
	 */
	protected abstract int getNextData(C chunk, int start) throws IOException;

	/**
	 * @param data The chunk of data
	 * @param index The index of the data value to get
	 * @return The data value at the given index in the given chunk
	 */
	protected abstract D get(C data, int index);

	protected abstract StringBuilder printChunk(C chunk, int start, int end, StringBuilder printTo);

	public StringBuilder printContent(int start, int end, StringBuilder printTo) {
		if (printTo == null)
			printTo = new StringBuilder(Math.min(end - start, getDiscoveredLength()));
		int chunkStart = thePosition + start;
		int chunkEnd = thePosition + end;
		Chunk chunk = theChunk;
		while (chunk != null && chunkStart < chunkEnd) {
			printChunk(chunk.getData(), chunkStart, Math.min(chunkEnd, chunk.length()), printTo);
			chunkStart += chunk.length();
			chunk = chunk.getNext();
		}
		return printTo;
	}

	@Override
	public String toString() {
		StringBuilder str = theRoot.printContent(0, theChunk.theChunkIndex * theChunkSize + thePosition, null);
		Chunk chunk = theChunk;
		str.append('\u2021'); // Double-dagger for the position
		if (thePosition != chunk.length())
			printChunk(chunk.getData(), thePosition, chunk.length(), str);
		while (!chunk.isLast && chunk.getNext() != null) {
			chunk = chunk.getNext();
			printChunk(chunk.getData(), 0, chunk.length(), str);
		}
		if (!chunk.isLast)
			str.append('\u2026'); // Ellipsis to show that there's more data
		return str.toString();
	}

	/** @return A representation of this stream's position */
	public abstract String printPosition();
}

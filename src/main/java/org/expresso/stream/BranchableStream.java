package org.expresso.stream;

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

		private final int theOffset;

		private C theData;

		private int theLength;

		private Chunk theNextChunk;

		private boolean isLast;

		Chunk(int chunkIndex, int offset) {
			theChunkIndex = chunkIndex;
			theData = createChunk(theStreamInfo.chunkSize);
			theOffset = offset;
		}

		void getMore() throws IOException {
			if (theLength == theStreamInfo.chunkSize)
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
				theStreamInfo.isFullyDiscovered = true;
				isLast = true;
				return;
			}
			theStreamInfo.theDiscoveredLength += nextLen;
			if (nextLen < theStreamInfo.chunkSize - theLength) {
				theStreamInfo.isFullyDiscovered = true;
				isLast = true;
			}
			theLength += nextLen;
			if (theLength == theStreamInfo.chunkSize)
				theNextChunk = new Chunk(theChunkIndex + 1, theOffset + theStreamInfo.chunkSize);
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

		/** @return This chunk's position offset from the beginning of the stream */
		public int getOffset() {
			return theOffset;
		}
	}

	private final StreamInfo<D, C> theStreamInfo;

	private Chunk theChunk;

	/** This stream's position within the current chunk */
	private int theChunkPosition;
	private int theAbsolutePosition;

	/** @param chunkSize The chunk size for this stream's cache */
	protected BranchableStream(int chunkSize) {
		if (chunkSize <= 0)
			throw new IllegalArgumentException("Positive chunk size expected, not " + chunkSize);
		theStreamInfo = new StreamInfo<>(chunkSize, this);
		theChunk = new Chunk(0, 0);
		theChunkPosition = 0;
	}

	private void setPosition(int chunkPosition) {
		theChunkPosition = chunkPosition;
		theAbsolutePosition = theChunk.theOffset + theChunkPosition;
	}

	/** @return The current position in this stream */
	public int getPosition() {
		return theAbsolutePosition;
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
		return theStreamInfo.theDiscoveredLength - getPosition();
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
		else if (getDiscoveredLength() >= length)
			return length;
		else if (isFullyDiscovered()) {
			int realLength = getDiscoveredLength();
			if (realLength > length)
				realLength = length;
			return realLength;
		}
		Chunk chunk = theChunk;
		int realLength = -theChunkPosition;
		while (length >= realLength + chunk.length()) {
			if (chunk.length() == theStreamInfo.chunkSize) {
				realLength += chunk.length();
				chunk = chunk.getNext();
			} else if (chunk.isLast)
				break;
			else
				chunk.getMore();
		}
		return realLength + chunk.length() - theChunkPosition;
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
				int newEnd = theChunkPosition + spaces;
				if (newEnd > theChunk.length())
					newEnd = theChunk.length();
				theChunk = theChunk.getNext();
				advancedPast(theChunk.getData(), theChunkPosition, newEnd);
			}
			setPosition(theChunk.length());
		} else {
			Chunk chunk = theChunk;
			int length = -theChunkPosition;
			boolean firstChunk = true;
			while (chunk != null && spaces >= length + chunk.length()) {
				if (chunk.length() == theStreamInfo.chunkSize) {
					length += chunk.length();
					int start = firstChunk ? theChunkPosition : 0;
					advancedPast(chunk.getData(), start, chunk.length());
					chunk = chunk.getNext();
					firstChunk = false;
				} else if (chunk.isLast)
					throw new IndexOutOfBoundsException(spaces + " of " + (length + chunk.length()));
				else
					chunk.getMore();
			}
			if (chunk == null)
				throw new IndexOutOfBoundsException(spaces + " of " + length);
			int targetPos = spaces - length;
			int start = firstChunk ? theChunkPosition : 0;
			if (start != targetPos)
				advancedPast(chunk.getData(), start, targetPos);
			theChunk = chunk;
			setPosition(spaces - length);
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
	 * @param index The position index
	 * @return The chunk at the given index
	 * @throws IOException If an error occurs reading the data
	 * @throws IndexOutOfBoundsException If the stream ends before the given index
	 */
	protected Chunk getChunkAt(int index) throws IOException, IndexOutOfBoundsException {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		Chunk chunk = theChunk;
		int length = -theChunkPosition;
		while (chunk != null && index >= length + chunk.length()) {
			if (chunk.length() == theStreamInfo.chunkSize) {
				length += chunk.length();
				chunk = chunk.getNext();
			} else if (chunk.isLast)
				throw new IndexOutOfBoundsException(index + " of " + (length + chunk.length()));
			else
				chunk.getMore();
		}
		if (chunk == null)
			throw new IndexOutOfBoundsException(index + " of " + length);
		return chunk;
	}

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
		int length = -theChunkPosition;
		boolean firstChunk = true;
		while (chunk != null && index >= length + chunk.length()) {
			if (chunk.length() == theStreamInfo.chunkSize) {
				length += chunk.length();
				if (skip != null) {
					int start = firstChunk ? theChunkPosition : 0;
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
			int start = firstChunk ? theChunkPosition : 0;
			if (start != targetPos)
				skip.apply(chunk.getData(), start, targetPos);
		}
		return op.apply(chunk, index - length);
	}

	/** @return Whether this stream has discovered all its available content or not */
	public boolean isFullyDiscovered() {
		return theStreamInfo.isFullyDiscovered;
	}

	/**
	 * @param spaces The number of data points to check
	 * @return Whether this stream contains at least the given number of data points past this position
	 * @throws IOException If an exception occurs reading the stream
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

	/**
	 * @param chunk The chunk to print
	 * @param start The beginning index (inclusive) in the chunk
	 * @param end The end index (exclusive) in the chunk
	 * @param printTo The string builder to print the data into
	 * @return The same string builder
	 */
	protected abstract StringBuilder printChunk(C chunk, int start, int end, StringBuilder printTo);

	/**
	 * Prints some of this stream's content to a string builder
	 * 
	 * @param start The beginning index (inclusive) in this stream (relative to the current position) to print data from
	 * @param end The ending index (exclusive) in this stream to print data up to
	 * @param printTo The string builder to print the data into
	 * @return The same string builder
	 */
	public StringBuilder printContent(int start, int end, StringBuilder printTo) {
		if (printTo == null)
			printTo = new StringBuilder(Math.min(end - start, getDiscoveredLength()));
		int chunkStart = theChunkPosition + start;
		int chunkEnd = theChunkPosition + end;
		Chunk chunk = theChunk;
		while (chunk != null && chunkStart < chunkEnd) {
			printChunk(chunk.getData(), chunkStart, Math.min(chunkEnd, chunk.length()), printTo);
			chunkStart = 0;
			chunkEnd -= chunk.length();
			chunk = chunk.getNext();
		}
		return printTo;
	}

	@Override
	public String toString() {
		StringBuilder str = theStreamInfo.root.printContent(0, theChunk.theChunkIndex * theStreamInfo.chunkSize + theChunkPosition, null);
		Chunk chunk = theChunk;
		str.append('\u2021'); // Double-dagger for the position
		if (theChunkPosition != chunk.length())
			printChunk(chunk.getData(), theChunkPosition, chunk.length(), str);
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

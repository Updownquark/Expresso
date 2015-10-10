package org.expresso.parse;

import java.util.function.BiFunction;

import org.qommons.Sealable;

/**
 * Provides stream iteration over some kind of data
 *
 * @param <D> The type of individual data values that this stream provides
 * @param <C> The type of data chunk that this stream handles
 */
public abstract class BranchableStream<D, C> implements Cloneable, Sealable {
	/** A chunk of streamed data */
	protected class Chunk {
		private final int theChunkIndex;

		private C theData;

		private int theLength;

		private Chunk theNextChunk;

		Chunk(int chunkIndex) {
			theChunkIndex = chunkIndex;
			theData = createChunk(theChunkSize);
		}

		void getMore() {
			if(theLength == theChunkSize)
				throw new IllegalStateException("No more data to get for this chunk");
			if(isDiscovered())
				return;
			theLength += getNextData(theData, theLength);
			if(theLength == theChunkSize)
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

	private final int theChunkSize;

	private Chunk theChunk;

	private int thePosition;

	private boolean isSealed;

	/** @param chunkSize The chunk size for this stream's cache */
	protected BranchableStream(int chunkSize) {
		if(chunkSize <= 0)
			throw new IllegalArgumentException("Positive chunk size expected, not " + chunkSize);
		theChunkSize = chunkSize;
		theChunk = new Chunk(0);
		thePosition = 0;
	}

	/** Throws an exception if this stream is sealed */
	protected void assertUnsealed() {
		if(isSealed)
			throw new SealedException(this);
	}

	@Override
	public boolean isSealed() {
		return isSealed;
	}

	@Override
	public void seal() {
		isSealed = true;
	}

	/** @return The current position in this stream */
	public int getPosition() {
		return theChunk.theChunkIndex * theChunkSize + thePosition;
	}

	/**
	 * @return Another stream at the same position as this stream, but independently {@link #advance(int) advanceable}. This method uses
	 *         {@link #clone()}, so the type of the branched stream will be exactly the same as this type.
	 */
	public BranchableStream<D, C> branch() {
		BranchableStream<D, C> ret;
		try {
			ret = (BranchableStream<D, C>) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
		ret.isSealed = false;
		return ret;
	}

	/** @return The amount of data that is available to this stream without further queries to the data source */
	public int getDiscoveredLength() {
		Chunk chunk = theChunk;
		int length = 0;
		while(chunk != null) {
			length += chunk.length();
			chunk = chunk.theNextChunk;
		}
		length -= thePosition;
		return length;
	}

	/**
	 * Discovers at least a certain portion of this stream. If present in the data source, the stream will discovered its content up to the
	 * given number of spaces past the current position and <code>length</code>will be returned. If the stream is exhausted before that
	 * number, then the stream will be fully discovered and the remaining length of the stream will be returned.
	 *
	 * @param length The number of places from this position to discover up to
	 * @return The number of places that have been discovered after this operation, or <code>length</code>, whichever is smaller.
	 */
	public int discoverTo(int length) {
		if(length < 0)
			throw new IndexOutOfBoundsException("" + length);
		if(isDiscovered()) {
			int realLength = getDiscoveredLength();
			if(realLength > length)
				realLength = length;
			return realLength;
		}
		Chunk chunk = theChunk;
		int realLength = -thePosition;
		while(chunk != null && length >= realLength + chunk.length()) {
			if(chunk.length() == theChunkSize) {
				realLength += chunk.length();
				chunk = chunk.getNext();
			} else if(isDiscovered())
				break;
			else
				chunk.getMore();
		}
		if(chunk == null)
			return realLength;
		return length;
	}

	/**
	 * @param index The index of the value to get
	 * @return The data value in this stream at the given offset from the stream's position
	 */
	public D get(int index) {
		Object [] ret = new Object[1];
		doOn(index, (chunk, idx) -> ret[0] = get(chunk.getData(), index));
		return (D) ret[0];
	}

	/**
	 * Moves this stream's position forward
	 *
	 * @param spaces The number of value spaces to advance this stream's position by
	 * @return This stream
	 */
	public BranchableStream<D, C> advance(int spaces) {
		assertUnsealed();
		doOn(spaces, (chunk, idx) -> {
			theChunk = chunk;
			thePosition = idx;
			return null;
		});
		return this;
	}

	/**
	 * Performs some operation on a data point
	 *
	 * @param <T> The type of value returned by the operation
	 * @param index The index of the data point to operate on
	 * @param op The operation to perform. The function takes the chunk containing the data point and the index within the chunk's data
	 *            corresponding to the given index from this stream position.
	 * @return The value returned by the operation
	 */
	protected <T> T doOn(int index, BiFunction<Chunk, Integer, T> op) {
		if(index < 0)
			throw new IndexOutOfBoundsException("" + index);
		Chunk chunk = theChunk;
		int length = -thePosition;
		while(chunk != null && index >= length + chunk.length()) {
			if(chunk.length() == theChunkSize) {
				length += chunk.length();
				chunk = chunk.getNext();
			} else if(isDiscovered())
				throw new IndexOutOfBoundsException(index + " of " + (length + chunk.length()));
			else
				chunk.getMore();
		}
		if(chunk == null)
			throw new IndexOutOfBoundsException(index + " of " + length);
		return op.apply(chunk, index - length + chunk.length());
	}

	/** @return Whether this stream has discovered all its available content or not */
	public abstract boolean isDiscovered();

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
	 * determined that the data source has been exhausted, the {@link #isDiscovered() discovered} flag will be set to true.</li>
	 * <li>If no data is available, this request will block until data is available (which will be filled in the chunk) and/or it is
	 * determined that the data source has been exhausted, in which case the {@link #isDiscovered() discovered} flag will be set to true.
	 * </li>
	 * </ul>
	 *
	 * @param chunk The chunk to fill with data
	 * @param start The start position of the chunk to fill
	 *
	 * @return A collection of data with the given length.
	 */
	protected abstract int getNextData(C chunk, int start);

	/**
	 * @param data The chunk of data
	 * @param index The index of the data value to get
	 * @return The data value at the given index in the given chunk
	 */
	protected abstract D get(C data, int index);
}

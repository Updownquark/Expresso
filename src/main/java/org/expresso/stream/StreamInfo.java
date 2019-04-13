package org.expresso.stream;

/**
 * Info about the source of a {@link BranchableStream}
 * 
 * @param <D> The data type
 * @param <C> The chunk type
 */
public class StreamInfo<D, C> {
	/** The chunk size for the stream */
	public final int chunkSize;
	/** The root of the stream (at position 0) */
	public final BranchableStream<D, C> root;
	int theDiscoveredLength;
	boolean isFullyDiscovered;

	/**
	 * @param chunkSize The chunk size for the stream
	 * @param root The root of the stream
	 */
	public StreamInfo(int chunkSize, BranchableStream<D, C> root) {
		this.chunkSize = chunkSize;
		this.root = root;
	}
}

package org.expresso.stream;

public class StreamInfo<D, C> {
	public final int chunkSize;
	public final BranchableStream<D, C> root;
	int theDiscoveredLength;
	boolean isFullyDiscovered;

	public StreamInfo(int chunkSize, BranchableStream<D, C> root) {
		this.chunkSize = chunkSize;
		this.root = root;
	}
}

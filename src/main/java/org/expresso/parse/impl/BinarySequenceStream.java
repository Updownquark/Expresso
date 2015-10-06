package org.expresso.parse.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.expresso.parse.BranchableStream;

/** A branchable stream of binary data */
public abstract class BinarySequenceStream extends BranchableStream<Byte, byte [], BinarySequenceStream> {
	private final byte [] holder = new byte[1];

	/** @see BranchableStream#BranchableStream(int) */
	protected BinarySequenceStream(int chunkSize) {
		super(chunkSize);
	}

	/**
	 * @param index The index of the value to get
	 * @return The byte at the given index
	 */
	public byte getByte(int index) {
		doOn(index, (chunk, idx) -> holder[0] = chunk.getData()[idx]);
		return holder[0];
	}

	@Override
	protected byte [] createChunk(int length) {
		return new byte[length];
	}

	@Override
	protected Byte get(byte [] data, int index) {
		return data[index];
	}

	/**
	 * Creates a byte stream from a byte array
	 *
	 * @param array The array to create the stream from
	 * @return The stream backed by the array
	 */
	public static BinarySequenceStream from(byte [] array) {
		return new BinarySequenceStream(array.length) {
			@Override
			public boolean isDiscovered() {
				return true;
			}

			@Override
			protected byte [] createChunk(int length) {
				return array;
			}

			@Override
			protected int getNextData(byte [] chunk, int start) {
				return chunk.length;
			}
		};
	}

	/**
	 * Creates a byte stream from a java input stream
	 *
	 * @param input The input stream to create the byte stream from
	 * @param chunkSize The chunk size for the byte stream
	 * @return The byte stream backed by the input stream's data
	 */
	public static BinarySequenceStream from(InputStream input, int chunkSize) {
		return new BinarySequenceStream(chunkSize) {
			boolean isDone;

			@Override
			public boolean isDiscovered() {
				return isDone;
			}

			@Override
			protected int getNextData(byte [] chunk, int start) {
				int ret;
				try {
					ret = input.read(chunk, start, chunk.length - start);
				} catch(IOException e) {
					throw new IllegalStateException("Could not read stream data", e);
				}
				if(ret < 0) {
					isDone = true;
					ret = 0;
				}
				return ret;
			}
		};
	}

	/**
	 * Creates a byte from a file
	 *
	 * @param file The file to supply data for the stream
	 * @param chunkSize The chunk size for the stream
	 * @return The byte stream backed by the file's contents
	 * @throws java.io.FileNotFoundException If the given file cannot be found
	 */
	public static BinarySequenceStream from(File file, int chunkSize) throws java.io.FileNotFoundException {
		return from(new java.io.FileInputStream(file), chunkSize);
	}
}

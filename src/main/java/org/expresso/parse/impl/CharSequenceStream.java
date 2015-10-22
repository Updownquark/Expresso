package org.expresso.parse.impl;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import org.expresso.parse.BranchableStream;

/** A branchable stream of character data */
public abstract class CharSequenceStream extends BranchableStream<Character, char []> implements CharSequence {
	private final char [] holder = new char[1];

	/** @see BranchableStream#BranchableStream(int) */
	protected CharSequenceStream(int chunkSize) {
		super(chunkSize);
	}

	@Override
	public int length() {
		return isDiscovered() ? getDiscoveredLength() : getDiscoveredLength() + 1;
	}

	@Override
	public char charAt(int index) {
		try {
			doOn(index, (chunk, idx) -> holder[0] = chunk.getData()[idx]);
		} catch(IOException e) {
			throw new IllegalStateException("Could not retrieve data for charAt", e);
		}
		return holder[0];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		if(start < 0)
			throw new IndexOutOfBoundsException("" + start);
		try {
			doOn(end, (chunk, idx) -> null);
		} catch(IOException e) {
			throw new IllegalStateException("Could not retrieve data for subsequence", e);
		} // Check the end index
		if(start==0){
			if(isDiscovered() && end == length())
				return this;
			return new CharSequence(){
				@Override
				public int length() {
					int ret=CharSequenceStream.this.length();
					if(ret>end)
						ret=end;
					return ret;
				}

				@Override
				public char charAt(int index) {
					if(index>=end)
						throw new IndexOutOfBoundsException(index+" of "+end);
					return CharSequenceStream.this.charAt(index);
				}

				@Override
				public CharSequence subSequence(int start2, int end2) {
					if(end2<=end)
						return this;
					else
						return CharSequenceStream.this.subSequence(start2, end2);
				}
			};
		} else
			try {
				return ((CharSequenceStream) branch().advance(start)).subSequence(0, end - start);
			} catch(IOException e) {
				throw new IllegalStateException("Could not retrieve data for subsequence", e);
			}
	}

	@Override
	protected char [] createChunk(int length) {
		return new char[length];
	}

	@Override
	protected Character get(char [] data, int index) {
		return data[index];
	}

	/**
	 * Creates a {@link CharSequenceStream} from a character array
	 *
	 * @param array The character array to make a stream from
	 * @return The stream backed by the character array
	 */
	public static CharSequenceStream from(char [] array) {
		return new CharSequenceStream(array.length) {
			@Override
			public boolean isDiscovered() {
				return true;
			}

			@Override
			protected char [] createChunk(int length) {
				return array;
			}

			@Override
			protected int getNextData(char [] chunk, int start) {
				return chunk.length;
			}
		};
	}

	/**
	 * Creates a {@link CharSequenceStream} from an in-memory {@link CharSequence}
	 *
	 * @param seq The character sequence to create the stream from
	 * @return The stream backed by the char sequence
	 */
	public static CharSequenceStream from(CharSequence seq) {
		char [] array = new char[seq.length()];
		for(int c = 0; c < array.length; c++)
			array[c] = seq.charAt(c);
		return from(array);
	}

	/**
	 * Creates a {@link CharSequenceStream} from a reader
	 *
	 * @param reader The reader to supply data for the stream
	 * @param chunkSize The chunk size for the stream
	 * @return The stream backed by the reader's data
	 */
	public static CharSequenceStream from(Reader reader, int chunkSize) {
		return new CharSequenceStream(chunkSize) {
			boolean isDone;

			@Override
			public boolean isDiscovered() {
				return isDone;
			}

			@Override
			protected int getNextData(char [] chunk, int start) throws IOException {
				int ret = reader.read(chunk, start, chunk.length - start);
				if(ret < 0) {
					isDone = true;
					ret = 0;
				}
				return ret;
			}
		};
	}

	/**
	 * Creates a {@link CharSequenceStream} from a text file
	 *
	 * @param file The file to supply data for the stream
	 * @param chunkSize The chunk size for the stream
	 * @return The stream backed by the file's contents
	 * @throws java.io.FileNotFoundException If the given file cannot be found
	 */
	public static CharSequenceStream from(File file, int chunkSize) throws java.io.FileNotFoundException {
		return from(new java.io.FileReader(file), chunkSize);
	}
}

package org.expresso.stream;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

/** A branchable stream of character data */
public abstract class CharSequenceStream extends BranchableStream<Character, char []> implements CharSequence {
	private int theLineNumber = 1;
	private int theColumnNumber = 1;
	private StringBuilder theCurrentLine = new StringBuilder();

	/** @see BranchableStream#BranchableStream(int) */
	protected CharSequenceStream(int chunkSize) {
		super(chunkSize);
	}

	@Override
	public int length() {
		return isFullyDiscovered() ? getDiscoveredLength() : getDiscoveredLength() + 1;
	}

	@Override
	public char charAt(int index) {
		char [] holder = new char[1];
		try {
			doOn(index, (chunk, idx) -> holder[0] = chunk.getData()[idx], null);
		} catch(IOException e) {
			throw new IllegalStateException("Could not retrieve data for charAt", e);
		}
		return holder[0];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		if(start < 0)
			throw new IndexOutOfBoundsException("" + start);
		if(end>0){
			try {
				doOn(end - 1, (chunk, idx) -> null, null);
			} catch(IOException e) {
				throw new IllegalStateException("Could not retrieve data for subsequence", e);
			} // Check the end index
		}
		if(start == 0) {
			if(isFullyDiscovered() && end == length())
				return this;
			return new CharSequence() {
				@Override
				public int length() {
					int ret = CharSequenceStream.this.length();
					if(ret > end)
						ret = end;
					return ret;
				}

				@Override
				public char charAt(int index) {
					if(index >= end)
						throw new IndexOutOfBoundsException(index + " of " + end);
					return CharSequenceStream.this.charAt(index);
				}

				@Override
				public CharSequence subSequence(int start2, int end2) {
					if(end2 <= end)
						return this;
					else
						return CharSequenceStream.this.subSequence(start2, end2);
				}

				@Override
				public String toString() {
					StringBuilder ret = new StringBuilder();
					for(int i = 0; i < length(); i++)
						ret.append(charAt(i));
					return ret.toString();
				}
			};
		} else
			try {
				return ((CharSequenceStream) advance(start)).subSequence(0, end - start);
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

	@Override
	protected void advancedPast(char[] chunk, int start, int end) {
		for (int i = start; i < end; i++) {
			if (chunk[i] == '\n') {
				theLineNumber++;
				theColumnNumber = 1;
				theCurrentLine.setLength(0);
			} else if (chunk[i] != '\r') {
				theColumnNumber++;
				theCurrentLine.append(chunk[i]);
			}
		}
	}

	@Override
	public CharSequenceStream clone() {
		CharSequenceStream ret = (CharSequenceStream) super.clone();
		ret.theCurrentLine = new StringBuilder(theCurrentLine);
		return ret;
	}

	@Override
	protected StringBuilder printChunk(char[] chunk, int start, int end, StringBuilder printTo) {
		return printTo.append(chunk, start, end - start);
	}

	@Override
	public String printPosition() {
		StringBuilder ret = new StringBuilder();
		ret.append("Line ").append(theLineNumber).append(", Column ").append(theColumnNumber).append('\n').append(theCurrentLine);
		for (int i = 0; i < getDiscoveredLength() || !isFullyDiscovered(); i++) {
			char c = charAt(i);
			if (c == '\n')
				break;
			else if (c != '\r')
				ret.append(c);
		}
		ret.append('\n');
		for (int i = 0; i < theCurrentLine.length(); i++) {
			char ch = theCurrentLine.charAt(i);
			if (ch == '\t')
				ret.append('\t');
			else
				ret.append(' ');
		}
		ret.append('^');
		return ret.toString();
	}

	/**
	 * @param spaces The number of characters to get
	 * @return A CharSequence containing the next <code>spaces</code> characters in this stream, or the rest of this stream, whichever is smaller.
	 * @throws IOException If an error occurs retrieving the data
	 */
	public CharSequence getNext(int spaces) throws IOException{
		int len=discoverTo(spaces);
		return subSequence(0, len);
	}

	/**
	 * Creates a {@link CharSequenceStream} from a character array
	 *
	 * @param array The character array to make a stream from
	 * @return The stream backed by the character array
	 */
	public static CharSequenceStream from(char [] array) {
		// TODO this will throw an exception for an zero-length array
		return new CharSequenceStream(array.length) {
			private boolean isFinished;

			@Override
			protected char [] createChunk(int length) {
				return array;
			}

			@Override
			protected int getNextData(char [] chunk, int start) {
				if (isFinished)
					return -1;
				isFinished = true;
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
			@Override
			protected int getNextData(char [] chunk, int start) throws IOException {
				return reader.read(chunk, start, chunk.length - start);
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

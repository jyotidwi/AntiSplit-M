package com.apk.axml.utils;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class IntReader {

	int m_position;
	private InputStream m_stream;
	private boolean m_bigEndian;

	public IntReader(InputStream stream,boolean bigEndian) {
		reset(stream,bigEndian);
	}

	public void reset(InputStream stream, boolean bigEndian) {
		m_stream = stream;
		m_bigEndian = bigEndian;
		m_position = 0;
	}

	public void close() {
		if (m_stream == null) {
			return;
		}
		try {
			m_stream.close();
		} catch (IOException ignored) {
		}
		reset(null,false);
	}

	public int readInt() throws IOException {
		return readInt(4);
	}

	public void readFully(byte[] b) throws IOException {
		new DataInputStream(m_stream).readFully(b);
	}

	public int readInt(int length) throws IOException {
		if (length < 0 || length > 4) {
			throw new IllegalArgumentException();
		}
		int result=0;
		if (m_bigEndian) {
			for (int i=(length-1)*8; i>=0; i-=8) {
				int b = m_stream.read();
				if (b ==-1) {
					throw new EOFException();
				}
				m_position+=1;
				result|=(b<<i);
			}
		} else {
			length*=8;
			for (int i=0; i!=length; i+=8) {
				int b = m_stream.read();
				if (b==-1) {
					throw new EOFException();
				}
				m_position +=1;
				result|=(b<<i);
			}
		}
		return result;
	}

	public int[] readIntArray(int length) throws IOException {
		int[] array=new int[length];
		readIntArray(array,0,length);
		return array;
	}

	public void readIntArray(int[] array,int offset,int length) throws IOException {
		for (;length>0;length-=1) {
			array[offset++]=readInt();
		}
	}

	public void skip(int bytes) throws IOException {
		if (bytes<=0) {
			return;
		}
		long skipped = m_stream.skip(bytes);
		m_position+=skipped;
		if (skipped!=bytes) {
			throw new EOFException();
		}
	}

	public void skipInt() throws IOException {
		skip(4);
	}

}
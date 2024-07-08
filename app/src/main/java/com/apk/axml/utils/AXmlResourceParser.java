package com.apk.axml.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AXmlResourceParser implements XmlPullParser {

	int m_idAttribute;
	int[] m_resourceIDs;
	int m_styleAttribute;
	private IntReader m_reader;
	private boolean m_operational = false;
	private boolean m_decreaseDepth;
	private final NamespaceStack m_namespaces = new NamespaceStack();
	private int m_event;
	private int m_lineNumber;
	private int m_name;
	private int m_namespaceUri;
	private int[] m_attributes;
	private int m_classAttribute;
	private StringBlock m_strings;

	private static final String E_NOT_SUPPORTED = "Method is not supported.";

	private static final int
			ATTRIBUTE_IX_NAMESPACE_URI = 0,
			ATTRIBUTE_IX_NAME = 1,
			ATTRIBUTE_IX_VALUE_STRING = 2,
			ATTRIBUTE_IX_VALUE_TYPE = 3,
			ATTRIBUTE_IX_VALUE_DATA = 4,
			ATTRIBUTE_LENGHT = 5;

	private static final int
			CHUNK_AXML_FILE = 0x00080003,
			CHUNK_RESOURCEIDS = 0x00080180,
			CHUNK_XML_FIRST = 0x00100100,
			CHUNK_XML_START_NAMESPACE = 0x00100100,
			CHUNK_XML_END_NAMESPACE = 0x00100101,
			CHUNK_XML_START_TAG = 0x00100102,
			CHUNK_XML_END_TAG = 0x00100103,
			CHUNK_XML_LAST = 0x00100104;

	public AXmlResourceParser() {
		resetEventInfo();
	}

	public void open(InputStream stream) {
		close();
		if (stream != null) {
			m_reader = new IntReader(stream,false);
		}
	}

	public void close() {
		if (!m_operational) {
			return;
		}
		m_operational = false;
		m_reader.close();
		m_reader = null;
		m_strings = null;
		m_resourceIDs = null;
		m_namespaces.reset();
		resetEventInfo();
	}

	public int next() throws XmlPullParserException,IOException {
		if (m_reader == null) {
			throw new XmlPullParserException("Parser is not opened.",this,null);
		}
		try {
			doNext();
			return m_event;
		}
		catch (IOException e) {
			close();
			throw e;
		}
	}

	public int nextToken() throws XmlPullParserException, IOException {
		return next();
	}

	public int nextTag() throws XmlPullParserException,IOException {
		int eventType = next();
		if (eventType == TEXT && isWhitespace()) {
			eventType = next();
		}
		if (eventType != START_TAG && eventType != END_TAG) {
			throw new XmlPullParserException("Expected start or end tag.",this,null);
		}
		return eventType;
	}

	public String nextText() throws XmlPullParserException,IOException {
		if(getEventType() != START_TAG) {
			throw new XmlPullParserException("Parser must be on START_TAG to read next text.",this,null);
		}
		int eventType = next();
		if (eventType == TEXT) {
			String result = getText();
			eventType=next();
			if (eventType != END_TAG) {
				throw new XmlPullParserException("Event TEXT must be immediately followed by END_TAG.",this,null);
			}
			return result;
		} else if (eventType == END_TAG) {
			return "";
		} else {
			throw new XmlPullParserException("Parser must be on START_TAG or TEXT to read text.",this,null);
		}
	}

	public void require(int type,String namespace,String name) throws XmlPullParserException {
		if (type != getEventType() ||
				(namespace != null && !namespace.equals(getNamespace())) ||
				(name != null && !name.equals(getName())))
		{
			throw new XmlPullParserException(TYPES[type]+" is expected.",this,null);
		}
	}

	public int getDepth() {
		return m_namespaces.getDepth()-1;
	}

	public int getEventType() {
		return m_event;
	}

	public int getLineNumber() {
		return m_lineNumber;
	}

	public String getName() {
		if (m_name == -1 || (m_event != START_TAG && m_event != END_TAG)) {
			return null;
		}
		return m_strings.getString(m_name);
	}

	public String getText() {
		if (m_name == -1 || m_event!=TEXT) {
			return null;
		}
		return m_strings.getString(m_name);
	}

	public char[] getTextCharacters(int[] holderForStartAndLength) {
		String text = getText();
		if (text == null) {
			return null;
		}
		holderForStartAndLength[0] = 0;
		holderForStartAndLength[1] = text.length();
		char[] chars = new char[text.length()];
		text.getChars(0,text.length(),chars,0);
		return chars;
	}

	public String getNamespace() {
		return m_strings.getString(m_namespaceUri);
	}

	public String getPrefix() {
		int prefix = m_namespaces.findPrefix(m_namespaceUri);
		return m_strings.getString(prefix);
	}

	public String getPositionDescription() {
		return "XML line #"+getLineNumber();
	}

	public int getNamespaceCount(int depth) {
		return m_namespaces.getAccumulatedCount(depth);
	}

	public String getNamespacePrefix(int pos) {
		int prefix = m_namespaces.getPrefix(pos);
		return m_strings.getString(prefix);
	}

	public String getNamespaceUri(int pos) {
		int uri = m_namespaces.getUri(pos);
		return m_strings.getString(uri);
	}

	public int getAttributeCount() {
		if (m_event != START_TAG) {
			return -1;
		}
		return m_attributes.length/ATTRIBUTE_LENGHT;
	}

	public String getAttributeNamespace(int index) {
		int offset = getAttributeOffset(index);
		int namespace = m_attributes[offset+ATTRIBUTE_IX_NAMESPACE_URI];
		if (namespace == -1) {
			return "";
		}
		return m_strings.getString(namespace);
	}

	public String getAttributePrefix(int index) {
		int offset = getAttributeOffset(index);
		int uri = m_attributes[offset+ATTRIBUTE_IX_NAMESPACE_URI];
		int prefix=m_namespaces.findPrefix(uri);
		if (prefix == -1) {
			return "";
		}
		return m_strings.getString(prefix);
	}

	public String getAttributeName(int index) {
		int offset = getAttributeOffset(index);
		int name=m_attributes[offset+ATTRIBUTE_IX_NAME];
		if (name == -1) {
			return "";
		}
		return m_strings.getString(name);
	}

	public int getAttributeValueType(int index) {
		int offset = getAttributeOffset(index);
		return m_attributes[offset+ATTRIBUTE_IX_VALUE_TYPE];
	}

	public int getAttributeValueData(int index) {
		int offset = getAttributeOffset(index);
		return m_attributes[offset+ATTRIBUTE_IX_VALUE_DATA];
	}

	public String getAttributeValue(int index) {
		int offset = getAttributeOffset(index);
		int valueType = m_attributes[offset+ATTRIBUTE_IX_VALUE_TYPE];
		if (valueType == TypedValue.TYPE_STRING) {
			int valueString = m_attributes[offset+ATTRIBUTE_IX_VALUE_STRING];
			return m_strings.getString(valueString);
		}
		return "";
	}

	public String getAttributeValue(String namespace,String attribute) {
		int index = findAttribute(namespace,attribute);
		if (index == -1) {
			return null;
		}
		return getAttributeValue(index);
	}

	public String getAttributeType(int index) {
		return "CDATA";
	}

	public boolean isAttributeDefault(int index) {
		return false;
	}

	public void setInput(InputStream stream,String inputEncoding) throws XmlPullParserException {
		throw new XmlPullParserException(E_NOT_SUPPORTED);
	}

	public void setInput(Reader reader) throws XmlPullParserException {
		throw new XmlPullParserException(E_NOT_SUPPORTED);
	}

	public String getInputEncoding() {
		return null;
	}

	public int getColumnNumber() {
		return -1;
	}

	public boolean isEmptyElementTag() {
		return false;
	}

	public boolean isWhitespace() {
		return false;
	}

	public void defineEntityReplacementText(String entityName,String replacementText) throws XmlPullParserException {
		throw new XmlPullParserException(E_NOT_SUPPORTED);
	}

	public String getNamespace(String prefix) {
		throw new RuntimeException(E_NOT_SUPPORTED);
	}

	public Object getProperty(String name) {
		return null;
	}

	public void setProperty(String name,Object value) throws XmlPullParserException {
		throw new XmlPullParserException(E_NOT_SUPPORTED);
	}

	public boolean getFeature(String feature) {
		return false;
	}

	public void setFeature(String name,boolean value) throws XmlPullParserException {
		throw new XmlPullParserException(E_NOT_SUPPORTED);
	}

	private static final class NamespaceStack {

		int m_count;
		private int m_dataLength;
		private int[] m_data;
		private int m_depth;
		public NamespaceStack() {
			m_data=new int[32];
		}

		public void reset() {
			m_dataLength = 0;
			m_count = 0;
			m_depth = 0;
		}

		public int getCurrentCount() {
			if (m_dataLength == 0) {
				return 0;
			}
			int offset = m_dataLength-1;
			return m_data[offset];
		}

		public int getAccumulatedCount(int depth) {
			if (m_dataLength == 0 || depth<0) {
				return 0;
			}
			if (depth > m_depth) {
				depth = m_depth;
			}
			int accumulatedCount=0;
			int offset = 0;
			for (;depth != 0;--depth) {
				int count = m_data[offset];
				accumulatedCount+=count;
				offset += (2+count*2);
			}
			return accumulatedCount;
		}

		public void push(int prefix,int uri) {
			if (m_depth == 0) {
				increaseDepth();
			}
			ensureDataCapacity();
			int offset = m_dataLength-1;
			int count = m_data[offset];
			m_data[offset-1-count*2] = count+1;
			m_data[offset] = prefix;
			m_data[offset+1] = uri;
			m_data[offset+2] = count+1;
			m_dataLength+=2;
			m_count+=1;
		}

		public void pop() {
			if (m_dataLength == 0) {
				return;
			}
			int offset = m_dataLength-1;
			int count = m_data[offset];
			if (count == 0) {
				return;
			}
			count-=1;
			offset-=2;
			m_data[offset]=count;
			offset-=(1+count*2);
			m_data[offset]=count;
			m_dataLength-=2;
			m_count-=1;
		}

		public int getPrefix(int index) {
			return get(index,true);
		}

		public int getUri(int index) {
			return get(index,false);
		}

		public int findPrefix(int uri) {
			return find(uri);
		}

		public int getDepth() {
			return m_depth;
		}

		public void increaseDepth() {
			ensureDataCapacity();
			int offset = m_dataLength;
			m_data[offset] = 0;
			m_data[offset+1] = 0;
			m_dataLength+=2;
			m_depth+=1;
		}

		public void decreaseDepth() {
			if (m_dataLength == 0) {
				return;
			}
			int offset = m_dataLength-1;
			int count = m_data[offset];
			if ((offset-1-count*2) == 0) {
				return;
			}
			m_dataLength-=2+count*2;
			m_count-=count;
			m_depth-=1;
		}

		private void ensureDataCapacity() {
			int available = (m_data.length-m_dataLength);
			if (available > 2) {
				return;
			}
			int newLength = (m_data.length+available)*2;
			int[] newData = new int[newLength];
			System.arraycopy(m_data,0,newData,0,m_dataLength);
			m_data = newData;
		}

		private int find(int prefixOrUri) {
			if (m_dataLength == 0) {
				return -1;
			}
			int offset = m_dataLength-1;
			for (int i = m_depth; i!=0; --i) {
				int count=m_data[offset];
				offset-=2;
				for (;count!=0; --count) {
					if (false) {
						if (m_data[offset] == prefixOrUri) {
							return m_data[offset+1];
						}
					} else {
						if (m_data[offset+1] == prefixOrUri) {
							return m_data[offset];
						}
					}
					offset-=2;
				}
			}
			return -1;
		}

		private int get(int index,boolean prefix) {
			if (m_dataLength == 0 || index < 0) {
				return -1;
			}
			int offset = 0;
			for (int i=m_depth; i!=0; --i) {
				int count = m_data[offset];
				if (index >= count) {
					index-=count;
					offset+=(2+count*2);
					continue;
				}
				offset+=(1+index*2);
				if (!prefix) {
					offset+=1;
				}
				return m_data[offset];
			}
			return -1;
		}
	}

	private int getAttributeOffset(int index) {
		if (m_event != START_TAG) {
			throw new IndexOutOfBoundsException("Current event is not START_TAG.");
		}
		int offset = index*5;
		if (offset >= m_attributes.length) {
			throw new IndexOutOfBoundsException("Invalid attribute index ("+index+").");
		}
		return offset;
	}

	private int findAttribute(String namespace,String attribute) {
		if (m_strings == null || attribute==null) {
			return -1;
		}
		int name = m_strings.find(attribute);
		if (name == -1) {
			return -1;
		}
		int uri = (namespace != null)?
				m_strings.find(namespace):
				-1;
		for (int o=0; o!=m_attributes.length; ++o) {
			if (name == m_attributes[o+ATTRIBUTE_IX_NAME] &&
					(uri == -1 || uri == m_attributes[o+ATTRIBUTE_IX_NAMESPACE_URI]))
			{
				return o/ATTRIBUTE_LENGHT;
			}
		}
		return -1;
	}

	private void resetEventInfo() {
		m_event = -1;
		m_lineNumber = -1;
		m_name =- 1;
		m_namespaceUri = -1;
		m_attributes = null;
		m_idAttribute =- 1;
		m_classAttribute =- 1;
		m_styleAttribute = -1;
	}

	private void doNext() throws IOException {
		if (m_strings == null) {
			ChunkUtil.readCheckType(m_reader,CHUNK_AXML_FILE);
			m_reader.skipInt();
			m_strings = StringBlock.read(m_reader);
			m_namespaces.increaseDepth();
			m_operational = true;
		}

		if (m_event == END_DOCUMENT) {
			return;
		}

		int event = m_event;
		resetEventInfo();

		while (true) {
			if (m_decreaseDepth) {
				m_decreaseDepth = false;
				m_namespaces.decreaseDepth();
			}

			if (event == END_TAG &&
					m_namespaces.getDepth() == 1 &&
					m_namespaces.getCurrentCount() == 0)
			{
				m_event = END_DOCUMENT;
				break;
			}

			int chunkType;
			if (event == START_DOCUMENT) {
				chunkType = CHUNK_XML_START_TAG;
			} else {
				chunkType = m_reader.readInt();
			}

			if (chunkType == CHUNK_RESOURCEIDS) {
				int chunkSize = m_reader.readInt();
				if (chunkSize < 8 || (chunkSize%4) != 0) {
					throw new IOException("Invalid resource ids size ("+chunkSize+").");
				}
				m_resourceIDs = m_reader.readIntArray(chunkSize/4-2);
				continue;
			}

			if (chunkType<CHUNK_XML_FIRST || chunkType>CHUNK_XML_LAST) {
				throw new IOException("Invalid chunk type ("+chunkType+").");
			}

			if (chunkType == CHUNK_XML_START_TAG && event == -1) {
				m_event = START_DOCUMENT;
				break;
			}

			m_reader.skipInt();
			int lineNumber = m_reader.readInt();
			m_reader.skipInt();

			if (chunkType == CHUNK_XML_START_NAMESPACE || chunkType == CHUNK_XML_END_NAMESPACE) {
				if (chunkType == CHUNK_XML_START_NAMESPACE) {
					int prefix = m_reader.readInt();
					int uri = m_reader.readInt();
					m_namespaces.push(prefix,uri);
				} else {
					m_reader.skipInt();
					m_reader.skipInt();
					m_namespaces.pop();
				}
				continue;
			}

			m_lineNumber = lineNumber;

			if (chunkType == CHUNK_XML_START_TAG) {
				m_namespaceUri = m_reader.readInt();
				m_name = m_reader.readInt();
				m_reader.skipInt();
				int attributeCount = m_reader.readInt();
				m_idAttribute = (attributeCount >>> 16)-1;
				attributeCount&=0xFFFF;
				m_classAttribute = m_reader.readInt();
				m_styleAttribute = (m_classAttribute >>> 16)-1;
				m_classAttribute = (m_classAttribute & 0xFFFF)-1;
				m_attributes = m_reader.readIntArray(attributeCount*ATTRIBUTE_LENGHT);
				for (int i=ATTRIBUTE_IX_VALUE_TYPE; i<m_attributes.length;) {
					m_attributes[i] = (m_attributes[i] >>> 24);
					i+=ATTRIBUTE_LENGHT;
				}
				m_namespaces.increaseDepth();
				m_event = START_TAG;
				break;
			}

			if (chunkType == CHUNK_XML_END_TAG) {
				m_namespaceUri = m_reader.readInt();
				m_name = m_reader.readInt();
				m_event = END_TAG;
				m_decreaseDepth=true;
				break;
			}

			m_name = m_reader.readInt();
			m_reader.skipInt();
			m_reader.skipInt();
			m_event = TEXT;
			break;
		}
	}

}
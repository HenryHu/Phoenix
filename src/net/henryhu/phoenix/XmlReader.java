package net.henryhu.phoenix;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;

import android.util.Log;
import android.util.Xml;


public class XmlReader {
	public List<PhoneticPair> readXML(InputStream inStream) {
		XmlPullParser parser = Xml.newPullParser();
		try {
			parser.setInput(inStream, "utf-8");
			int eventType = parser.getEventType();
			PhoneticPair currentPair = null;
			List<PhoneticPair> pairs = null;
			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					pairs = new ArrayList<PhoneticPair>();
					break;
				case XmlPullParser.START_TAG:
					String name = parser.getName();
					if (name.equalsIgnoreCase("pair")) {
						currentPair = new PhoneticPair();
					} else if (currentPair != null) {
						if (name.equalsIgnoreCase("phonetic")) {
							currentPair.setPhonetic(parser.nextText());
						} else if (name.equalsIgnoreCase("chars")) {
							currentPair.setChars(parser.nextText());
						}
					}
					break;
				case XmlPullParser.END_TAG:
					if (parser.getName().equalsIgnoreCase("pair") && currentPair != null) {
						pairs.add(currentPair);
						currentPair = null;
					}
					break;
				}
				eventType = parser.next();
			}
			inStream.close();
			return pairs;
		} catch (Exception e) {
			Log.e("xmlReader", e.getMessage());
		}
		return null;
	}
} 
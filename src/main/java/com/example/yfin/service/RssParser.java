package com.example.yfin.service;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RssParser {
    public static List<Map<String, Object>> parse(String xml) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");
            List<Map<String, Object>> out = new ArrayList<>(items.getLength());
            for (int i = 0; i < items.getLength(); i++) {
                Element e = (Element) items.item(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", text(e, "title"));
                m.put("link", text(e, "link"));
                m.put("pubDate", text(e, "pubDate"));
                out.add(m);
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String text(Element e, String tag) {
        NodeList nl = e.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent();
    }
}



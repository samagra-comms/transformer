package com.uci.transformer.odk.utilities;

import com.thoughtworks.xstream.XStream;

import android.util.Pair;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Builder
public class FormUpdation {
    String formPath;
    Document instanceData;
    private XStream magicApi;
    File formXml;
    static TransformerFactory transformerFactory = TransformerFactory.newInstance();
    static Transformer transformer;
    static StringWriter writer = new StringWriter();
    static StreamResult sr = new StreamResult(writer);
    static FileInputStream fis;

    static {
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void init() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        formXml = new File(formPath);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        this.instanceData = builder.parse(formXml);
        this.instanceData.getDocumentElement().normalize();
    }

    public String getXML() throws TransformerException {
        DOMSource source = new DOMSource(this.instanceData);
        transformer.transform(source, sr);
        return writer.toString();
    }

    public InputStream getInputStream() throws TransformerException {
        DOMSource source = new DOMSource(this.instanceData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Result outputTarget = new StreamResult(outputStream);
        transformer.transform(source, outputTarget);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }
    
    public void addSelectOneOptions(ArrayList<Item> options, String key){
        NodeList e =  this.instanceData.getElementsByTagName("select1");
        boolean found = false;
        Node selectElement = null;
        NodeList translations = this.instanceData.getElementsByTagName("translation");
        
        ArrayList rArr = new ArrayList();
        for(int i=0; i< e.getLength(); i++){
            if(e.item(i).getAttributes().getNamedItem("ref").getChildNodes().item(0).toString().contains(key)){
        		found = true;
                selectElement = e.item(i);
                log.info("Select element: "+selectElement);
                for (Item item: options){
                	log.info("IN-3");
                	String label = item.value + " " + item.label;
                    Node itemNodeClone = selectElement.getFirstChild().getNextSibling().cloneNode(true);
                    if(itemNodeClone.getChildNodes().item(0).getFirstChild() == null) {
                    	itemNodeClone.getChildNodes().item(0).appendChild(this.instanceData.createTextNode(label));
                    } else {
                    	itemNodeClone.getChildNodes().item(0).getFirstChild().setNodeValue(label);
                    }
                    if(itemNodeClone.getChildNodes().item(1).getFirstChild() == null) {
                    	itemNodeClone.getChildNodes().item(1).appendChild(this.instanceData.createTextNode(item.value + " " + item.label));
                    } else {
                    	itemNodeClone.getChildNodes().item(1).getFirstChild().setNodeValue(item.value);
                    }
                    
                    selectElement.appendChild(itemNodeClone);
                    
                    try {
                    	String refValue = itemNodeClone.getChildNodes().item(0).getAttributes().getNamedItem("ref").getChildNodes().item(0).getNodeValue();
                        refValue = refValue.replace("jr:itext('", "");
                        refValue = refValue.replace("')", "");
                        log.info("refValue: "+refValue);
                        
                        log.info("translations.getLength(): "+translations.getLength());
                        for(int j =0; j < translations.getLength();j++) {
                    		NodeList childs = translations.item(j).getChildNodes();
                    		log.info("childs.getLength(): "+childs.getLength());
                    		for(int k =0; k < childs.getLength();k++) {
                    			if(childs.item(k).getAttributes().getNamedItem("id") != null
                    				&& childs.item(k).getAttributes().getNamedItem("id").getChildNodes().item(0).getNodeValue().equals(refValue)) {
                    				Node clone = childs.item(k).cloneNode(true);
//                    				log.info("child value: "+childs.item(k).getFirstChild().getFirstChild().getNodeValue());
                    					
                    				clone.getFirstChild().getFirstChild().setNodeValue(label);
//                    				log.info("child value: "+clone.getFirstChild().getFirstChild().getNodeValue());
                    					
                    				translations.item(j).appendChild(clone);
                    				
                    				translations.item(j).removeChild(childs.item(k));
                    				
                    				rArr.add(k);
                    				break;
                    			}
                    		}
                    	}
                    } catch (Exception ex) {
                    	log.info("Language translation append exception: "+ex.getMessage());
                    }
                }
                
                // Delete the dummy one.
                selectElement.removeChild(selectElement.getFirstChild().getNextSibling());
                
//                try {
//                	log.info("translations.getLength(): "+translations.getLength());
//                	for(int x = 0; x < translations.getLength(); x++) {
//                		
//                		log.info("Delete "+x);
//                		log.info("rArr size: "+rArr.size());
//                    	for (int y=0; y < rArr.size(); y++) {
//                    		log.info("Delete "+y+", node: "+rArr.get(y));
//                    		translations.item(x).removeChild(translations.item(x).getChildNodes().item((int) rArr.get(y)));
//                    	}
//                    }
//                } catch (Exception ex) {
//                	log.info("Delete dummy child: exception"+ex.getMessage());
//                }
                break;
            }
        }
    }
}

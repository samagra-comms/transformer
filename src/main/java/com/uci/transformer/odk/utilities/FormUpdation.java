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
        Node langClone = null;
        String dummyID = null;
        
        for(int i=0; i< e.getLength(); i++){
            if(e.item(i).getAttributes().getNamedItem("ref").getChildNodes().item(0).toString().contains(key)){
        		found = true;
                selectElement = e.item(i);
                Node itemNodeClone = selectElement.getFirstChild().getNextSibling().cloneNode(true);
                for (int a = 0; a < options.size(); a++){
                	Item item = options.get(a);
                	String label = item.value + " " + item.label;
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
                    
                    try {
                    	if(translations.getLength() > 0) {
                    		/* Set Dummy ID if empty */
                    		if(dummyID == null || dummyID.isEmpty()) {
                    			dummyID = itemNodeClone.getChildNodes().item(0).getAttributes().getNamedItem("ref").getChildNodes().item(0).getNodeValue();
    	                    	dummyID = dummyID.replace("jr:itext('", "").replace("')", "");	
                    		}
                    		
                    		/* Create New ID for translation choices & Ref for select choice*/
	                        String newID = dummyID;
	                        newID = newID.replace(":label", "")+item.value+":label";
	                        
	                        String newRefV = "jr:itext('"+newID+"')";
	                        log.info("dummyID: "+dummyID+", newID: "+newID+" ,newRefV: "+newRefV);
	                        
	                        /* Get translation clone of dummy select choice and remove them from xml
	                         * Append translation clone with new id */
	                        for(int j =0; j < translations.getLength();j++) {
	                        	log.info("Language: "+translations.item(j).getAttributes().getNamedItem("lang").getChildNodes().item(0).getNodeValue());
                            	
	                        	NodeList childs = translations.item(j).getChildNodes();
		                    	/* Loop child nodes to find dummy element for clone & removal */
	                        	for(int k =0; k < childs.getLength();k++) {
		                    			/* Find the child with ref value from the last item */
		                    		if(childs.item(k).getAttributes().getNamedItem("id") != null
		                    				&& childs.item(k).getAttributes().getNamedItem("id").getChildNodes().item(0).getNodeValue().equals(dummyID)) {
//		                    			/* Set language clone */
		                    			if(langClone == null) {
		                    				langClone = childs.item(k).cloneNode(true);
		                    			}
		                    			/* Delete child with dummy id */
		                    			log.info("Deleting child with id: "+dummyID);
		                    			translations.item(j).removeChild(childs.item(k));
		                    		}
		                    	}
	                    		
	                    		Node clone = langClone;
	                    		clone.getFirstChild().getFirstChild().setNodeValue(label);
                            	clone.getAttributes().getNamedItem("id").getChildNodes().item(0).setNodeValue(newID);
                            	log.info("langClone label: "+label+", newID: "+newID);
                            	translations.item(j).appendChild(clone);
	                        }
	                        
	                        itemNodeClone.getChildNodes().item(0).getAttributes().getNamedItem("ref").getChildNodes().item(0).setNodeValue(newRefV);
                        }
                    } catch (Exception ex) {
                    	log.info("Language translation append exception: "+ex.getMessage());
                    }
                    
                    selectElement.appendChild(itemNodeClone);
                }
                
                // Delete the dummy one.
                selectElement.removeChild(selectElement.getFirstChild().getNextSibling());
                break;
            }
        }
    }
}

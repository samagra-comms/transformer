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

    public void addTranslationLabels(ArrayList<Item> options, String key) {
        // Get all translations by language
        // Get the current dummy option in the page

        // Step 1
        // Iterate over translations
        // Iterate over options
        // For a translation in X language add the following =>
                /*  <text id="/data/group_matched_vacancies/initial_interest/dummy15:label">
                        <value>15 Delivery Executive at DM Labsy</value>
                    </text>
                 */
        // Remove the original translations
        NodeList translations = this.instanceData.getElementsByTagName("translation");
        NodeList allSelectTags =  this.instanceData.getElementsByTagName("select1");

        for(int i=0; i< allSelectTags.getLength(); i++){
            if(allSelectTags.item(i).getAttributes().getNamedItem("ref").getChildNodes().item(0).toString().contains(key)){
                Node selectElement = allSelectTags.item(i);
                Node itemNodeClone = selectElement.getFirstChild().getNextSibling().cloneNode(true);
                /* Set Dummy ID if empty */
                // ref="jr:itext('/data/group_matched_vacancies/initial_interest/dummy15:label')
                // dummyID = /data/group_matched_vacancies/initial_interest/dummy15:label
                String dummyID = itemNodeClone.getChildNodes().item(0)
                        .getAttributes().getNamedItem("ref")
                        .getChildNodes().item(0).getNodeValue()
                        .replace("jr:itext('", "")
                        .replace("')", "");

                for(int j =0; j < translations.getLength(); j++) {
                    NodeList children = translations.item(j).getChildNodes();
                    Node translationNode = null;
                    boolean found = false;

                    /* Loop child nodes to find dummy element for clone & removal */
                    for(int k =0; k < children.getLength();k++) {
                        /* Find the child with ref value from the last item */
                        if(children.item(k).getAttributes().getNamedItem("id") != null
                                && children.item(k).getAttributes().getNamedItem("id").getChildNodes().item(0).getNodeValue().equals(dummyID)) {
                            /* Set language clone */
                            translationNode = children.item(k);
                            found = true;
                            break;
                        }
                    }

                    if(found) {
                        for (Item item : options) {
                            String label = item.value + " " + item.label;

                            /* Create New ID for translation choices & Ref for select choice*/
                            // ID = /data/group_matched_vacancies/initial_interest/<item.value>:label
                            String id = dummyID.replace(":label", "") + item.value + ":label";
                            String newRefV = "jr:itext('" + id + "')";

                            /* Get translation clone of dummy select choice and remove them from xml
                             * Append translation clone with new id */
                            log.info("Language: " + translations.item(j).getAttributes().getNamedItem("lang").getChildNodes().item(0).getNodeValue());

                            Node clone = translationNode.cloneNode(true);
                            clone.getFirstChild().getFirstChild().setNodeValue(label); // set value
                            clone.getAttributes().getNamedItem("id").getChildNodes().item(0).setNodeValue(id); // set id
                            translations.item(j).appendChild(clone);
                        }
//                        translations.item(j).removeChild(translationNode);
                    }
                }
            }
        }
    }

    public void addSelectOneOptions(ArrayList<Item> options, String key){
        NodeList e =  this.instanceData.getElementsByTagName("select1");
        boolean found = false;
        Node selectElement = null;

        ArrayList rArr = new ArrayList();

        // Step 2
        // Iterate over options
            // Add the following as a new item
            /*  <item>
                    <label ref="jr:itext('/data/group_matched_vacancies/initial_interest/dummy15:label')">15 Delivery Executive at DM Labsy</label>
                    <value>15</value>
                </item>
             */
        // Remove the original item

        for(int i=0; i< e.getLength(); i++){
            if(e.item(i).getAttributes().getNamedItem("ref").getChildNodes().item(0).toString().contains(key)){
        		found = true;
                selectElement = e.item(i);

                for (int a = 0; a < options.size(); a++){
                    Node itemNodeClone = selectElement.getFirstChild().getNextSibling().cloneNode(true);
                    String dummyID = itemNodeClone.getChildNodes().item(0)
                            .getAttributes().getNamedItem("ref")
                            .getChildNodes().item(0).getNodeValue()
                            .replace("jr:itext('", "")
                            .replace("')", "");
                	Item item = options.get(a);
                	String label = item.value + " " + item.label;
                    // ID = /data/group_matched_vacancies/initial_interest/<item.value>:label
                    String ref = "jr:itext('" + dummyID.replace(":label", "") + item.value + ":label')";

                    if(itemNodeClone.getChildNodes().item(0).getFirstChild() == null) {
                    	itemNodeClone.getChildNodes().item(0).appendChild(this.instanceData.createTextNode(label));
                    } else {
                    	itemNodeClone.getChildNodes().item(0).getFirstChild().setNodeValue(label);
                    }

                    itemNodeClone.getChildNodes().item(0)
                            .getAttributes().getNamedItem("ref")
                            .getChildNodes().item(0).setNodeValue(ref);

                    if(itemNodeClone.getChildNodes().item(1).getFirstChild() == null) {
                    	itemNodeClone.getChildNodes().item(1).appendChild(this.instanceData.createTextNode(item.value + " " + item.label));
                    } else {
                    	itemNodeClone.getChildNodes().item(1).getFirstChild().setNodeValue(item.value);
                    }

                    selectElement.appendChild(itemNodeClone);
                }
                

                addTranslationLabels(options, key);
                
                // Delete the dummy one.
                selectElement.removeChild(selectElement.getFirstChild().getNextSibling());
                
                break;
            }
        }
    }
}
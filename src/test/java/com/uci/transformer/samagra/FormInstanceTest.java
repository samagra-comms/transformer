package com.uci.transformer.samagra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static java.util.UUID.randomUUID;

class FormInstanceTest {

	@Test
    public void initFromInstanceTest() throws JsonProcessingException {
        String instanceString = "<?xml version='1.0' ?><data id=\"samagra_workflows_form_updated_1\" xmlns:ev=\"http://www.w3.org/2001/xml-events\" xmlns:orx=\"http://openrosa.org/xforms\" xmlns:odk=\"http://www.opendatakit.org/xforms\" xmlns:h=\"http://www.w3.org/1999/xhtml\" xmlns:jr=\"http://openrosa.org/javarosa\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><application_process><member_name /><form_intro /><team_name /><manager_name /><manager_contact /><engagement_owner_name /><engagement_owner_number /><leave_balance /><preferences>2</preferences></application_process><leave_app><type_of_leave>1</type_of_leave><reason>Personal travel</reason><start_date_leave>12-09-2020</start_date_leave><end_date_leave>14-09-2020</end_date_leave><number_of_working_days>2</number_of_working_days><leave_applied_message /></leave_app><meta><instanceID>uuid:a0450024-16c1-4820-ae2a-8feeae833228</instanceID></meta></data>";
//        XStream magicApi = new XStream();
        XStream magicApi = new XStream(new StaxDriver()) {
            @Override
            protected void setupConverters() {
            }
        };
        magicApi.registerConverter(new MapEntryConverter());
        magicApi.alias("data", Map.class);
        Map<String, Object> map2 = (Map<String, Object>) magicApi.fromXML(instanceString);
        String xml = "<?xml version='1.0' ?>" + magicApi.toXML(map2);
        UUID instanceID = randomUUID();
        System.out.println(instanceID.toString());
    }

}
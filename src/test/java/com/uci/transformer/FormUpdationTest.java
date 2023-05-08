package com.uci.transformer;

import com.uci.transformer.odk.MenuManager;
import com.uci.transformer.odk.ServiceResponse;
import com.uci.transformer.odk.utilities.FormUpdation;
import com.uci.transformer.odk.utilities.Item;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;

import static com.uci.transformer.odk.ODKConsumerReactive.getFormPath;
@Slf4j
public class FormUpdationTest {
    @Test
    public void testAddSelectOneOptions() throws Exception {
        String formID = "rozgar-candidate-registration";
        String formPath = getFormPath(formID);
        FormUpdation ss = FormUpdation.builder().formPath(formPath).build();
        ss.init();
        ArrayList<Item> options = new ArrayList<>();
        options.add(Item.builder().label("test-custom-label").value("14").build());
        log.info("Final XML:: start");
        ss.addSelectOneOptions(options, "");
        String xml = ss.getXML();
        log.info("Final XML:: done");
    }
}

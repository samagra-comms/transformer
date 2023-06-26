package com.uci.transformer.odk.persistance;

import io.jsondb.JsonDBTemplate;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JsonDBTest {

    @Test
    public void testJSONDBSetup() {
        JsonDBTemplate jsonDBTemplate = JsonDB.getInstance().getDB();
        assertNotNull(jsonDBTemplate);
    }
}
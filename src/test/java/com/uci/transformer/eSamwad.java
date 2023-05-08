package com.uci.transformer;

import com.uci.utils.service.UserService;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.List;

public class eSamwad {

    @Test
    public void testUsersForESamwad() throws Exception {
        List<String> users = new UserService().findUsersForESamwad("Test 4/11");
        System.out.println(users.size());
        Assert.assertTrue(users.size() > 52000);
    }

}

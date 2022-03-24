package com.uci.transformer.controllers;


import com.uci.utils.service.UserService;
import com.uci.transformer.samagra.LeaveManager;
import io.fusionauth.domain.User;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Log
@RestController
public class InternalBot {
	
	@Autowired
	UserService userService;

    @SneakyThrows
    @GetMapping("/delete-leave")
    public ResponseEntity<User> deleteLeave(@RequestParam(value = "userEmail", required = false) String userEmail,
                                           @RequestParam(value = "workingDays", required = false) int workingDays) {
        try{
            User user = userService.findByEmail(userEmail);
            User userResponse = LeaveManager.builder().user(user).build().deleteLeaves(workingDays);
            return ResponseEntity.status(HttpStatus.OK).body(userResponse);
        } catch(Exception e){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @SneakyThrows
    @GetMapping("/approve-leave")
    public ResponseEntity<User> approveLeave(@RequestParam(value = "userEmail", required = false) String userEmail,
                                         @RequestParam(value = "workingDays", required = false) int workingDays) {
        try{
            User user = userService.findByEmail(userEmail);
            User userResponse = LeaveManager.builder().user(user).build().addLeaves(workingDays);
            return ResponseEntity.status(HttpStatus.OK).body(userResponse);
        } catch(Exception e){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @SneakyThrows
    @GetMapping("/reject-leave")
    public ResponseEntity<User> rejectLeave(@RequestParam(value = "userEmail", required = false) String userEmail,
                                         @RequestParam(value = "workingDays", required = false) int workingDays) {
        try{
            User user = userService.findByEmail(userEmail);
            User userResponse = LeaveManager.builder().user(user).build().deleteLeaves(workingDays);
            return ResponseEntity.status(HttpStatus.OK).body(userResponse);
        } catch(Exception e){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}

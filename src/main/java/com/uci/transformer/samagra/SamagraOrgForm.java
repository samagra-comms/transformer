package com.uci.transformer.samagra;

import com.uci.utils.service.UserService;
import com.thoughtworks.xstream.XStream;
import io.fusionauth.domain.User;
import lombok.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Map;
import java.util.UUID;

import static java.util.UUID.randomUUID;

@Builder
@Getter
@Setter
public class SamagraOrgForm {
    Map<String, Object> instanceData;
    User user;
    User engagementOwner;
    User manager;
    User programCoordinator;
    UserService userService;

    public void init() {
        getEngagementOwner();
        getManager();
    }

    public Map<String, Object> parse(String xml) {
        XStream magicApi = new XStream();
        magicApi.registerConverter(new MapEntryConverter());
        magicApi.alias("data", Map.class);
        this.instanceData = (Map<String, Object>) magicApi.fromXML(xml);
        return this.instanceData;
    }

    public String getStartDate() {
        return (String) ((Map<String, Object>) this.instanceData.get("leave_app")).get("start_date_leave");
    }

    public String getEndDate() {
        return (String) ((Map<String, Object>) this.instanceData.get("leave_app")).get("end_date_leave");
    }

    public String getNumberOfWorkingDays() {
        return (String) ((Map<String, Object>) this.instanceData.get("leave_app")).get("number_of_working_days");
    }

    public String getReason() {
        return (String) ((Map<String, Object>) this.instanceData.get("leave_app")).get("leave_type_text");
    }

    public User getEngagementOwner() {
        if (this.engagementOwner == null) {
            Boolean isAssociate = userService.isAssociate(this.user);
            String programConstruct = userService.getProgramConstruct(this.user);
            if (isAssociate && programConstruct.equals("2")){
                this.engagementOwner = userService.getManager(this.user);
            }else{
                this.engagementOwner = userService.getEngagementOwner(this.user);
            }
        }
        return this.engagementOwner;
    }

    public String updateLeaves() {
        ((Map<String, Object>) this.instanceData.get("application_process")).put("leave_balance", this.user.data.get("leavesAvailable"));
        XStream magicApi = new XStream();
        magicApi.registerConverter(new MapEntryConverter());
        magicApi.alias("data", Map.class);
        return magicApi.toXML(this.instanceData);
    }

    public User getManager() {
        if (this.manager == null) {
            Boolean isAssociate = userService.isAssociate(this.user);
            String programConstruct = userService.getProgramConstruct(this.user);
            if (isAssociate && programConstruct.equals("2")){
                this.manager = userService.getProgramCoordinator(this.user);
            }else{
                this.manager = userService.getManager(this.user);
            }
        }

        return this.manager;
    }

    public String getInitialValue() {
        init();
        UUID instanceID = randomUUID();

        String instanceXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<data>\n" +
                "    <meta><instanceID>uuid:%s</instanceID></meta>\n" +
                "    <application_process>\n" +
                "        <engagement_owner_name>%s</engagement_owner_name>\n" +
                "        <engagement_owner_number>%s</engagement_owner_number>\n" +
                "        <manager_name>%s</manager_name>\n" +
                "        <manager_contact>%s</manager_contact>\n" +
                "        <member_name>%s</member_name>\n" +
                "        <member_name_edited>%s</member_name_edited>\n" +
                "        <status>3</status>\n" +
                "        <leave_balance>%s</leave_balance>\n" +
                "        <team_name>%s</team_name>\n" +
                "        <filling_date>%s</filling_date>\n" +
                "        <preferences />\n" +
                "        <form_intro />\n" +
                "    </application_process>\n" +
                "</data>";

        DateTime dt = DateTime.now();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("dd-MM-yyyy");

        String memberNameEdited = this.user.fullName.replaceAll(" ", "%20");

        return String.format(instanceXML, instanceID.toString(),
                this.engagementOwner.fullName, this.engagementOwner.mobilePhone,
                this.manager.fullName, this.manager.mobilePhone,
                this.user.fullName, memberNameEdited, this.user.data.get("leavesAvailable"), this.user.data.get("engagement"), fmt.print(dt));
    }

    public String getMissedFlightPNR() {
        return (String) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("air_ticket")).get("missed_flight")).get("PNR_number");
    }

    public String getCancellationFlightPNR() {
        return (String) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("air_ticket")).get("air_ticket_cancellation")).get("PNR_number");
    }

    public String getAmmendmentFlightPNR() {
        return (String) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("air_ticket")).get("air_ticket_amendment")).get("air_pnr_number");
    }

    public Map<String, Object> getAirOneWayData() {
        return (Map<String, Object>) ((Map<String, Object>) this.instanceData.get("air_ticket")).get("air_ticket_new");
    }

    public Map<String, Object> getTrainOneWayData() {
        return (Map<String, Object>) ((Map<String, Object>) this.instanceData.get("train_ticket")).get("new_train_ticket");
    }

    public Map<String, Object> getTrainTwoWayData() {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("train_ticket")).get("new_train_ticket")).get("new_train_ticket_round");
    }

    public String getTrainCancellationPNR() {
        return (String) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("train_ticket")).get("train_ticket_cancellation")).get("PNR_number");
    }

    public String getTrainMissedPNR() {
        return (String) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("train_ticket")).get("train_missed")).get("PNR_number");
    }

    public Map<String, Object> getAirTwoWayData() {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) this.instanceData.get("air_ticket")).get("air_ticket_new")).get("air_ticket_round");
    }

    public String getReasonForLeave() {
        return (String) ((Map<String, Object>) this.instanceData.get("leave_app")).get("reason");
    }
}

package com.flowzo.callingagent.service.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowzo.callingagent.entity.CallRecord;
import com.flowzo.callingagent.entity.Lead;
import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.ConversationStep;
import com.flowzo.callingagent.enums.LeadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleBasedConversationEngineTest {

    private RuleBasedConversationEngine engine;
    private CallRecord call;

    @BeforeEach
    void setUp() {
        engine = new RuleBasedConversationEngine();
        Lead lead = new Lead();
        lead.setName("Priya");
        lead.setCompanyName("Northwind");
        lead.setPhoneNumber("+91111");
        lead.setStatus(LeadStatus.CONTACTED);

        call = new CallRecord();
        call.setLead(lead);
        call.setCurrentStep(ConversationStep.AWAIT_INTEREST);
    }

    @Test
    void greetingMentionsFlowzoAndLeadName() {
        String greeting = engine.buildGreeting(call.getLead());
        assertThat(greeting).contains("Priya").contains("Flowzo CRM").contains("Northwind");
    }

    @Test
    void interestedPathAdvancesToTeamSize() {
        var result = engine.processCustomerReply(call, "Yes, interested");
        assertThat(result.nextStep()).isEqualTo(ConversationStep.AWAIT_TEAM_SIZE);
        assertThat(call.getInterestedInCrm()).isTrue();
    }

    @Test
    void aPlainNoDisqualifiesTheLeadWithAnAcknowledgement() {
        var result = engine.processCustomerReply(call, "No");

        assertThat(result.outcome()).isEqualTo(CallOutcome.NOT_INTERESTED);
        assertThat(result.completed()).isTrue();
        assertThat(result.aiMessage()).startsWith("Okay");
        assertThat(call.getInterestedInCrm()).isFalse();
        assertThat(call.getLead().getStatus()).isEqualTo(LeadStatus.DISQUALIFIED);
    }

    @Test
    void agreeingWithoutSayingYesIsNotQuestioned() {
        var result = engine.processCustomerReply(call, "Tell me more");

        assertThat(result.aiMessage()).doesNotContain("Just to confirm");
        assertThat(result.nextStep()).isEqualTo(ConversationStep.AWAIT_TEAM_SIZE);
        assertThat(call.getInterestedInCrm()).isTrue();
    }

    @Test
    void interestIsConfirmedOnceThenTreatedAsARefusal() {
        var confirm = engine.processCustomerReply(call, "What is this regarding");
        assertThat(confirm.nextStep()).isEqualTo(ConversationStep.AWAIT_INTEREST);
        assertThat(confirm.aiMessage()).contains("Just to confirm");
        assertThat(confirm.completed()).isFalse();

        // Asking a third time would loop the call forever, so the second unclear answer ends it.
        var closed = engine.processCustomerReply(call, "What is this regarding");
        assertThat(closed.completed()).isTrue();
        assertThat(closed.outcome()).isEqualTo(CallOutcome.NOT_INTERESTED);
        assertThat(closed.aiMessage()).startsWith("Okay");
        assertThat(call.getLead().getStatus()).isEqualTo(LeadStatus.DISQUALIFIED);
    }

    @Test
    void confirmingInterestLeavesAFullRepromptBudgetForTheNextQuestion() {
        engine.processCustomerReply(call, "What is this regarding");
        engine.processCustomerReply(call, "Yes, I am interested");

        var reprompt = engine.processCustomerReply(call, "I cannot say right now");

        assertThat(reprompt.nextStep()).isEqualTo(ConversationStep.AWAIT_TEAM_SIZE);
        assertThat(reprompt.aiMessage()).contains("approximate number");
    }

    @Test
    void detailsVolunteeredEarlyAreNotAskedAgain() {
        engine.processCustomerReply(call, "Yes, interested");

        var result = engine.processCustomerReply(call, "We are 12 people and we track leads in Excel today");

        assertThat(call.getSalesTeamSize()).isEqualTo(12);
        assertThat(call.getLeadManagementMethod()).contains("Excel");
        assertThat(result.nextStep()).isEqualTo(ConversationStep.AWAIT_DEMO);
        assertThat(result.aiMessage()).contains("demonstration");
    }

    @Test
    void unansweredQuestionIsAskedOnceThenSkipped() {
        engine.processCustomerReply(call, "Yes, interested");

        var reprompt = engine.processCustomerReply(call, "I cannot say right now");
        assertThat(reprompt.nextStep()).isEqualTo(ConversationStep.AWAIT_TEAM_SIZE);
        assertThat(reprompt.aiMessage()).contains("approximate number");

        var movedOn = engine.processCustomerReply(call, "I still cannot say");
        assertThat(call.getSalesTeamSize()).isNull();
        assertThat(movedOn.nextStep()).isEqualTo(ConversationStep.AWAIT_LEAD_MANAGEMENT);
        assertThat(movedOn.aiMessage()).contains("manage your leads");
    }

    @Test
    void demoRequestedWhileAnsweringAnotherQuestionClosesTheCall() {
        engine.processCustomerReply(call, "Yes, interested");
        engine.processCustomerReply(call, "We use spreadsheets");

        var result = engine.processCustomerReply(call, "Just book me a demo on Friday afternoon");

        assertThat(call.getWantsDemo()).isTrue();
        assertThat(call.getSalesTeamSize()).isNull();
        assertThat(result.completed()).isTrue();
        assertThat(result.outcome()).isEqualTo(CallOutcome.DEMO_REQUESTED);
    }

    @Test
    void callbackRequestEndsTheCallAtAnyStep() {
        call.setCurrentStep(ConversationStep.AWAIT_TEAM_SIZE);

        var result = engine.processCustomerReply(call, "I am busy, please call back later");

        assertThat(result.completed()).isTrue();
        assertThat(result.outcome()).isEqualTo(CallOutcome.CALLBACK_REQUESTED);
        assertThat(call.getLead().getStatus()).isEqualTo(LeadStatus.CONTACTED);
    }

    @Test
    void demoRequestSetsOutcome() {
        call.setCurrentStep(ConversationStep.AWAIT_DEMO);
        call.setInterestedInCrm(true);
        call.setSalesTeamSize(8);
        call.setLeadManagementMethod("Excel");

        var result = engine.processCustomerReply(call, "Yes please schedule a demo");
        assertThat(result.completed()).isTrue();
        assertThat(result.outcome()).isEqualTo(CallOutcome.DEMO_REQUESTED);
        assertThat(engine.buildSummary(call)).contains("Excel").contains("DEMO_REQUESTED");
    }
}

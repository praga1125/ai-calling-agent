package com.flowzo.callingagent.service.conversation;

import com.flowzo.callingagent.entity.CallRecord;
import com.flowzo.callingagent.entity.Lead;
import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.ConversationStep;
import com.flowzo.callingagent.enums.LeadStatus;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rule-based Flowzo CRM sales conversation.
 * Avoids repeating answered questions and drives a fixed qualification path.
 */
@Component
public class RuleBasedConversationEngine {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,4})");
    private static final int MAX_REPROMPTS = 1;

    public String buildGreeting(Lead lead) {
        String company = lead.getCompanyName() == null || lead.getCompanyName().isBlank()
                ? "your company"
                : lead.getCompanyName();
        return "Hello " + lead.getName() + ", this is Maya, a virtual assistant from Flowzo CRM. "
                + "I am calling to learn whether " + company
                + " would be interested in a CRM solution to manage sales leads. "
                + "Are you interested in exploring a CRM with us today?";
    }

    public EngineResult processCustomerReply(CallRecord call, String customerText) {
        String text = customerText == null ? "" : customerText.trim();
        ConversationStep step = call.getCurrentStep();

        return switch (step) {
            case AWAIT_INTEREST -> handleInterest(call, text);
            case AWAIT_TEAM_SIZE, AWAIT_LEAD_MANAGEMENT, AWAIT_DEMO -> handleQualification(call, text, step);
            case CLOSING, COMPLETED, GREETING -> new EngineResult(
                    "Thanks again for your time. Our team will follow up if needed. Goodbye!",
                    ConversationStep.COMPLETED,
                    CallOutcome.COMPLETED,
                    true
            );
        };
    }

    private EngineResult handleInterest(CallRecord call, String text) {
        if (isCallback(text)) {
            return complete(call, "No problem. I will note a callback request and have a Flowzo specialist reach out soon. Goodbye!",
                    CallOutcome.CALLBACK_REQUESTED, LeadStatus.CONTACTED);
        }
        if (isNegative(text)) {
            call.setInterestedInCrm(false);
            return complete(call, "Okay, thank you for letting me know. If your needs change, Flowzo CRM "
                            + "is here to help. Have a great day!",
                    CallOutcome.NOT_INTERESTED, LeadStatus.DISQUALIFIED);
        }
        if (!isPositive(text) && !looksUncertain(text)) {
            // Confirm once. A second unclear answer is a no in practice, and asking a third time
            // would loop the call forever on the same question.
            if (call.getRepromptCount() < MAX_REPROMPTS) {
                call.setRepromptCount(call.getRepromptCount() + 1);
                return question(call, "Just to confirm, are you interested in learning about a CRM solution from Flowzo?",
                        ConversationStep.AWAIT_INTEREST, CallOutcome.IN_PROGRESS);
            }
            call.setInterestedInCrm(false);
            call.setRepromptCount(0);
            return complete(call, "Okay, I will not take any more of your time. If a CRM becomes useful "
                            + "later, Flowzo is here to help. Have a great day!",
                    CallOutcome.NOT_INTERESTED, LeadStatus.DISQUALIFIED);
        }

        call.setRepromptCount(0);
        call.setInterestedInCrm(true);
        call.setOutcome(CallOutcome.INTERESTED);
        call.getLead().setStatus(LeadStatus.CONTACTED);
        captureVolunteeredDetails(call, text, ConversationStep.AWAIT_INTEREST);
        return ask(call, "Great to hear. ");
    }

    /**
     * Handles every qualification question with the same loop: record what the reply answers, record
     * anything else the customer volunteered, then ask for the first detail still missing. A question
     * is repeated at most {@value #MAX_REPROMPTS} time before the agent moves on, so a customer who
     * keeps side-stepping never gets stuck on the same question.
     */
    private EngineResult handleQualification(CallRecord call, String text, ConversationStep step) {
        if (isCallback(text)) {
            LeadStatus status = step == ConversationStep.AWAIT_DEMO ? LeadStatus.QUALIFIED : LeadStatus.CONTACTED;
            return complete(call, "Understood. I will note a callback so a Flowzo specialist can reach you at a better time. Goodbye!",
                    CallOutcome.CALLBACK_REQUESTED, status);
        }

        boolean answered = captureAnswer(call, text, step);
        captureVolunteeredDetails(call, text, step);

        if (!answered) {
            if (call.getRepromptCount() < MAX_REPROMPTS) {
                call.setRepromptCount(call.getRepromptCount() + 1);
                return question(call, reprompt(step), step, call.getOutcome());
            }
            markSkipped(call, step);
        }

        call.setRepromptCount(0);
        return ask(call, "");
    }

    /** @return true when the reply answered the question that was asked */
    private boolean captureAnswer(CallRecord call, String text, ConversationStep step) {
        switch (step) {
            case AWAIT_TEAM_SIZE -> {
                OptionalInt size = extractNumber(text);
                if (size.isEmpty() || (mentionsScheduling(text) && !mentionsTeam(text))) {
                    return false;
                }
                call.setSalesTeamSize(size.getAsInt());
                return true;
            }
            case AWAIT_LEAD_MANAGEMENT -> {
                if (text.isBlank() || isFiller(text)) {
                    return false;
                }
                call.setLeadManagementMethod(text);
                return true;
            }
            case AWAIT_DEMO -> {
                if (isNegative(text)) {
                    call.setWantsDemo(false);
                    return true;
                }
                if (isPositive(text) || text.toLowerCase(Locale.ROOT).contains("demo")) {
                    call.setWantsDemo(true);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** Records details the customer offers before being asked, so those questions can be skipped. */
    private void captureVolunteeredDetails(CallRecord call, String text, ConversationStep step) {
        if (text.isBlank()) {
            return;
        }
        if (call.getLeadManagementMethod() == null
                && step != ConversationStep.AWAIT_LEAD_MANAGEMENT
                && mentionsLeadTooling(text)) {
            call.setLeadManagementMethod(text);
        }
        if (call.getSalesTeamSize() == null
                && step != ConversationStep.AWAIT_TEAM_SIZE
                && mentionsTeam(text)) {
            extractNumber(text).ifPresent(call::setSalesTeamSize);
        }
        if (call.getWantsDemo() == null
                && step != ConversationStep.AWAIT_DEMO
                && text.toLowerCase(Locale.ROOT).contains("demo")
                && !isNegative(text)) {
            call.setWantsDemo(true);
        }
    }

    /** Asks for the first qualification detail still missing, or closes the call when none are left. */
    private EngineResult ask(CallRecord call, String prefix) {
        if (needs(call, ConversationStep.AWAIT_TEAM_SIZE, call.getSalesTeamSize())) {
            return question(call, prefix + "Roughly how many people are on your sales team?",
                    ConversationStep.AWAIT_TEAM_SIZE);
        }
        if (needs(call, ConversationStep.AWAIT_LEAD_MANAGEMENT, call.getLeadManagementMethod())) {
            return question(call, prefix + "How do you currently manage your leads — for example Excel, "
                    + "another CRM, or something else?", ConversationStep.AWAIT_LEAD_MANAGEMENT);
        }
        if (needs(call, ConversationStep.AWAIT_DEMO, call.getWantsDemo())) {
            return question(call, prefix + "Would you like to schedule a short product demonstration of Flowzo CRM?",
                    ConversationStep.AWAIT_DEMO);
        }
        return closeQualifiedCall(call);
    }

    private EngineResult closeQualifiedCall(CallRecord call) {
        if (Boolean.TRUE.equals(call.getWantsDemo())) {
            return complete(call, "Wonderful. I have noted your demo request and a Flowzo specialist will "
                            + "contact you shortly to schedule it. Thank you!",
                    CallOutcome.DEMO_REQUESTED, LeadStatus.QUALIFIED);
        }
        if (Boolean.FALSE.equals(call.getWantsDemo())) {
            return complete(call, "No problem. Since you are interested, our team can still share resources "
                            + "by email. Thank you for your time!",
                    CallOutcome.INTERESTED, LeadStatus.QUALIFIED);
        }
        return complete(call, "Thank you for your time. I have noted your interest and a Flowzo specialist "
                        + "will follow up with more details. Goodbye!",
                CallOutcome.INTERESTED, LeadStatus.QUALIFIED);
    }

    private EngineResult question(CallRecord call, String message, ConversationStep step) {
        return question(call, message, step, CallOutcome.INTERESTED);
    }

    /** Keeps the call in step with the question being asked so callers cannot drift out of sync. */
    private EngineResult question(CallRecord call, String message, ConversationStep step, CallOutcome outcome) {
        call.setCurrentStep(step);
        return new EngineResult(message, step, outcome, false);
    }

    private boolean needs(CallRecord call, ConversationStep step, Object answer) {
        return answer == null && !isSkipped(call, step);
    }

    private String reprompt(ConversationStep step) {
        return switch (step) {
            case AWAIT_TEAM_SIZE -> "Could you share an approximate number of salespeople on your team?";
            case AWAIT_LEAD_MANAGEMENT -> "Could you briefly describe how you currently manage leads?";
            case AWAIT_DEMO -> "Just to confirm, would you like us to arrange a product demonstration?";
            default -> "Could you please repeat that?";
        };
    }

    private boolean isSkipped(CallRecord call, ConversationStep step) {
        String skipped = call.getSkippedSteps();
        return skipped != null && List.of(skipped.split(",")).contains(step.name());
    }

    private void markSkipped(CallRecord call, ConversationStep step) {
        if (isSkipped(call, step)) {
            return;
        }
        String skipped = call.getSkippedSteps();
        call.setSkippedSteps(skipped == null || skipped.isBlank() ? step.name() : skipped + "," + step.name());
    }

    private EngineResult complete(CallRecord call, String message, CallOutcome outcome, LeadStatus leadStatus) {
        call.setOutcome(outcome);
        call.setStatus(CallStatus.COMPLETED);
        call.setCurrentStep(ConversationStep.COMPLETED);
        call.getLead().setStatus(leadStatus);
        return new EngineResult(message, ConversationStep.COMPLETED, outcome, true);
    }

    public String buildSummary(CallRecord call) {
        Lead lead = call.getLead();
        StringBuilder sb = new StringBuilder();
        sb.append("Call with ").append(lead.getName());
        if (lead.getCompanyName() != null && !lead.getCompanyName().isBlank()) {
            sb.append(" from ").append(lead.getCompanyName());
        }
        sb.append(". ");

        if (Boolean.FALSE.equals(call.getInterestedInCrm()) || call.getOutcome() == CallOutcome.NOT_INTERESTED) {
            sb.append("The customer is not interested in a CRM at this time.");
        } else if (call.getOutcome() == CallOutcome.CALLBACK_REQUESTED) {
            sb.append("The customer requested a callback.");
        } else {
            if (Boolean.TRUE.equals(call.getInterestedInCrm())) {
                sb.append("The customer is interested in CRM software. ");
            }
            if (call.getSalesTeamSize() != null) {
                sb.append("Sales team size is approximately ")
                        .append(call.getSalesTeamSize())
                        .append(". ");
            } else {
                sb.append("Sales team size was not shared. ");
            }
            if (call.getLeadManagementMethod() != null) {
                sb.append("Lead handling today: \"")
                        .append(call.getLeadManagementMethod().trim())
                        .append("\". ");
            }
            if (Boolean.TRUE.equals(call.getWantsDemo())) {
                sb.append("Requested a product demonstration.");
            } else if (Boolean.FALSE.equals(call.getWantsDemo())) {
                sb.append("Did not request a product demonstration.");
            }
        }
        sb.append(" Final outcome: ").append(call.getOutcome()).append(".");
        return sb.toString().trim();
    }

    private boolean isPositive(String text) {
        String t = normalize(text);
        if (t.contains("not interested")) {
            return false;
        }
        // Phrases that agree without saying yes. Without them the agent asks the caller to confirm
        // an interest they have already shown, which reads as though it was not listening.
        if (t.contains("tell me more") || t.contains("go ahead") || t.contains("sounds good")
                || t.contains("sounds interesting") || t.contains("of course") || t.contains("why not")
                || containsWord(t, "definitely", "certainly")) {
            return true;
        }
        return containsWord(t, "yes", "yeah", "yep", "sure", "interested", "absolutely", "please")
                || t.equals("ok")
                || t.equals("okay")
                || t.startsWith("ok ")
                || t.startsWith("okay ");
    }

    private boolean isNegative(String text) {
        String t = normalize(text);
        return t.contains("not interested")
                || containsWord(t, "no", "nope", "never")
                || t.contains("don't")
                || t.contains("do not")
                || t.contains("stop calling");
    }

    private boolean isCallback(String text) {
        String t = normalize(text);
        return t.contains("callback")
                || t.contains("call back")
                || t.contains("not now")
                || t.contains("another time")
                || containsWord(t, "later", "busy");
    }

    private boolean looksUncertain(String text) {
        String t = normalize(text);
        return t.contains("not sure") || containsWord(t, "maybe", "perhaps", "might");
    }

    /** A bare acknowledgement carries no information about how leads are managed today. */
    private boolean isFiller(String text) {
        String t = normalize(text);
        return t.isEmpty()
                || containsWord(t, "yes", "yeah", "yep", "no", "nope", "ok", "okay", "sure", "hmm")
                && t.split(" ").length <= 2
                || t.contains("no idea")
                || t.contains("not sure")
                || t.contains("don't know")
                || t.contains("dont know");
    }

    private boolean mentionsTeam(String text) {
        String t = normalize(text);
        return containsWord(t, "team", "people", "person", "reps", "rep", "salespeople", "staff",
                "employees", "members", "headcount", "agents");
    }

    private boolean mentionsScheduling(String text) {
        String t = normalize(text);
        return t.contains("demo")
                || containsWord(t, "schedule", "monday", "tuesday", "wednesday", "thursday", "friday",
                "saturday", "sunday", "am", "pm", "tomorrow", "clock");
    }

    /**
     * Only concrete tool names count here. Generic words such as "CRM" or "email" appear in replies
     * like "yes, I am interested in a CRM", which say nothing about how leads are handled today.
     */
    private boolean mentionsLeadTooling(String text) {
        String t = normalize(text);
        return containsWord(t, "excel", "spreadsheet", "spreadsheets", "sheet", "sheets", "whatsapp",
                "hubspot", "salesforce", "zoho", "notebook", "diary", "manually", "notion", "airtable")
                || t.contains("google sheet")
                || t.contains("on paper")
                || t.contains("by hand");
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s']", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean containsWord(String text, String... words) {
        for (String word : words) {
            if ((" " + text + " ").contains(" " + word + " ")) {
                return true;
            }
        }
        return false;
    }

    private OptionalInt extractNumber(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("ten")) {
            return OptionalInt.of(10);
        }
        if (lower.contains("five")) {
            return OptionalInt.of(5);
        }
        if (lower.contains("twenty")) {
            return OptionalInt.of(20);
        }
        return OptionalInt.empty();
    }

    public record EngineResult(
            String aiMessage,
            ConversationStep nextStep,
            CallOutcome outcome,
            boolean completed
    ) {
    }
}

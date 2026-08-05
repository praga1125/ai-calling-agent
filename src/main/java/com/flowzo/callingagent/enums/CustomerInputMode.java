package com.flowzo.callingagent.enums;

/** How the customer's turn reached the app. */
public enum CustomerInputMode {

    /** A recording that OpenAI speech-to-text transcribed. */
    VOICE,

    /** Text typed straight into the API, used when a microphone or quota is unavailable. */
    TEXT
}

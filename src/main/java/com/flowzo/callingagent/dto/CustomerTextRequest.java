package com.flowzo.callingagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A customer turn typed instead of spoken. */
public class CustomerTextRequest {

    @NotBlank
    @Size(max = 2000)
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}

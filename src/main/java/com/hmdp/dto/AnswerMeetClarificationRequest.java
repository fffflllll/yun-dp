package com.hmdp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerMeetClarificationRequest {

    @NotBlank(message = "请选择澄清答案")
    @Size(max = 64, message = "澄清答案过长")
    private String answer;
}

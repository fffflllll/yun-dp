package com.hmdp;

import com.hmdp.dto.MeetPreferenceData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetPreferenceDataValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsAllExecutableHardConstraintKeysAndBoundaryValues() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setBudgetMax(10_000);
        data.setMaxDistanceMeters(50_000);
        data.setPreferredTime("时".repeat(100));
        data.setPreferredCuisines(List.of("菜".repeat(64)));
        data.setAvoidFoods(List.of("忌".repeat(64)));
        data.setAllergens(List.of("敏".repeat(64)));
        data.setNotes(List.of("注".repeat(200)));
        data.setHardConstraintKeys(List.of(
                "BUDGET_MAX",
                "ALLERGENS",
                "ACCEPTS_SPICY",
                "MAX_DISTANCE"));

        assertTrue(validator.validate(data).isEmpty());
    }

    @Test
    void rejectsUnsupportedHardConstraintKey() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setHardConstraintKeys(List.of("MODEL_INVENTED_KEY"));

        assertViolation(data, "hardConstraintKeys[0].<list element>",
                "存在不支持的硬约束");
    }

    @Test
    void rejectsTooManyHardConstraintKeys() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setHardConstraintKeys(List.of(
                "BUDGET_MAX",
                "ALLERGENS",
                "ACCEPTS_SPICY",
                "MAX_DISTANCE",
                "BUDGET_MAX",
                "BUDGET_MAX"));

        assertViolation(data, "hardConstraintKeys", "硬约束不能超过5项");
    }

    @Test
    void rejectsNullCollections() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setPreferredCuisines(null);
        data.setAvoidFoods(null);
        data.setAllergens(null);
        data.setHardConstraintKeys(null);
        data.setNotes(null);

        Set<String> paths = validator.validate(data).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "preferredCuisines",
                "avoidFoods",
                "allergens",
                "hardConstraintKeys",
                "notes"), paths);
    }

    @Test
    void rejectsOversizedCollections() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setPreferredCuisines(repeated("菜", 11));
        data.setAvoidFoods(repeated("忌", 11));
        data.setAllergens(repeated("敏", 11));
        data.setNotes(repeated("注", 6));

        Set<String> messages = messages(data);
        assertTrue(messages.contains("偏好菜系不能超过10项"));
        assertTrue(messages.contains("忌口不能超过10项"));
        assertTrue(messages.contains("过敏原不能超过10项"));
        assertTrue(messages.contains("补充说明不能超过5项"));
    }

    @Test
    void rejectsOversizedCollectionElements() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setPreferredCuisines(List.of("菜".repeat(65)));
        data.setAvoidFoods(List.of("忌".repeat(65)));
        data.setAllergens(List.of("敏".repeat(65)));
        data.setNotes(List.of("注".repeat(201)));

        Set<String> messages = messages(data);
        assertTrue(messages.contains("菜系名称不能超过64个字符"));
        assertTrue(messages.contains("忌口内容不能超过64个字符"));
        assertTrue(messages.contains("过敏原不能超过64个字符"));
        assertTrue(messages.contains("单条补充说明不能超过200个字符"));
    }

    @Test
    void rejectsOutOfRangeNumbersAndOversizedPreferredTime() {
        MeetPreferenceData data = new MeetPreferenceData();
        data.setBudgetMax(0);
        data.setMaxDistanceMeters(50_001);
        data.setPreferredTime("时".repeat(101));

        Set<String> messages = messages(data);
        assertTrue(messages.contains("预算必须大于0"));
        assertTrue(messages.contains("距离不能超过50000米"));
        assertTrue(messages.contains("偏好时间不能超过100个字符"));
    }

    private List<String> repeated(String value, int count) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(value);
        }
        return result;
    }

    private Set<String> messages(MeetPreferenceData data) {
        return validator.validate(data).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void assertViolation(
            MeetPreferenceData data, String path, String message) {
        assertTrue(validator.validate(data).stream().anyMatch(violation ->
                path.equals(violation.getPropertyPath().toString())
                        && message.equals(violation.getMessage())));
    }
}

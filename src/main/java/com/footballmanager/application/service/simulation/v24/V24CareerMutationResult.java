package com.footballmanager.application.service.simulation.v24;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Immutable result of a V24 career mutation pass.
 *
 * <p>Records how many mutations of each type were applied and any failures encountered.
 * Failure means a fatal/unexpected error occurred; individual player/effect failures
 * are skipped and recorded in the failures list.
 */
public final class V24CareerMutationResult {

    private final int injuriesApplied;
    private final int fatigueApplied;
    private final int disciplineApplied;
    private final int formApplied;
    private final List<String> failures;
    private final boolean partialFailure;

    private V24CareerMutationResult(
            int injuriesApplied,
            int fatigueApplied,
            int disciplineApplied,
            int formApplied,
            List<String> failures,
            boolean partialFailure) {
        this.injuriesApplied = injuriesApplied;
        this.fatigueApplied = fatigueApplied;
        this.disciplineApplied = disciplineApplied;
        this.formApplied = formApplied;
        this.failures = failures;
        this.partialFailure = partialFailure;
    }

    public static V24CareerMutationResult empty() {
        return new V24CareerMutationResult(0, 0, 0, 0,
                Collections.emptyList(), false);
    }

    public static V24CareerMutationResult success(
            int injuries, int fatigue, int discipline, int form) {
        return new V24CareerMutationResult(
                injuries, fatigue, discipline, form,
                Collections.emptyList(), false);
    }

    public static V24CareerMutationResult failure(String message) {
        return failure(message, false);
    }

    public static V24CareerMutationResult failure(String message, boolean partial) {
        ArrayList<String> list = new ArrayList<>();
        list.add(message);
        return new V24CareerMutationResult(0, 0, 0, 0,
                Collections.unmodifiableList(list), partial);
    }

    public static V24CareerMutationResult failure(int injuries, int fatigue, String message) {
        ArrayList<String> list = new ArrayList<>();
        list.add(message);
        boolean partial = injuries > 0 || fatigue > 0;
        return new V24CareerMutationResult(injuries, fatigue, 0, 0,
                Collections.unmodifiableList(list), partial);
    }

    public static V24CareerMutationResult failure(List<String> messages) {
        List<String> defensive = messages != null
                ? Collections.unmodifiableList(new ArrayList<>(messages))
                : Collections.emptyList();
        return new V24CareerMutationResult(0, 0, 0, 0, defensive, false);
    }

    public static V24CareerMutationResult failure(List<String> messages, boolean partial) {
        List<String> defensive = messages != null
                ? Collections.unmodifiableList(new ArrayList<>(messages))
                : Collections.emptyList();
        return new V24CareerMutationResult(0, 0, 0, 0, defensive, partial);
    }

    public static V24CareerMutationResult failure(int injuries, int fatigue, List<String> messages, boolean partial) {
        List<String> defensive = messages != null
                ? Collections.unmodifiableList(new ArrayList<>(messages))
                : Collections.emptyList();
        return new V24CareerMutationResult(injuries, fatigue, 0, 0, defensive, partial);
    }

    public static V24CareerMutationResult failure(int injuries, int fatigue, int discipline, List<String> messages, boolean partial) {
        List<String> defensive = messages != null
                ? Collections.unmodifiableList(new ArrayList<>(messages))
                : Collections.emptyList();
        return new V24CareerMutationResult(injuries, fatigue, discipline, 0, defensive, partial);
    }

    public static V24CareerMutationResult partial(
            int injuries, int fatigue, int discipline, int form,
            List<String> failures) {
        List<String> defensive = failures != null
                ? Collections.unmodifiableList(new ArrayList<>(failures))
                : Collections.emptyList();
        boolean partial = !defensive.isEmpty()
                && (injuries > 0 || fatigue > 0 || discipline > 0 || form > 0);
        return new V24CareerMutationResult(
                injuries, fatigue, discipline, form, defensive, partial);
    }

    public int injuriesApplied() { return injuriesApplied; }
    public int fatigueApplied() { return fatigueApplied; }
    public int disciplineApplied() { return disciplineApplied; }
    public int formApplied() { return formApplied; }
    public List<String> failures() { return failures; }
    public boolean partialFailure() { return partialFailure; }

    @Override
    public String toString() {
        return "V24CareerMutationResult{injuries=%d, fatigue=%d, discipline=%d, form=%d, failures=%s, partial=%s}"
                .formatted(injuriesApplied, fatigueApplied, disciplineApplied,
                        formApplied, failures, partialFailure);
    }
}
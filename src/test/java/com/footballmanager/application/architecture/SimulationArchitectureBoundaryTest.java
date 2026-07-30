package com.footballmanager.application.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.footballmanager", importOptions = ImportOption.DoNotIncludeTests.class)
class SimulationArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule simulation_core_uses_domain_and_application_collaborators_only = noClasses()
            .that().resideInAPackage("..application.service.simulation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..",
                    "..infrastructure..",
                    "org.springframework.data.redis..",
                    "org.springframework.jdbc..",
                    "org.springframework.r2dbc..",
                    "io.r2dbc..",
                    "java.sql..");

    @ArchTest
    static final ArchRule detailed_match_flow_components_do_not_depend_on_web_or_persistence = noClasses()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters.in.web..",
                    "..adapters.out..",
                    "..infrastructure..",
                    "org.springframework.data..",
                    "org.springframework.web..",
                    "org.springframework.security..",
                    "java.sql..");

    @ArchTest
    static final ArchRule simulation_orchestrators_are_application_layer_components = classes()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Orchestrator")
            .should().resideInAPackage("..application.service.simulation..");

    @ArchTest
    static final ArchRule simulation_orchestrators_do_not_depend_on_adapter_or_infrastructure_details = noClasses()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Orchestrator")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..",
                    "..infrastructure..");

    @ArchTest
    static final ArchRule detailed_match_engine_facade_stays_small_and_stateless = classes()
            .that().haveFullyQualifiedName(
                    "com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine")
            .should(haveAtMostTwoInstanceFields());

    @ArchTest
    static final ArchRule simulation_package_does_not_reintroduce_large_stateful_flow_objects = classes()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Flow")
            .should(haveAtMostFiveInstanceFields());

    @ArchTest
    static final ArchRule detailed_minute_pipeline_has_no_generic_support_helper_or_utils = classes()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .should(notHaveGenericUtilityName());

    @ArchTest
    static final ArchRule detailed_minute_phases_do_not_depend_on_web_persistence_or_infrastructure = noClasses()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .and().haveSimpleNameEndingWith("Phase")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..",
                    "..infrastructure..",
                    "org.springframework..",
                    "java.sql..");

    @ArchTest
    static final ArchRule minute_context_state_input_and_result_do_not_hold_services_or_adapters = noClasses()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .and(contextStateInputOrResult())
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..",
                    "..infrastructure..",
                    "org.springframework..");

    @ArchTest
    static final ArchRule minute_state_input_and_result_do_not_depend_on_minute_composition_or_engines = noClasses()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .and(contextStateInputOrResult())
            .should().dependOnClassesThat(minuteCompositionPolicyEngineOrPhase());

    @ArchTest
    static final ArchRule minute_phases_are_concrete_cohesive_steps_not_aggregate_physical_state = noClasses()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .should().haveSimpleName("MinutePhysicalStatePhase");

    @ArchTest
    static final ArchRule simulation_flows_do_not_have_mutable_static_fields = classes()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Flow")
            .should(haveNoMutableStaticFields());

    @Test
    void detailedMinutePipelineMethodsRemainSmallEnoughToAudit() throws IOException {
        Path detailedPackage = Path.of(
                "src/main/java/com/footballmanager/application/service/simulation/detailed");
        List<String> centralFiles = Files.list(detailedPackage)
                .filter(path -> path.getFileName().toString().matches("DetailedMatchMinute.*\\.java|Minute.*Phase\\.java"))
                .map(Path::toString)
                .sorted()
                .toList();

        for (String file : centralFiles) {
            assertThat(longestMethodBodyIn(Path.of(file)))
                    .as(file)
                    .isLessThanOrEqualTo(80);
        }
    }

    @Test
    void detailedMinutePipelineUsesExplicitInjuryAndRestartPhases() throws IOException {
        String pipeline = Files.readString(Path.of(
                "src/main/java/com/footballmanager/application/service/simulation/detailed/DetailedMatchMinutePipeline.java"));

        assertThat(pipeline).contains("MinuteInjuryPhase");
        assertThat(pipeline).contains("MinuteRestartEventPhase");
        assertThat(pipeline).doesNotContain("MinutePhysicalStatePhase");
    }

    private static ArchCondition<JavaClass> haveAtMostTwoInstanceFields() {
        return haveAtMostInstanceFields(2);
    }

    private static ArchCondition<JavaClass> haveAtMostFiveInstanceFields() {
        return haveAtMostInstanceFields(5);
    }

    private static ArchCondition<JavaClass> haveAtMostInstanceFields(int maxFields) {
        return new ArchCondition<>("have at most " + maxFields + " instance fields") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                long fieldCount = item.getFields().stream()
                        .filter(field -> !Modifier.isStatic(field.reflect().getModifiers()))
                        .count();
                if (fieldCount > maxFields) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " has " + fieldCount + " instance fields"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveNoMutableStaticFields() {
        return new ArchCondition<>("have no mutable static fields") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaField field : item.getFields()) {
                    int modifiers = field.reflect().getModifiers();
                    if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                item.getName() + " has mutable static field " + field.getName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notHaveGenericUtilityName() {
        return new ArchCondition<>("not have generic Support, Helper or Utils names") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.getSimpleName().matches(".*(Support|Helper|Utils)")) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " uses a generic utility-style name"));
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> contextStateInputOrResult() {
        return new DescribedPredicate<>("context/state/input/result classes") {
            @Override
            public boolean test(JavaClass input) {
                return input.getSimpleName().matches(".*(Context|Config|State|Input|Result)");
            }
        };
    }

    private static DescribedPredicate<JavaClass> minuteCompositionPolicyEngineOrPhase() {
        return new DescribedPredicate<>("minute composition, policies, engines or phases") {
            @Override
            public boolean test(JavaClass input) {
                return input.getSimpleName().matches("DetailedMatchMinuteComposition|.*Engine|.*Phase|.*Policies");
            }
        };
    }

    private static int longestMethodBodyIn(Path sourceFile) throws IOException {
        List<String> lines = Files.readAllLines(sourceFile);
        int longest = 0;
        int depth = 0;
        int methodStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean methodCandidate = methodStart < 0
                    && line.contains("(")
                    && line.contains(")")
                    && line.contains("{")
                    && !line.trim().startsWith("if ")
                    && !line.trim().startsWith("for ")
                    && !line.trim().startsWith("while ")
                    && !line.trim().startsWith("switch ");
            if (methodCandidate) {
                methodStart = i;
            }
            depth += count(line, '{');
            depth -= count(line, '}');
            if (methodStart >= 0 && depth == 1 && line.trim().equals("}")) {
                longest = Math.max(longest, i - methodStart + 1);
                methodStart = -1;
            }
        }
        return longest;
    }

    private static int count(String value, char token) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == token) {
                count++;
            }
        }
        return count;
    }
}

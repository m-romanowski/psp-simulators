package dev.marcinromanowski.pspsim.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@AnalyzeClasses(
    packages = "dev.marcinromanowski.pspsim",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  private static final String ROOT = "dev.marcinromanowski.pspsim";
  private static final String APPLICATION = "dev.marcinromanowski.pspsim..";
  private static final String KERNEL = "dev.marcinromanowski.pspsim.kernel..";
  private static final String KERNEL_SPI = "dev.marcinromanowski.pspsim.kernel.spi..";
  private static final String KERNEL_CLOCK = "dev.marcinromanowski.pspsim.kernel.clock..";
  private static final String KERNEL_IDS = "dev.marcinromanowski.pspsim.kernel.ids..";
  private static final String KERNEL_INTERNALS = "dev.marcinromanowski.pspsim.kernel.*.internal..";
  private static final String KERNEL_TEST_RUN = "dev.marcinromanowski.pspsim.kernel.testrun";
  private static final String KERNEL_CREDENTIALS =
      "dev.marcinromanowski.pspsim.kernel.credentials..";
  private static final String PROVIDER = "dev.marcinromanowski.pspsim.provider..";
  private static final String CONTROL = "dev.marcinromanowski.pspsim.control..";
  private static final String UI = "dev.marcinromanowski.pspsim.ui..";
  private static final String PLATFORM = "dev.marcinromanowski.pspsim.platform..";
  private static final String UNIT_OF_WORK_INTERNALS =
      "dev.marcinromanowski.pspsim.kernel.transaction.internal";
  private static final String DATABASE_DRIVER = "org.postgresql..";

  private static final String CONTROL_ROUTE_PREFIX = "/__simulator";
  private static final String UI_ROUTE_PREFIX = "/ui";

  @ArchTest
  static final ArchRule every_class_lives_in_a_declared_top_level_package =
      classes()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .resideInAnyPackage(ROOT, KERNEL, PROVIDER, CONTROL, UI, PLATFORM);

  @ArchTest
  static final ArchRule kernel_depends_on_nothing_else_in_the_application =
      noClasses()
          .that()
          .resideInAPackage(KERNEL)
          .should()
          .dependOnClassesThat(resideInAPackage(APPLICATION).and(resideOutsideOfPackage(KERNEL)));

  @ArchTest
  static final ArchRule only_the_unit_of_work_reads_the_database_driver =
      noClasses()
          .that()
          .resideOutsideOfPackage(UNIT_OF_WORK_INTERNALS)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(DATABASE_DRIVER);

  @ArchTest
  static final ArchRule kernel_internals_are_private_to_their_module =
      classes()
          .that()
          .resideInAPackage(KERNEL_INTERNALS)
          .should(beAccessedOnlyFromTheirOwnKernelModule())
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule kernel_spi_does_not_reach_into_any_internal_package =
      noClasses()
          .that()
          .resideInAPackage(KERNEL_SPI)
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..internal..");

  @ArchTest
  static final ArchRule kernel_modules_are_free_of_cycles =
      SlicesRuleDefinition.slices()
          .matching("dev.marcinromanowski.pspsim.kernel.(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule provider_modules_do_not_depend_on_each_other =
      SlicesRuleDefinition.slices()
          .matching("dev.marcinromanowski.pspsim.provider.(*)..")
          .should()
          .notDependOnEachOther()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule control_and_ui_do_not_depend_on_provider_packages =
      noClasses()
          .that()
          .resideInAnyPackage(CONTROL, UI)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(PROVIDER)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule provider_modules_do_not_depend_on_the_control_api =
      noClasses()
          .that()
          .resideInAPackage(PROVIDER)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(CONTROL)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule the_reserved_test_run_id_is_produced_only_by_its_own_module =
      noClasses()
          .that()
          .resideOutsideOfPackage(KERNEL_TEST_RUN)
          .should()
          .callConstructor(TestRunId.class, String.class);

  @ArchTest
  static final ArchRule a_transaction_is_opened_only_with_the_default_propagation =
      classes().that().resideInAPackage(APPLICATION).should(notChooseAPropagationOrIsolation());

  @ArchTest
  static final ArchRule the_application_declares_no_data_source_of_its_own =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("javax.sql.DataSource");

  @ArchTest
  static final ArchRule provider_configuration_is_written_on_the_thread_that_opened_it =
      classes()
          .that()
          .resideInAnyPackage(KERNEL_CREDENTIALS, KERNEL_SPI, PROVIDER)
          .should(notHandWorkToAnotherThread());

  @ArchTest
  static final ArchRule nothing_declares_a_controller_advice =
      classes().that().resideInAPackage(APPLICATION).should(notBeAControllerAdvice());

  @ArchTest
  static final ArchRule exception_handlers_live_only_in_the_control_api =
      methods()
          .that()
          .areAnnotatedWith(ExceptionHandler.class)
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage(CONTROL);

  @ArchTest
  static final ArchRule an_exception_handler_answers_only_a_fault_raised_before_a_route_decided =
      methods()
          .that()
          .areAnnotatedWith(ExceptionHandler.class)
          .should(answerNoFaultThisBuildRaisesItself())
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_fault_outside_the_screens_carries_a_status_of_its_own =
      noClasses()
          .that()
          .areAssignableTo(Throwable.class)
          .and()
          .resideOutsideOfPackage(UI)
          .should()
          .beAnnotatedWith(ResponseStatus.class)
          .because(
              "a status on an exception class answers a client error from wherever the exception"
                  + " was raised, with spring's own envelope and nothing on the trail, which is"
                  + " the decision the handler rule above refuses in its own mechanism; the"
                  + " screens are the one surface that answers a fault of this build with a page"
                  + " of their own, and UnknownContributedScreenException is where they say so");

  @ArchTest
  static final ArchRule no_provider_module_chooses_the_order_one_answer_is_in =
      classes()
          .that()
          .areAssignableTo(SimulatorProvider.class)
          .and()
          .areNotInterfaces()
          .should(notDeclareTheBatchedRead());

  @ArchTest
  static final ArchRule only_the_batched_read_asks_a_module_what_it_holds =
      classes()
          .that()
          .resideInAPackage(APPLICATION)
          .should(notAskAModuleWhatItHoldsWithoutOrderingTheAnswer());

  @ArchTest
  static final ArchRule wall_clock_time_is_read_only_by_the_simulator_clock =
      classes().that().resideOutsideOfPackage(KERNEL_CLOCK).should(notReadWallClockTime());

  @ArchTest
  static final ArchRule randomness_is_produced_only_by_the_id_generator =
      classes().that().resideOutsideOfPackage(KERNEL_IDS).should(notProduceRandomness());

  @ArchTest
  static final ArchRule control_maps_only_simulator_namespace_routes =
      methods()
          .that(areRequestMappings())
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage(CONTROL)
          .should(mapOnlyRoutesStartingWith(CONTROL_ROUTE_PREFIX))
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule ui_maps_only_ui_routes =
      methods()
          .that(areRequestMappings())
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage(UI)
          .should(mapOnlyRoutesStartingWith(UI_ROUTE_PREFIX))
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule provider_routes_stay_out_of_the_simulator_namespaces =
      methods()
          .that(areRequestMappings())
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage(PROVIDER)
          .should(mapNoRouteStartingWithAnyOf(CONTROL_ROUTE_PREFIX, UI_ROUTE_PREFIX))
          .allowEmptyShould(true);

  private static final Set<String> CHOOSE_A_PROPAGATION_OR_ISOLATION =
      Set.of(
          "setPropagationBehavior",
          "setPropagationBehaviorName",
          "setIsolationLevel",
          "setIsolationLevelName");

  private static final Set<String> DECLARE_A_TRANSACTION_OF_THEIR_OWN =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "org.springframework.transaction.annotation.Propagation",
          "org.springframework.transaction.annotation.Isolation",
          "org.springframework.transaction.TransactionDefinition",
          "org.springframework.transaction.support.DefaultTransactionDefinition");

  private static final Set<String> HAND_WORK_TO_ANOTHER_THREAD =
      Set.of(
          "java.lang.Thread",
          "java.util.concurrent.Executor",
          "java.util.concurrent.ExecutorService",
          "java.util.concurrent.ScheduledExecutorService",
          "java.util.concurrent.ForkJoinPool",
          "java.util.concurrent.Future",
          "java.util.concurrent.CompletableFuture",
          "java.util.concurrent.CompletionStage",
          "org.springframework.core.task.TaskExecutor",
          "org.springframework.scheduling.TaskScheduler",
          "org.springframework.scheduling.annotation.Async",
          "org.springframework.scheduling.annotation.Scheduled");

  private static ArchCondition<JavaClass> notChooseAPropagationOrIsolation() {
    return new ArchCondition<>(
        "leave a transaction on the default propagation, so a provider store called inside the"
            + " core's transaction joins it rather than opening or suspending one") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
          if (DECLARE_A_TRANSACTION_OF_THEIR_OWN.contains(
              dependency.getTargetClass().getFullName())) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
        for (JavaCodeUnitAccess<?> call : javaClass.getCodeUnitAccessesFromSelf()) {
          if (CHOOSE_A_PROPAGATION_OR_ISOLATION.contains(call.getTarget().getName())) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notHandWorkToAnotherThread() {
    return new ArchCondition<>(
        "write provider configuration on the thread that opened the transaction, because a"
            + " transaction is bound to its thread and a hand-off leaves it") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
          if (HAND_WORK_TO_ANOTHER_THREAD.contains(dependency.getTargetClass().getFullName())) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notBeAControllerAdvice() {
    return new ArchCondition<>("not be annotated with @ControllerAdvice or @RestControllerAdvice") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean advice =
            javaClass.isAnnotatedWith(ControllerAdvice.class)
                || javaClass.isMetaAnnotatedWith(ControllerAdvice.class)
                || javaClass.isAnnotatedWith(RestControllerAdvice.class);
        if (advice) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, javaClass.getFullName() + " is a controller advice"));
        }
      }
    };
  }

  private static final Set<String> SYSTEM_CLOCK_FACTORIES =
      Set.of(
          "system", "systemUTC", "systemDefaultZone", "tickMillis", "tickSeconds", "tickMinutes");

  private static final String THE_BATCHED_READ = "states";

  private static final String THE_UNORDERED_READ_BEHIND_IT = "theObjectsItHoldsUnder";

  private static ArchCondition<JavaClass> notDeclareTheBatchedRead() {
    return new ArchCondition<>(
        "leave the order of one answer for many identifiers to "
            + SimulatorProvider.class.getSimpleName()
            + ", which puts it in the order asked") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod declared : javaClass.getMethods()) {
          if (THE_BATCHED_READ.equals(declared.getName())) {
            events.add(
                SimpleConditionEvent.violated(
                    declared,
                    declared.getFullName()
                        + " declares "
                        + THE_BATCHED_READ
                        + " itself, so this module and not the core decides the order its answer"
                        + " is in. Override "
                        + THE_UNORDERED_READ_BEHIND_IT
                        + " and let the core order what it reads: "
                        + TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notAskAModuleWhatItHoldsWithoutOrderingTheAnswer() {
    return new ArchCondition<>(
        "read many identifiers through "
            + THE_BATCHED_READ
            + " rather than through the unordered read behind it") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.isEquivalentTo(SimulatorProvider.class)) {
          return;
        }
        for (JavaCodeUnitAccess<?> call : javaClass.getCodeUnitAccessesFromSelf()) {
          if (THE_UNORDERED_READ_BEHIND_IT.equals(call.getTarget().getName())) {
            events.add(
                SimpleConditionEvent.violated(
                    call,
                    call.getDescription()
                        + ", which answers what its stores return rather than what was asked: "
                        + TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notReadWallClockTime() {
    return new ArchCondition<>("not read wall-clock time outside " + KERNEL_CLOCK) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaCodeUnitAccess<?> call : javaClass.getCodeUnitAccessesFromSelf()) {
          if (readsWallClock(call)) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }

  private static boolean readsWallClock(JavaCodeUnitAccess<?> call) {
    JavaClass owner = call.getTargetOwner();
    String ownerName = owner.getFullName();
    String name = call.getTarget().getName();
    boolean noArguments = call.getTarget().getRawParameterTypes().isEmpty();

    if (owner.getPackageName().startsWith("java.time") && "now".equals(name)) {
      return true;
    }
    if ("java.time.Clock".equals(ownerName) && SYSTEM_CLOCK_FACTORIES.contains(name)) {
      return true;
    }
    if ("java.lang.System".equals(ownerName)) {
      return "currentTimeMillis".equals(name) || "nanoTime".equals(name);
    }
    if ("java.util.Calendar".equals(ownerName) && "getInstance".equals(name)) {
      return true;
    }
    boolean legacyDate =
        "java.util.Date".equals(ownerName) || "java.util.GregorianCalendar".equals(ownerName);
    return legacyDate && JavaConstructor.CONSTRUCTOR_NAME.equals(name) && noArguments;
  }

  private static ArchCondition<JavaClass> notProduceRandomness() {
    return new ArchCondition<>("not produce randomness outside " + KERNEL_IDS) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
          if (isRandomSource(dependency.getTargetClass())) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
        for (JavaCodeUnitAccess<?> call : javaClass.getCodeUnitAccessesFromSelf()) {
          if (producesRandomness(call)) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }

  private static boolean isRandomSource(JavaClass type) {
    return type.isAssignableTo(Random.class)
        || type.getPackageName().startsWith("java.util.random");
  }

  private static boolean producesRandomness(JavaCodeUnitAccess<?> call) {
    String ownerName = call.getTargetOwner().getFullName();
    String name = call.getTarget().getName();
    return ("java.util.UUID".equals(ownerName) && "randomUUID".equals(name))
        || ("java.lang.Math".equals(ownerName) && "random".equals(name));
  }

  private static ArchCondition<JavaClass> beAccessedOnlyFromTheirOwnKernelModule() {
    return new ArchCondition<>("be accessed only from within their own kernel module") {
      @Override
      public void check(JavaClass internalClass, ConditionEvents events) {
        String owningModule = owningKernelModuleOf(internalClass.getPackageName());
        for (Dependency dependency : internalClass.getDirectDependenciesToSelf()) {
          String origin = dependency.getOriginClass().getPackageName();
          boolean inside = origin.equals(owningModule) || origin.startsWith(owningModule + ".");
          if (!inside) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  private static String owningKernelModuleOf(String internalPackageName) {
    int marker = internalPackageName.indexOf(".internal");
    if (marker < 0) {
      throw new IllegalStateException("not an internal package: " + internalPackageName);
    }
    return internalPackageName.substring(0, marker);
  }

  private static ArchCondition<JavaMethod> answerNoFaultThisBuildRaisesItself() {
    return new ArchCondition<>(
        "answer only a fault raised before a route of this build decided anything, in either"
            + " spelling of the annotation: the exceptions a handler answers are the ones its"
            + " annotation names, or where it names none the ones its own parameters do, which is"
            + " how spring reads it. A refusal this simulator decided is answered and recorded by"
            + " the route that decided it, where the run it belongs to, the subject it names and"
            + " the words it was answered in are all still in hand, and a handler that answers one"
            + " of them with a client error publishes a decision the trail never carries") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (JavaClass raised : theExceptionsAnsweredBy(method)) {
          events.add(
              new SimpleConditionEvent(
                  method,
                  !raised.getPackageName().startsWith(ROOT),
                  method.getFullName() + " answers " + raised.getFullName()));
        }
      }
    };
  }

  private static List<JavaClass> theExceptionsAnsweredBy(JavaMethod method) {
    List<JavaClass> answered = new ArrayList<>();
    for (JavaAnnotation<?> annotation : method.getAnnotations()) {
      if (!annotation.getRawType().isEquivalentTo(ExceptionHandler.class)) {
        continue;
      }
      answered.addAll(theExceptionsTheAnnotationNames(annotation));
      if (answered.isEmpty()) {
        answered.addAll(theExceptionsTheParametersName(method));
      }
    }
    return answered;
  }

  private static List<JavaClass> theExceptionsTheAnnotationNames(JavaAnnotation<?> annotation) {
    List<JavaClass> named = new ArrayList<>();
    annotation
        .get("value")
        .ifPresent(
            value -> {
              for (Object raised : (Object[]) value) {
                named.add((JavaClass) raised);
              }
            });
    return named;
  }

  private static List<JavaClass> theExceptionsTheParametersName(JavaMethod method) {
    List<JavaClass> named = new ArrayList<>();
    for (JavaClass taken : method.getRawParameterTypes()) {
      if (taken.isAssignableTo(Throwable.class)) {
        named.add(taken);
      }
    }
    return named;
  }

  private static DescribedPredicate<JavaMethod> areRequestMappings() {
    return new DescribedPredicate<>("are Spring request mappings") {
      @Override
      public boolean test(JavaMethod method) {
        return !mappingAnnotationsOf(method).isEmpty();
      }
    };
  }

  private static ArchCondition<JavaMethod> mapOnlyRoutesStartingWith(String prefix) {
    return new ArchCondition<>("map only routes starting with '" + prefix + "'") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (String route : routesOf(method)) {
          events.add(
              new SimpleConditionEvent(
                  method, route.startsWith(prefix), method.getFullName() + " maps " + route));
        }
      }
    };
  }

  private static ArchCondition<JavaMethod> mapNoRouteStartingWithAnyOf(String... prefixes) {
    return new ArchCondition<>("map no route starting with " + String.join(" or ", prefixes)) {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (String route : routesOf(method)) {
          boolean reserved = false;
          for (String prefix : prefixes) {
            reserved = reserved || route.startsWith(prefix);
          }
          events.add(
              new SimpleConditionEvent(method, !reserved, method.getFullName() + " maps " + route));
        }
      }
    };
  }

  private static Set<String> routesOf(JavaMethod method) {
    List<String> classPaths = orRoot(mappingPathsOf(method.getOwner()));
    List<String> methodPaths = orRoot(mappingPathsOf(method));
    Set<String> routes = new LinkedHashSet<>();
    for (String classPath : classPaths) {
      for (String methodPath : methodPaths) {
        routes.add(concatPaths(classPath, methodPath));
      }
    }
    return routes;
  }

  private static List<String> orRoot(List<String> paths) {
    return paths.isEmpty() ? List.of("") : paths;
  }

  private static String concatPaths(String classPath, String methodPath) {
    String combined = trimTrailingSlash(classPath) + ensureLeadingSlash(methodPath);
    return combined.isEmpty() ? "/" : combined;
  }

  private static String trimTrailingSlash(String path) {
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static String ensureLeadingSlash(String path) {
    if (path.isEmpty()) {
      return "";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  private static List<String> mappingPathsOf(HasAnnotations<?> annotated) {
    List<String> paths = new ArrayList<>();
    for (JavaAnnotation<?> annotation : mappingAnnotationsOf(annotated)) {
      addAttributeValues(annotation, "value", paths);
      addAttributeValues(annotation, "path", paths);
    }
    return paths;
  }

  private static void addAttributeValues(
      JavaAnnotation<?> annotation, String attribute, List<String> into) {
    annotation
        .get(attribute)
        .ifPresent(
            value -> {
              if (value instanceof Object[] values) {
                for (Object element : values) {
                  into.add(String.valueOf(element));
                }
              } else {
                into.add(String.valueOf(value));
              }
            });
  }

  private static List<JavaAnnotation<?>> mappingAnnotationsOf(HasAnnotations<?> annotated) {
    List<JavaAnnotation<?>> mappings = new ArrayList<>();
    for (JavaAnnotation<?> annotation : annotated.getAnnotations()) {
      JavaClass annotationType = annotation.getRawType();
      if (annotationType.isEquivalentTo(RequestMapping.class)
          || annotationType.isAnnotatedWith(RequestMapping.class)) {
        mappings.add(annotation);
      }
    }
    return mappings;
  }
}

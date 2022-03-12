package com.github.forax.carrier.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodType.methodType;

public class Matcher {
  private static final MethodHandle THROW_NPE, IS_INSTANCE, EQUALS, IS_NULL, IS_NOT_NULL/*, TAP*/;
  static {
    var lookup = lookup();
    try {
      THROW_NPE = lookup.findStatic(Matcher.class, "throw_npe", methodType(void.class, String.class));
      IS_INSTANCE = lookup.findVirtual(Class.class, "isInstance", methodType(boolean.class, Object.class));
      EQUALS = lookup.findVirtual(Object.class, "equals", methodType(boolean.class, Object.class));
      IS_NULL = lookup.findStatic(Objects.class, "isNull", methodType(boolean.class, Object.class));
      IS_NOT_NULL = lookup.findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class));
      //TAP = lookup.findStatic(Matcher.class, "_tap", methodType(void.class, Object[].class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static void throw_npe(String message) {
    throw new NullPointerException(message);
  }

//  private static void _tap(Object... args) {
//    System.out.println("TAP " + Arrays.toString(args));
//  }


  private static void checkMatcher(MethodHandle matcher) {
    var type = matcher.type();
    if (type.parameterCount() != 2 ||
        type.returnType() != Object.class ||
        type.parameterType(1) != Object.class) {
      throw new IllegalArgumentException("invalid matcher " + matcher);
    }
  }

//  public static MethodHandle tap(MethodHandle matcher) {
//    return foldArguments(matcher, TAP.asCollector(Object[].class, matcher.type().parameterCount()).asType(matcher.type().changeReturnType(void.class)));
//  }

  // return o -> matcher.apply(o, carrier);
  public static MethodHandle of(Object carrier, MethodHandle matcher) {
    Objects.requireNonNull(carrier, "carrier is null");
    Objects.requireNonNull(matcher, "matcher is null");
    checkMatcher(matcher);
    return insertArguments(matcher, 1, carrier);
  }

  // return (o, carrier) -> null;
  public static MethodHandle doNotMatch(Class<?> type) {
    return dropArguments(constant(Object.class, null), 0, type, Object.class);
  }

  // return (o, carrier -> carrier
  static MethodHandle doMatch(Class<?> type) {
    return dropArguments(identity(Object.class),0, type);
  }

  // return (o, carrier) -> type.isInstance(o);
  public static MethodHandle isInstance(Class<?> declaredType, Class<?> type) {
    Objects.requireNonNull(declaredType, "declaredType is null");
    Objects.requireNonNull(type, "type is null");
    return dropArguments(IS_INSTANCE.bindTo(type).asType(methodType(boolean.class, declaredType)), 1, Object.class);
  }

  // return (o, carrier) -> o == null;
  public static MethodHandle isNull(Class<?> type) {
    Objects.requireNonNull(type, "type is null");
    return dropArguments(IS_NULL.asType(methodType(boolean.class, type)), 1, Object.class);
  }

  // return (o, carrier) -> constant.equals(o)
  public static MethodHandle isEquals(Class<?> type, Object constant) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(constant, "constant is null");
    return dropArguments(EQUALS.bindTo(constant).asType(methodType(boolean.class, type)), 1, Object.class);
  }

  // return (o, carrier) -> { throw new NullPointerException(); };
  public static MethodHandle throwNPE(Class<?> type, String message) {
    Objects.requireNonNull(type, "type is null");
    return dropArguments(THROW_NPE.bindTo(message).asType(methodType(Object.class)), 0, type, Object.class);
  }

  // return (o, carrier) -> matcher.apply(project.apply(o), carrier);
  public static MethodHandle project(MethodHandle project, MethodHandle  matcher) {
    Objects.requireNonNull(project, "project is null");
    Objects.requireNonNull(matcher, "matcher is null");
    var projectType = project.type();
    if (projectType.parameterCount() != 1 ||
        projectType.returnType() != matcher.type().parameterType(0)) {
      throw new IllegalArgumentException("invalid project " + project + " for matcher " + matcher);
    }
    checkMatcher(matcher);
    return filterArguments(matcher, 0, project);
  }

  // return (o, carrier) -> matcher.apply(o, with(o, carrier, binding));
  public static MethodHandle bind(int binding, CarrierMetadata carrierMetadata, MethodHandle matcher) {
    Objects.requireNonNull(carrierMetadata, "carrierInfo is null");
    Objects.requireNonNull(matcher, "matcher is null");
    if (binding < 0) {
      throw new IllegalArgumentException("binding negative " + binding);
    }
    checkMatcher(matcher);
    var mh = permuteArguments(matcher, methodType(Object.class, Object.class, matcher.type().parameterType(0), Object.class), 1, 0 );
    return foldArguments(mh, carrierMetadata.with(binding));
  }

  // return (o, carrier) -> with(o, carrier, binding);
  public static MethodHandle bind(int binding, CarrierMetadata carrierMetadata) {
    Objects.requireNonNull(carrierMetadata, "carrierInfo is null");
    if (binding < 0) {
      throw new IllegalArgumentException("binding negative " + binding);
    }
    return carrierMetadata.with(binding);
  }

  // return (o, carrier) -> test.test(o, carrier)? target.apply(o, carrier): fallback.apply(o, carrier);
  static MethodHandle test(MethodHandle test, MethodHandle target, MethodHandle fallback) {
    Objects.requireNonNull(test, "test is null");
    Objects.requireNonNull(target, "target is null");
    Objects.requireNonNull(fallback, "fallback is null");
    checkMatcher(target);
    checkMatcher(fallback);
    return guardWithTest(test, target, fallback);
  }

  // return (o, carrier) -> {
  //    var carrier2 = matcher1.apply(o, carrier);
  //    if (carrier2 == null) {
  //      return null;
  //    }
  //    return matcher2.apply(o, carrier2);
  //  };
  static MethodHandle and(MethodHandle  matcher1, MethodHandle  matcher2) {
    Objects.requireNonNull(matcher1, "matcher1 is null");
    Objects.requireNonNull(matcher2, "matcher2 is null");
    checkMatcher(matcher1);
    checkMatcher(matcher2);

    var type = matcher1.type().parameterType(0);
    var doNotMatch = doNotMatch(type);
    var test = dropArguments(IS_NULL, 0, type);
    var guard = guardWithTest(test, doNotMatch, matcher2);
    var mh = permuteArguments(guard, methodType(Object.class, Object.class, type, Object.class), 1, 0 );
    return foldArguments(mh, matcher1);
  }

  // return (o, carrier) -> {
  //    var carrier2 = matcher1.apply(o, carrier);
  //    if (carrier2 == null) {
  //      return matcher2.apply(o, carrier);
  //    }
  //    return carrier2;
  //  };
  static MethodHandle or(MethodHandle  matcher1, MethodHandle  matcher2) {
    Objects.requireNonNull(matcher1, "matcher1 is null");
    Objects.requireNonNull(matcher2, "matcher2 is null");
    checkMatcher(matcher1);
    checkMatcher(matcher2);

    var type = matcher1.type().parameterType(0);
    var target = dropArguments(matcher2, 0, Object.class);
    var fallback = dropArguments(identity(Object.class), 1, type, Object.class);
    var guard = guardWithTest(IS_NULL, target, fallback);
    return foldArguments(guard, matcher1);
  }

  // return (Type o, carrier) -> matcher.apply(o, carrier)
  public static MethodHandle cast(Class<?> type, MethodHandle matcher) {
    Objects.requireNonNull(matcher);
    checkMatcher(matcher);
    return matcher.asType(methodType(Object.class, type, Object.class));
  }

  // return (o, carrier) -> with(index, carrier, 0)
  public static MethodHandle index(Class<?> type, CarrierMetadata carrierMetadata, int index) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(carrierMetadata, "carrierInfo is null");
    return dropArguments(insertArguments(carrierMetadata.with(0), 0, index), 0, type);
  }

  // return carrier -> (carrier == null)? -1: carrier.component[0];
  public static MethodHandle switchResult(CarrierMetadata carrierMetadata) {
    return MethodHandles.guardWithTest(IS_NULL,
        dropArguments(constant(int.class, -1), 0, Object.class),
        carrierMetadata.accessor(0)
    );
  }

  // return carrier -> carrier == null;
  public static MethodHandle instanceOfResult() {
    return IS_NOT_NULL;
  }

  // Metadata associated with a Carrier
  public static final class CarrierMetadata {
    private final MethodHandle constructor;
    private final MethodHandle[] accessors;

    private Object empty;  // lazily initialized
    private final MethodHandle[] withers;  // array cell lazily initialized

    private CarrierMetadata(MethodHandle constructor, MethodHandle[] accessors) {
      this.constructor = constructor;
      this.accessors = accessors;
      this.withers = new MethodHandle[accessors.length];
    }

    // returns an empty carrier
    public Object empty() {
      if (empty != null) {
        return empty;
      }
      return empty = empty(constructor, accessors);
    }

    public MethodHandle accessor(int i) {
      return accessors[i];
    }

    // returns a mh that creates a new carrier from a value and a previous value of a carrier
    public MethodHandle with(int i) {
      var wither = withers[i];
      if (wither != null) {
        return wither;
      }
      return withers[i] = wither(constructor, accessors, i);
    }

    private static Object empty(MethodHandle constructor, MethodHandle[] accessors) {
      try {
        return insertArguments(constructor, 0,
            Arrays.stream(accessors)
                .map(accessor -> {
                  var type = accessor.type().returnType();
                  if (!type.isPrimitive()) {
                    return null;
                  }
                  return switch (type.descriptorString()) {
                    case "Z" -> false;
                    case "B" -> (byte) 0;
                    case "S" -> (short) 0;
                    case "C" -> '\0';
                    case "I" -> 0;
                    case "J" -> 0L;
                    case "F" -> 0f;
                    case "D" -> 0.;
                    default -> throw new AssertionError();
                  };
                })
                .toArray(Object[]::new))
            .invoke();
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new AssertionError(e);
      }
    }

    private static MethodHandle wither(MethodHandle constructor, MethodHandle[] accessors, int binding) {
      var filters = new MethodHandle[accessors.length];
      var reorder = new int[filters.length];
      for (var i = 0; i < filters.length; i++) {
        filters[i] = (i == binding) ? null : accessors[i];
        reorder[i] = (i == binding) ? 0 : 1;
      }
      var mh = filterArguments(constructor, 0, filters);
      var accessorBindingType = accessors[binding].type();
      var carrierType = accessorBindingType.parameterType(0);
      var bindingType = accessorBindingType.returnType();
      return permuteArguments(mh, methodType(carrierType, bindingType, carrierType), reorder);
    }

    private static CarrierMetadata from(MethodHandle constructor, MethodHandle[] accessors) {
      return new CarrierMetadata(constructor, accessors);
    }

    public static CarrierMetadata fromCarrier(MethodType carrierType) {
      return from(Carrier.constructor(carrierType), Carrier.components(carrierType));
    }

    public static CarrierMetadata fromRecord(Lookup lookup, Class<?> recordClass) {
      if (!recordClass.isRecord()) {
        throw new IllegalArgumentException(recordClass.getName() + " is not a record");
      }
      var recordComponents = recordClass.getRecordComponents();
      var accessors = Arrays.stream(recordComponents)
          .map(recordComponent -> {
            try {
              return lookup.unreflect(recordComponent.getAccessor());
            } catch (IllegalAccessException e) {
              throw (IllegalAccessError) new IllegalAccessError().initCause(e);
            }
          })
          .toArray(MethodHandle[]::new);
      MethodHandle constructor;
      try {
        constructor = lookup.findConstructor(recordClass, methodType(void.class,
            Arrays.stream(recordComponents).map(recordComponent -> recordComponent.getAccessor().getReturnType()).toArray(Class[]::new)));
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      } catch (NoSuchMethodException e) {
        throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
      }
      return from(constructor, accessors);
    }
  }
}

package com.github.forax.carrier.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodType.methodType;

public class Matcher {
  private static final MethodHandle THROW_NPE, IS_INSTANCE, IS_NULL, TAP;
  static {
    var lookup = lookup();
    try {
      THROW_NPE = lookup.findStatic(Matcher.class, "throw_npe", methodType(void.class, String.class));
      IS_INSTANCE = lookup.findVirtual(Class.class, "isInstance", methodType(boolean.class, Object.class));
      IS_NULL = lookup.findStatic(Objects.class, "isNull", methodType(boolean.class, Object.class));
      TAP = lookup.findStatic(Matcher.class, "_tap", methodType(void.class, Object[].class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static void throw_npe(String message) {
    throw new NullPointerException(message);
  }

  private static void _tap(Object... args) {
    System.out.println("TAP " + Arrays.toString(args));
  }

  // a pattern is typed
  // (T;Ljava/lang/Object)Ljava/lang/Object;


  private static void checkMatcher(MethodHandle matcher) {
    var type = matcher.type();
    if (type.parameterCount() != 2 ||
        type.returnType() != Object.class ||
        type.parameterType(1) != Object.class) {
      throw new IllegalArgumentException("invalid matcher " + matcher);
    }
  }

  public static MethodHandle tap(MethodHandle matcher) {
    return foldArguments(matcher, TAP.asCollector(Object[].class, matcher.type().parameterCount()).asType(matcher.type().changeReturnType(void.class)));
  }

  // return o -> matcher.apply(o, carrier);
  public static MethodHandle of(Object carrier, MethodHandle matcher) {
    Objects.requireNonNull(carrier, "carrier is null");
    Objects.requireNonNull(matcher, "matcher is null");
    checkMatcher(matcher);
    return insertArguments(matcher, 1, carrier);
  }

  // return (o, carrier) -> with(index, carrier, 0);
  //public static MethodHandle match(Class<?> type, CarrierMetadata carrierMetadata, int index) {
  //  Objects.requireNonNull(type, "type is null");
  //  return dropArguments(insertArguments(carrierMetadata.with(0), 0, index), 0, type);
  //}

  // return (o, carrier) -> null;
  public static MethodHandle doNotMatch(Class<?> type) {
    return dropArguments(constant(Object.class, null), 0, type, Object.class);
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

  // return (o, carrier) -> with(index, carrier, 0)
  public static MethodHandle index(Class<?> type, CarrierMetadata carrierMetadata, int index) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(carrierMetadata, "carrierInfo is null");
    return dropArguments(insertArguments(carrierMetadata.with(0), 0, index), 0, type);
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
  static MethodHandle or(MethodHandle  matcher1, MethodHandle  matcher2) {
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

  // Metadata associated with a Carrier
  public record CarrierMetadata(Object empty, MethodHandle[] accessors, MethodHandle[] withers) {
    public CarrierMetadata(MethodType carrierType) {
      this(empty(carrierType), Carrier.components(carrierType), withers(carrierType));
    }

    public MethodHandle accessor(int i) {
      return accessors[i];
    }

    // return a mh that creates a new carrier from a value and a previous value of a carrier
    public MethodHandle with(int i) {
      return withers[i];
    }

    private static Object empty(MethodType carrierType) {
      try {
        return MethodHandles.insertArguments(Carrier.constructor(carrierType), 0,
                Arrays.stream(Carrier.components(carrierType))
                    .map(c -> {
                      var type = c.type().returnType();
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
            .invokeExact();
      } catch (RuntimeException | Error e) {
        throw e;
      }catch (Throwable e) {
        throw new AssertionError(e);
      }
    }

    private static MethodHandle[] withers(MethodType carrierType) {
      var constructor = Carrier.constructor(carrierType);
      var accessors = Carrier.components(carrierType);
      return IntStream.range(0, accessors.length)
          .mapToObj(i -> with(constructor, accessors, i))
          .toArray(MethodHandle[]::new);
    }

    private static MethodHandle with(MethodHandle constructor, MethodHandle[] accessors, int binding) {
      var filters = new MethodHandle[accessors.length];
      var reorder = new int[filters.length];
      for(var i = 0; i < filters.length; i++) {
        filters[i] = (i == binding)? null: accessors[i];
        reorder[i] = (i == binding)? 0: 1;
      }
      var mh = filterArguments(constructor, 0, filters);
      var bindingType = accessors[binding].type().returnType();
      return MethodHandles.permuteArguments(mh, MethodType.methodType(Object.class, bindingType, Object.class), reorder);
    }
  }

  // return (Type o, carrier) -> matcher.apply(o, carrier)
  public static MethodHandle cast(Class<?> type, MethodHandle matcher) {
    Objects.requireNonNull(matcher);
    checkMatcher(matcher);
    return matcher.asType(methodType(Object.class, type, Object.class));
  }

  private static final ClassValue<RecordComponent[]> RECORD_COMPONENTS = new ClassValue<>() {
    @Override
    protected RecordComponent[] computeValue(Class<?> type) {
      if (!type.isRecord()) {
        throw new IllegalArgumentException(type.getName() + " is not a record");
      }
      return type.getRecordComponents();
    }
  };

  // return o -> o.component[position]
  public static MethodHandle record_accessor(Lookup lookup, Class<?> recordClass, int position) {
    var accessor = RECORD_COMPONENTS.get(recordClass)[position].getAccessor();
    try {
      return lookup.unreflect(accessor);
    } catch (IllegalAccessException e) {
      throw (LinkageError) new LinkageError().initCause(e);
    }
  }

  public static void main(String[] args) throws Throwable {
    record Point(int x, int y) {}
    record Rectangle(Point p1, Point p2) {}

    // Object o = ...
    //switch(o) {
    //  case Rectangle(Point p1, Point p2) -> ...
    //}

    var lookup = MethodHandles.lookup();

    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class, Point.class, Point.class));
    var empty = carrierMetadata.empty();

    var op = of(empty,
        test(isInstance(Object.class, Rectangle.class),
            cast(Object.class,
                or(project(record_accessor(lookup, Rectangle.class, 0),
                        test(isNull(Point.class),
                            doNotMatch(Point.class),
                            bind(1, carrierMetadata))),
                    project(record_accessor(lookup, Rectangle.class, 1),
                        test(isNull(Point.class),
                            doNotMatch(Point.class),
                            bind(2, carrierMetadata,
                                index(Point.class, carrierMetadata, 0))))
                )
            ),
            throwNPE(Object.class, "o is null")
        )
    );

    // match: new Rectangle(new Point(1, 2), new Point(3, 4))
    var rectangle1 = (Object) new Rectangle(new Point(1, 2), new Point(3, 4));
    var carrier1 = op.invokeExact(rectangle1);
    System.out.println("result: " + (int) carrierMetadata.accessor(0).invokeExact(carrier1));
    System.out.println("binding 1 " + (Point) carrierMetadata.accessor(1).invokeExact(carrier1));
    System.out.println("binding 2 " + (Point) carrierMetadata.accessor(2).invokeExact(carrier1));

    // match: new Rectangle(null, new Point(1, 2))
    var rectangle2 = (Object) new Rectangle(null, new Point(1, 2));
    var carrier2 = op.invokeExact(rectangle2);
    System.out.println("carrier: " + carrier2);

    // match: new Rectangle(new Point(1, 2), null)
    var rectangle3 = (Object) new Rectangle(new Point(1, 2), null);
    var carrier3 = op.invokeExact(rectangle3);
    System.out.println("carrier: " + carrier3);
  }
}

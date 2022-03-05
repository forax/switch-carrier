package com.github.forax.carrier.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

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

public class Patterns {
  private static final MethodHandle THROW_NPE, INDEX_MINUS_ONE, IS_INSTANCE, IS_NULL;
  static {
    var lookup = lookup();
    try {
      THROW_NPE = lookup.findStatic(Patterns.class, "throwNPE", methodType(void.class, String.class));
      INDEX_MINUS_ONE = lookup.findStatic(Patterns.class, "indexMinusOne", methodType(boolean.class, int.class));
      IS_INSTANCE = lookup.findStatic(Patterns.class, "isInstance", methodType(boolean.class, Class.class, Object.class));
      IS_NULL = lookup.findStatic(Patterns.class, "isNull", methodType(boolean.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static void throwNPE(String message) {
    throw new NullPointerException(message);
  }

  private static boolean indexMinusOne(int index) {
    return index == -1;
  }

  private static boolean isInstance(Class<?> type, Object o) {
    return type.isInstance(o);
  }

  private static boolean isNull(Object o) {
    return o == null;
  }

  // a pattern is typed
  // (T;Ljava/lang/Object)Ljava/lang/Object;


  private static void checkPattern(MethodHandle pattern) {
    var type = pattern.type();
    if (type.parameterCount() != 2 ||
        type.returnType() != Object.class ||
        type.parameterType(1) != Object.class) {
      throw new IllegalArgumentException("invalid pattern " + pattern);
    }
  }

  // return o -> pattern.apply(o, carrier);
  public static MethodHandle of(Object carrier, MethodHandle pattern) {
    Objects.requireNonNull(carrier, "carrier is null");
    Objects.requireNonNull(pattern, "pattern is null");
    checkPattern(pattern);
    return insertArguments(pattern, 1, carrier);
  }

  // return (o, carrier) -> with(index, carrier, 0);
  public static MethodHandle match(Class<?> type, CarrierMetadata carrierMetadata, int index) {
    Objects.requireNonNull(type, "type is null");
    return dropArguments(insertArguments(carrierMetadata.with(0), 0, index), 0, type);

  }

  // return (o, carrier) -> null;
  public static MethodHandle do_not_match(Class<?> type, CarrierMetadata carrierMetadata) {
    return match(type, carrierMetadata, -1);
  }

  // return (o, carrier) -> type.isInstance(o);
  public static MethodHandle is_instance(Class<?> declaredType, Class<?> type) {
    Objects.requireNonNull(declaredType, "declaredType is null");
    Objects.requireNonNull(type, "type is null");
    return dropArguments(IS_INSTANCE.bindTo(type).asType(methodType(boolean.class, declaredType)), 1, Object.class);
  }

  // return (o, carrier) -> o == null;
  public static MethodHandle is_null(Class<?> type) {
    Objects.requireNonNull(type, "type is null");
    return dropArguments(IS_NULL.asType(methodType(boolean.class, type)), 1, Object.class);
  }

  // return (o, carrier) -> { throw new NullPointerException(); };
  public static MethodHandle throw_NPE(Class<?> type, String message) {
    Objects.requireNonNull(type, "type is null");
    return dropArguments(THROW_NPE.bindTo(message).asType(methodType(Object.class)), 0, type, Object.class);
  }

  // return (o, carrier) -> pattern.apply(project.apply(o), carrier);
  public static MethodHandle project(MethodHandle project, MethodHandle  pattern) {
    Objects.requireNonNull(project, "project is null");
    Objects.requireNonNull(pattern, "pattern is null");
    var projectType = project.type();
    if (projectType.parameterCount() != 1 ||
        projectType.returnType() != pattern.type().parameterType(0)) {
      throw new IllegalArgumentException("invalid project " + project + " for pattern " + pattern);
    }
    checkPattern(pattern);
    return filterArguments(pattern, 0, project);
  }

  // return (o, carrier) -> pattern.apply(o, with(o, carrier, binding));
  public static MethodHandle bind(int binding, CarrierMetadata carrierMetadata, MethodHandle pattern) {
    Objects.requireNonNull(carrierMetadata, "carrierInfo is null");
    Objects.requireNonNull(pattern, "pattern is null");
    if (binding < 0) {
      throw new IllegalArgumentException("binding negative " + binding);
    }
    checkPattern(pattern);
    var mh = permuteArguments(pattern, methodType(Object.class, Object.class, pattern.type().parameterType(0), Object.class), 1, 0 );
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
    checkPattern(target);
    checkPattern(fallback);
    return guardWithTest(test, target, fallback);
  }

  // return (o, carrier) -> {
  //    var carrier2 = pattern1.apply(o, carrier);
  //    if (carrier2.accessor[Ø] == -1) {
  //      return carrier2;
  //    }
  //    return pattern2.apply(o, carrier2);
  //  };
  static MethodHandle or(CarrierMetadata carrierMetadata, MethodHandle  pattern1, MethodHandle  pattern2) {
    Objects.requireNonNull(carrierMetadata, "carrierMetadata is null");
    Objects.requireNonNull(pattern1, "pattern1 is null");
    Objects.requireNonNull(pattern2, "pattern2 is null");
    checkPattern(pattern1);
    checkPattern(pattern2);

    var type = pattern1.type().parameterType(0);
    var test = filterReturnValue(dropArguments(carrierMetadata.accessor(0), 0, type), INDEX_MINUS_ONE);
    var identity = dropArguments(identity(Object.class), 0, type);
    var guard = guardWithTest(test, identity, pattern2);
    var mh = permuteArguments(guard, methodType(Object.class, Object.class, type, Object.class), 1, 0 );
    return foldArguments(mh, pattern1);
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

  // return (Type o, carrier) -> pattern.apply(o, carrier)
  public static MethodHandle cast(Class<?> type, MethodHandle pattern) {
    Objects.requireNonNull(pattern);
    checkPattern(pattern);
    return pattern.asType(methodType(Object.class, type, Object.class));
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
        test(is_instance(Object.class, Rectangle.class),
            cast(Object.class,
                or(carrierMetadata,
                    project(record_accessor(lookup, Rectangle.class, 0),
                        test(is_null(Point.class),
                            do_not_match(Point.class, carrierMetadata),
                            bind(1, carrierMetadata))),
                    project(record_accessor(lookup, Rectangle.class, 1),
                        test(is_null(Point.class),
                            do_not_match(Point.class, carrierMetadata),
                            bind(2, carrierMetadata,
                                match(Point.class, carrierMetadata, 0))))
                )
            ),
            throw_NPE(Object.class, "o is null")
        )
    );

    // match: new Rectangle(new Point(1, 2), new Point(3, 4))
    var rectangle1 = (Object) new Rectangle(new Point(1, 2), new Point(3, 4));
    var carrier1 = op.invokeExact(rectangle1);
    System.out.println("result: " + (int) carrierMetadata.accessor(0).invokeExact(carrier1));
    System.out.println("binding 1 " + (Point) carrierMetadata.accessor(1).invokeExact(carrier1));
    System.out.println("binding 2 " + (Point) carrierMetadata.accessor(2).invokeExact(carrier1));

    // match: new Rectangle(new Point(1, 2), null)
    var rectangle2 = (Object) new Rectangle(new Point(1, 2), null);
    var carrier2 = op.invokeExact(rectangle2);
    System.out.println("result: " + (int) carrierMetadata.accessor(0).invokeExact(carrier2));
    System.out.println("binding 1 " + (Point) carrierMetadata.accessor(1).invokeExact(carrier2));
    System.out.println("binding 2 " + (Point) carrierMetadata.accessor(2).invokeExact(carrier2));
  }
}

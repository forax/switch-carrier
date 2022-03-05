package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Patterns.CarrierMetadata;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

public class PatternsTest {
  private static MethodHandle asMH(Function<?, ?> op, Class<?> returnType, Class<?> parameterType) throws NoSuchMethodException, IllegalAccessException {
    return MethodHandles.lookup()
        .bind(op, "apply", methodType(Object.class, Object.class))
        .asType(methodType(returnType, parameterType));
  }

  private static MethodHandle asMH(Predicate<?> op, Class<?> parameterType) throws NoSuchMethodException, IllegalAccessException {
    return MethodHandles.lookup()
        .bind(op, "test", methodType(boolean.class, Object.class))
        .asType(methodType(boolean.class, parameterType));
  }

  @Test
  public void empty() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class, Object.class));
    var empty = carrierMetadata.empty();

    assertEquals(0, (int) carrierMetadata.accessor(0).invokeExact(empty));
    assertNull(carrierMetadata.accessor(1).invokeExact(empty));
  }

  @Test
  public void match() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.match(int.class, carrierMetadata, 42);
    var carrier = (Object) pattern.invokeExact(666, empty);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(42, result);
  }

  @Test
  public void do_not_match() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.do_not_match(int.class, carrierMetadata);
    var carrier = (Object) pattern.invokeExact(3, empty);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(-1, result);
  }

  @Test
  public void is_instance() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.is_instance(Object.class, String.class);
    var result = (boolean) pattern.invokeExact((Object) "data", empty);
    assertTrue(result);

    var result2 = (boolean) pattern.invokeExact((Object) 777, empty);
    assertFalse(result2);
  }

  @Test
  public void throw_NPE() {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.throw_NPE(String.class, "achtung !");
    assertThrows(NullPointerException.class, () -> {
      var result = (Object) pattern.invokeExact("data", empty);
    });
  }

  @Test
  public void project() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.project(asMH((String s) -> s.length(), int.class, String.class), Patterns.match(int.class, carrierMetadata, 42));
    var carrier = pattern.invokeExact("hello", empty);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(42, result);
  }

  @Test
  public void with() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var mh = carrierMetadata.with(0);
    var carrier = mh.invokeExact(666, empty);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(666, result);
  }

  @Test
  public void bind3Arguments() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class, String.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.bind(1, carrierMetadata, Patterns.match(String.class, carrierMetadata, 42));
    var carrier = pattern.invokeExact("hello", empty);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    var binding = (String) carrierMetadata.accessor(1).invokeExact(carrier);
    assertEquals(42, result);
    assertEquals("hello", binding);
  }

  @Test
  public void bind2Arguments() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class, String.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.bind(1, carrierMetadata);
    var carrier = pattern.invokeExact("hello", empty);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    var binding = (String) carrierMetadata.accessor(1).invokeExact(carrier);
    assertEquals(0, result);
    assertEquals("hello", binding);
  }

  @Test
  public void test() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.test(asMH((String s) -> s.startsWith("foo"), String.class),
        Patterns.match(String.class, carrierMetadata, 0),
        Patterns.do_not_match(String.class, carrierMetadata));
    var carrier = pattern.invokeExact("foobar", empty);
    var index = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(0, index);
  }

  @Test
  public void or() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.or(carrierMetadata,
        Patterns.match(String.class, carrierMetadata, 1),
        Patterns.match(String.class, carrierMetadata, 2)
    );
    var carrier = pattern.invokeExact("foo", empty);
    assertEquals(2, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }

  @Test
  public void orFirstFalse() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.or(carrierMetadata,
        Patterns.match(String.class, carrierMetadata, -1),
        Patterns.match(String.class, carrierMetadata, 2)
    );
    var carrier = pattern.invokeExact("foo", empty);
    assertEquals(-1, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }

  @Test
  public void record_accessor() throws Throwable {
    record Point(int x, int y) {}

    var projection0 = Patterns.record_accessor(MethodHandles.lookup(), Point.class, 0);
    var projection1 = Patterns.record_accessor(MethodHandles.lookup(), Point.class, 1);
    assertEquals(42, (int) projection0.invokeExact(new Point(42, 111)));
    assertEquals(111, (int) projection1.invokeExact(new Point(42, 111)));
  }

  @Test
  public void of() throws Throwable {
    var carrierMetadata = new CarrierMetadata(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var pattern = Patterns.of(empty, Patterns.match(String.class, carrierMetadata, 42));
    var carrier = pattern.invokeExact("hello");
    assertEquals(42, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }




}
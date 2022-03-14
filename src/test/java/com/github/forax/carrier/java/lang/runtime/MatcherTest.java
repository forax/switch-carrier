package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;
import com.github.forax.carrier.java.lang.runtime.Pattern.TypePattern;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

public class MatcherTest {
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
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class, Object.class));
    var empty = carrierMetadata.empty();

    assertEquals(0, (int) carrierMetadata.accessor(0).invokeExact(empty));
    assertNull(carrierMetadata.accessor(1).invokeExact(empty));
  }

  @Test
  public void doNotMatch() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.doNotMatch(int.class);
    var carrier = (Object) matcher.invokeExact(3, empty);
    assertNull(carrier);
  }

  @Test
  public void doMatch() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.doMatch(int.class);
    var carrier = (Object) matcher.invokeExact(3, empty);
    assertNotNull(carrier);
    assertSame(empty, carrier);
  }

  @Test
  public void isInstance() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isInstance(Object.class, String.class);
    var result = (boolean) matcher.invokeExact((Object) "data", empty);
    assertTrue(result);

    var result2 = (boolean) matcher.invokeExact((Object) 777, empty);
    assertFalse(result2);
  }

  @Test
  public void isNull() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isNull(String.class);
    assertFalse((boolean) matcher.invokeExact( "data", empty));
    assertTrue((boolean) matcher.invokeExact( (String) null, empty));
  }

  @Test
  public void isEquals() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(String.class, "hello");
    assertTrue((boolean) matcher.invokeExact( "hello", empty));
    assertTrue((boolean) matcher.invokeExact( new String("hello"), empty));
    assertFalse((boolean) matcher.invokeExact( "foo", empty));
    assertFalse((boolean) matcher.invokeExact( (String) null, empty));
  }

  @Test
  public void isEqualsBoolean() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(boolean.class, true);
    assertTrue((boolean) matcher.invokeExact( true, empty));
    assertFalse((boolean) matcher.invokeExact( false, empty));
  }

  @Test
  public void isEqualsByte() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(byte.class, (byte) 42);
    assertTrue((boolean) matcher.invokeExact( (byte) 42, empty));
    assertFalse((boolean) matcher.invokeExact( (byte) 101, empty));
  }

  @Test
  public void isEqualsShort() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(short.class, (short) 42);
    assertTrue((boolean) matcher.invokeExact( (short) 42, empty));
    assertFalse((boolean) matcher.invokeExact( (short) 101, empty));
  }

  @Test
  public void isEqualsInt() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(int.class, 42);
    assertTrue((boolean) matcher.invokeExact( 42, empty));
    assertFalse((boolean) matcher.invokeExact( 101, empty));
  }

  @Test
  public void isEqualsLong() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(long.class, 42L);
    assertTrue((boolean) matcher.invokeExact( 42L, empty));
    assertFalse((boolean) matcher.invokeExact( 101L, empty));
  }

  @Test
  public void isEqualsFloat() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(float.class, 42f);
    assertTrue((boolean) matcher.invokeExact( 42f, empty));
    assertFalse((boolean) matcher.invokeExact( 101f, empty));
  }

  @Test
  public void isEqualsDouble() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.isEquals(double.class, 42.);
    assertTrue((boolean) matcher.invokeExact( 42., empty));
    assertFalse((boolean) matcher.invokeExact( 101., empty));
  }

  @Test
  public void throwNPE() {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class));
    var empty = carrierMetadata.empty();

    var pattern = new TypePattern(String.class);

    var matcher = Matcher.throwNPE(String.class, pattern, pattern);
    assertThrows(NullPointerException.class, () -> {
      var result = (Object) matcher.invokeExact("data", empty);
    });
  }

  @Test
  public void project() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.project(asMH((String s) -> s.length(), int.class, String.class), Matcher.index(int.class, carrierMetadata, 42));
    var carrier = matcher.invokeExact("hello", empty);
    assertNotNull(carrier);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(42, result);
  }

  @Test
  public void with() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var mh = carrierMetadata.with(0);
    var carrier = mh.invokeExact(666, empty);
    assertNotNull(carrier);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(666, result);
  }

  @Test
  public void bind3Arguments() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class, String.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.bind(1, carrierMetadata, Matcher.index(String.class, carrierMetadata, 42));
    var carrier = matcher.invokeExact("hello", empty);
    assertNotNull(carrier);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    var binding = (String) carrierMetadata.accessor(1).invokeExact(carrier);
    assertEquals(42, result);
    assertEquals("hello", binding);
  }

  @Test
  public void bind2Arguments() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class, String.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.bind(1, carrierMetadata);
    var carrier = matcher.invokeExact("hello", empty);
    assertNotNull(carrier);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    var binding = (String) carrierMetadata.accessor(1).invokeExact(carrier);
    assertEquals(0, result);
    assertEquals("hello", binding);
  }

  @Test
  public void test() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.test(asMH((String s) -> s.startsWith("foo"), String.class),
        Matcher.index(String.class, carrierMetadata, 0),
        Matcher.doNotMatch(String.class));
    var carrier = matcher.invokeExact("foobar", empty);
    assertNotNull(carrier);
    var index = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(0, index);
  }

  @Test
  public void and() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.and(
        Matcher.index(long.class, carrierMetadata, 1),
        Matcher.index(long.class, carrierMetadata, 2)
    );
    var carrier = matcher.invokeExact(123L, empty);
    assertNotNull(carrier);
    assertEquals(2, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }

  @Test
  public void andFirstFalse() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.and(
        Matcher.doNotMatch(long.class),
        Matcher.index(long.class, carrierMetadata, 2)
    );
    var carrier = matcher.invokeExact(123L, empty);
    assertNull(carrier);
  }

  @Test
  public void or() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.or(
        Matcher.index(long.class, carrierMetadata, 1),
        Matcher.index(long.class, carrierMetadata, 2)
    );
    var carrier = matcher.invokeExact(123L, empty);
    assertNotNull(carrier);
    assertEquals(1, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }

  @Test
  public void orFirstFalse() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.or(
        Matcher.doNotMatch(long.class),
        Matcher.index(long.class, carrierMetadata, 2)
    );
    var carrier = matcher.invokeExact(123L, empty);
    assertNotNull(carrier);
    assertEquals(2, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }

  @Test
  public void record_accessor() throws Throwable {
    record Point(int x, int y) {}
    var pointMetadata = CarrierMetadata.fromRecord(MethodHandles.lookup(), Point.class);

    var projection0 = pointMetadata.accessor(0);
    var projection1 = pointMetadata.accessor(1);
    assertEquals(42, (int) projection0.invokeExact(new Point(42, 111)));
    assertEquals(111, (int) projection1.invokeExact(new Point(42, 111)));
  }

  @Test
  public void index() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.index(int.class, carrierMetadata, 42);
    var carrier = (Object) matcher.invokeExact(666, empty);
    assertNotNull(carrier);
    var result = (int) carrierMetadata.accessor(0).invokeExact(carrier);
    assertEquals(42, result);
  }

  @Test
  public void switchResultDoNotMatch() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var carrier = Matcher.doNotMatch(String.class).invokeExact("hello", empty);
    var switchResult = Matcher.switchResult(carrierMetadata);
    var result = (int) switchResult.invokeExact(carrier);
    assertEquals(-1, result);
  }

  @Test
  public void switchResultMatch() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var carrier = Matcher.index(String.class, carrierMetadata, 42).invokeExact("hello", empty);
    var switchResult = Matcher.switchResult(carrierMetadata);
    var result = (int) switchResult.invokeExact(carrier);
    assertEquals(42, result);
  }

  @Test
  public void instanceOfResultDoNotMatch() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var carrier = Matcher.doNotMatch(String.class).invokeExact("hello", empty);
    var instanceofResult = Matcher.instanceOfResult();
    var result = (boolean) instanceofResult.invokeExact(carrier);
    assertFalse(result);
  }

  @Test
  public void instanceOfResultMatch() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var carrier = Matcher.index(String.class, carrierMetadata, 42).invokeExact("hello", empty);
    var instanceofResult = Matcher.instanceOfResult();
    var result = (boolean) instanceofResult.invokeExact(carrier);
    assertTrue(result);
  }

  @Test
  public void of() throws Throwable {
    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class));
    var empty = carrierMetadata.empty();

    var matcher = Matcher.of(empty, Matcher.index(String.class, carrierMetadata, 42));
    var carrier = matcher.invokeExact("hello");
    assertNotNull(carrier);
    assertEquals(42, (int) carrierMetadata.accessor(0).invokeExact(carrier));
  }
}
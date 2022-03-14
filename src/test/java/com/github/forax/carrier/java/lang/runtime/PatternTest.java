package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;
import com.github.forax.carrier.java.lang.runtime.Pattern.ConstantPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.NullPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.OrPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.RecordPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.ResultPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.TypePattern;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.*;
public class PatternTest {

  @Test
  public void nullPattern() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class);
    var empty = CarrierMetadata.fromCarrier(carrierType).empty();

    var pattern = new NullPattern();
    var matcher = pattern.toMatcher(lookup, String.class, carrierType, 0, false);

    // match
    var carrier1 = matcher.invokeExact((String) null, empty);
    assertSame(empty, carrier1);

    // do not match
    var carrier2 = matcher.invokeExact("hello", empty);
    assertNull(carrier2);
  }

  @Test
  public void constantPattern() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class);
    var empty = CarrierMetadata.fromCarrier(carrierType).empty();

    var pattern = new ConstantPattern("hello");
    var matcher = pattern.toMatcher(lookup, String.class, carrierType, 0, false);

    // match
    var carrier1 = matcher.invokeExact("hello", empty);
    assertSame(empty, carrier1);

    // do not match
    var carrier2 = matcher.invokeExact("foo", empty);
    assertNull(carrier2);

    var carrier3 = matcher.invokeExact((String) null, empty);
    assertNull(carrier3);
  }

  @Test
  public void constantPatternInt() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class);
    var empty = CarrierMetadata.fromCarrier(carrierType).empty();

    var pattern = new ConstantPattern(42);
    var matcher = pattern.toMatcher(lookup, int.class, carrierType, 0, false);

    // match
    var carrier1 = matcher.invokeExact(42, empty);
    assertSame(empty, carrier1);

    // do not match
    var carrier2 = matcher.invokeExact(101, empty);
    assertNull(carrier2);
  }

  @Test
  public void typePattern() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class, String.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new TypePattern(String.class);
    var matcher = pattern.toMatcher(lookup, CharSequence.class, carrierType, 0, false);

    // match
    var carrier1 = matcher.invokeExact((CharSequence) "hello", empty);
    assertNotNull(carrier1);
    assertEquals("hello", (String) carrierMetadata.accessor(0).invokeExact(carrier1));

    // do not match
    var carrier2 = matcher.invokeExact((CharSequence) new StringBuilder("foo"), empty);
    assertNull(carrier2);

    var carrier3 = matcher.invokeExact((CharSequence) null, empty);
    assertNull(carrier3);
  }

  @Test
  public void typePatternEmitNull() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class, String.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new TypePattern(String.class);
    var matcher = pattern.toMatcher(lookup, CharSequence.class, carrierType, 0, true);

    // call with null
    var carrier = matcher.invokeExact((CharSequence) null, empty);
    assertNull(carrier);
  }

  @Test
  public void recordPattern() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class, int.class, int.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    record Point(int x, int y) {}

    var pattern = new RecordPattern(Point.class,
        new TypePattern(int.class),
        new TypePattern(int.class));
    var matcher = pattern.toMatcher(lookup, Record.class, carrierType, 0, false);

    // match
    var carrier1 = matcher.invokeExact((Record) new Point(2, 3), empty);
    assertNotNull(carrier1);
    assertEquals(2, (int) carrierMetadata.accessor(0).invokeExact(carrier1));
    assertEquals(3, (int) carrierMetadata.accessor(1).invokeExact(carrier1));

    // do not match
    record NotAPoint() {}
    var carrier2 = matcher.invokeExact((Record) new NotAPoint(), empty);
    assertNull(carrier2);

    var carrier3 = matcher.invokeExact((Record) null, empty);
    assertNull(carrier3);
  }

  @Test
  public void recordPatternEmitNull() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class, int.class, int.class, int.class, int.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    record Point(int x, int y) {}
    record Rectangle(Point upperLeft, Point lowerRight) {}

    var pattern = new RecordPattern(Rectangle.class,
        new RecordPattern(Point.class,
            new TypePattern(int.class),
            new TypePattern(int.class)
        ),
        new RecordPattern(Point.class,
            new TypePattern(int.class),
            new TypePattern(int.class)
        ));
    var matcher = pattern.toMatcher(lookup, Record.class, carrierType, 0, true);

    // call with Person(null)
    assertThrows(NullPointerException.class,  () -> {
      var carrier = matcher.invokeExact((Record) null, empty);
    });

    // call with Rectangle(null, Point(3, 4))
    assertThrows(NullPointerException.class,  () -> {
      var carrier = matcher.invokeExact((Record) new Rectangle(null, new Point(3, 4)), empty);
    });

    // call with Rectangle(Point(1, 2), null)
    assertThrows(NullPointerException.class,  () -> {
      var carrier = matcher.invokeExact((Record) new Rectangle(new Point(1, 2), null), empty);
    });
  }

  @Test
  public void orPattern() throws Throwable {
    record Box(Object o) {}

    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class, int.class, String.class, Integer.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new OrPattern(
        new ResultPattern(1,
            new RecordPattern(Box.class,
                new TypePattern(String.class))),
        new ResultPattern(2,
            new RecordPattern(Box.class,
                new TypePattern(Integer.class))));
    var matcher = pattern.toMatcher(lookup, Box.class, carrierType, 1, false);

    // match
    var carrier1 = matcher.invokeExact(new Box("hello"), empty);
    assertNotNull(carrier1);
    assertEquals(1, (int) carrierMetadata.accessor(0).invokeExact(carrier1));
    assertEquals("hello", (String) carrierMetadata.accessor(1).invokeExact(carrier1));

    // match
    var carrier2 = matcher.invokeExact(new Box(42), empty);
    assertNotNull(carrier2);
    assertEquals(2, (int) carrierMetadata.accessor(0).invokeExact(carrier2));
    assertEquals(42, (Integer) carrierMetadata.accessor(2).invokeExact(carrier2));

    // do not match
    var carrier3 = matcher.invokeExact((new Box(3.2)), empty);
    assertNull(carrier3);

    var carrier4 = matcher.invokeExact(new Box(null), empty);
    assertNull(carrier4);
  }

  @Test
  public void resultPattern() throws Throwable {
    var lookup = MethodHandles.lookup();
    var carrierType = MethodType.methodType(Object.class, int.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new ResultPattern(42,
        new ConstantPattern("hello"));
    var matcher = pattern.toMatcher(lookup, String.class, carrierType, 1, false);

    // match
    var carrier1 = matcher.invokeExact("hello", empty);
    assertNotNull(carrier1);
    assertEquals(42, (int) carrierMetadata.accessor(0).invokeExact(carrier1));

    // do not match
    var carrier2 = matcher.invokeExact("foo", empty);
    assertNull(carrier2);

    var carrier3 = matcher.invokeExact((String) null, empty);
    assertNull(carrier3);
  }
}
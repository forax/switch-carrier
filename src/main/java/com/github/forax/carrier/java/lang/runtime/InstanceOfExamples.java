package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;
import com.github.forax.carrier.java.lang.runtime.Pattern.RecordPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.TypePattern;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.github.forax.carrier.java.lang.runtime.Matcher.bind;
import static com.github.forax.carrier.java.lang.runtime.Matcher.cast;
import static com.github.forax.carrier.java.lang.runtime.Matcher.doNotMatch;
import static com.github.forax.carrier.java.lang.runtime.Matcher.index;
import static com.github.forax.carrier.java.lang.runtime.Matcher.instanceOfResult;
import static com.github.forax.carrier.java.lang.runtime.Matcher.isInstance;
import static com.github.forax.carrier.java.lang.runtime.Matcher.isNull;
import static com.github.forax.carrier.java.lang.runtime.Matcher.of;
import static com.github.forax.carrier.java.lang.runtime.Matcher.and;
import static com.github.forax.carrier.java.lang.runtime.Matcher.project;
import static com.github.forax.carrier.java.lang.runtime.Matcher.test;
import static java.lang.invoke.MethodType.methodType;

public class InstanceOfExamples {
  public static void main(String[] args) throws Throwable {
    record Point(int x, int y) {}
    record Rectangle(Point p1, Point p2) {}

    // Object o = ...
    // if (o instanceof Rectangle(Point p1, Point p2)) -> ...

    var lookup = MethodHandles.lookup();

    var carrierType = methodType(Object.class, Point.class, Point.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new RecordPattern(Rectangle.class,
        new TypePattern(Point.class),
        new TypePattern(Point.class)
    );
    var matcher = pattern.toMatcher(lookup, Object.class, carrierType, 0, doNotMatch(Object.class));
    var op = of(empty, matcher);

    // match: new Rectangle(new Point(1, 2), new Point(3, 4))
    var object1 = (Object) new Rectangle(new Point(1, 2), new Point(3, 4));
    var carrier1 = op.invokeExact(object1);
    System.out.println("carrier1: " + carrier1);
    System.out.println("result1: " + (boolean) instanceOfResult().invokeExact(carrier1));
    System.out.println("binding 1 " + (Point) carrierMetadata.accessor(0).invokeExact(carrier1));
    System.out.println("binding 2 " + (Point) carrierMetadata.accessor(1).invokeExact(carrier1));

    // match: new Rectangle(null, new Point(1, 2))
    var object2 = (Object) new Rectangle(null, new Point(1, 2));
    var carrier2 = op.invokeExact(object2);
    System.out.println("carrier2: " + carrier2);
    System.out.println("result2: " + (boolean) instanceOfResult().invokeExact(carrier2));

    // match: new Rectangle(new Point(1, 2), null)
    var object3 = (Object) new Rectangle(new Point(1, 2), null);
    var carrier3 = op.invokeExact(object3);
    System.out.println("carrier3: " + carrier3);
    System.out.println("result3: " + (boolean) instanceOfResult().invokeExact(carrier3));
  }
}

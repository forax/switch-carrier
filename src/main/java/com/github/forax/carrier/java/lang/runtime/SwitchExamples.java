package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;

import java.lang.invoke.MethodHandles;

import static com.github.forax.carrier.java.lang.runtime.Matcher.bind;
import static com.github.forax.carrier.java.lang.runtime.Matcher.cast;
import static com.github.forax.carrier.java.lang.runtime.Matcher.doNotMatch;
import static com.github.forax.carrier.java.lang.runtime.Matcher.index;
import static com.github.forax.carrier.java.lang.runtime.Matcher.isInstance;
import static com.github.forax.carrier.java.lang.runtime.Matcher.isNull;
import static com.github.forax.carrier.java.lang.runtime.Matcher.of;
import static com.github.forax.carrier.java.lang.runtime.Matcher.or;
import static com.github.forax.carrier.java.lang.runtime.Matcher.project;
import static com.github.forax.carrier.java.lang.runtime.Matcher.switchResult;
import static com.github.forax.carrier.java.lang.runtime.Matcher.test;
import static java.lang.invoke.MethodType.methodType;

public class SwitchExamples {
  public static void main(String[] args) throws Throwable {
    record Point(int x, int y) {}
    record Rectangle(Point p1, Point p2) {}

    // Object o = ...
    // switch(o) {
    //   case Rectangle(Point p1, Point p2) -> ...
    //   case Object value -> ...
    // }

    var lookup = MethodHandles.lookup();

    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class, Point.class, Point.class, Object.class));
    var empty = carrierMetadata.empty();

    var rectangleMetadata = CarrierMetadata.fromRecord(lookup, Rectangle.class);

    var op = of(empty,
        test(isInstance(Object.class, Rectangle.class),
            cast(Object.class,
                or(project(rectangleMetadata.accessor(0),
                        test(isNull(Point.class),
                            doNotMatch(Point.class),
                            bind(1, carrierMetadata))),
                    project(rectangleMetadata.accessor(1),
                        test(isNull(Point.class),
                            doNotMatch(Point.class),
                            bind(2, carrierMetadata,
                                index(Point.class, carrierMetadata, 0))))
                )
            ),
            bind(3, carrierMetadata,
                index(Object.class, carrierMetadata, 1))
        )
    );

    // match: new Rectangle(new Point(1, 2), new Point(3, 4))
    var object1 = (Object) new Rectangle(new Point(1, 2), new Point(3, 4));
    var carrier1 = op.invokeExact(object1);
    System.out.println("carrier1: " + carrier1);
    System.out.println("result1: " + (int) switchResult(carrierMetadata).invokeExact(carrier1));
    System.out.println("binding 1 " + (Point) carrierMetadata.accessor(1).invokeExact(carrier1));
    System.out.println("binding 2 " + (Point) carrierMetadata.accessor(2).invokeExact(carrier1));

    // match: new Rectangle(null, new Point(1, 2))
    var object2 = (Object) new Rectangle(null, new Point(1, 2));
    var carrier2 = op.invokeExact(object2);
    System.out.println("carrier2: " + carrier2);
    System.out.println("result2: " + (int) switchResult(carrierMetadata).invokeExact(carrier2));

    // match: new Rectangle(new Point(1, 2), null)
    var object3 = (Object) new Rectangle(new Point(1, 2), null);
    var carrier3 = op.invokeExact(object3);
    System.out.println("carrier3: " + carrier3);
    System.out.println("result3: " + (int) switchResult(carrierMetadata).invokeExact(carrier3));

    // match: null
    var object4 = (Object) null;
    var carrier4 = op.invokeExact(object4);
    System.out.println("carrier4: " + carrier4);
    System.out.println("result4: " + (int) switchResult(carrierMetadata).invokeExact(carrier4));
    System.out.println("binding 3 " + (Object) carrierMetadata.accessor(3).invokeExact(carrier4));

    // match: "hello"
    var object5 = (Object) "hello";
    var carrier5 = op.invokeExact(object5);
    System.out.println("carrier5: " + carrier5);
    System.out.println("result5: " + (int) switchResult(carrierMetadata).invokeExact(carrier5));
    System.out.println("binding 3 " + (Object) carrierMetadata.accessor(3).invokeExact(carrier5));
  }
}
